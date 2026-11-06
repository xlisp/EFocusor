#!/usr/bin/env python3
"""Mock EFocusor ESP8266 firmware speaking the `#`-terminated text protocol
over TCP 4030. Used to test the Android app end-to-end via `adb reverse`
without real hardware. Reply formats mirror processCommand()/statusResponse()
in ESP8266FocuserFirmware.ino (firmware 1105).
"""
import json
import socketserver
import threading
import time

LOCK = threading.Lock()
STATE = {
    "firmware": 1105,
    "position": 100000,
    "target": 100000,
    "hold": True,
    "reversed": False,
    "tempComp": False,
    "stepsPerRev": 8160,
    "maxSteps": 816000,
    "maxSpeed": 500,
    "acceleration": 2000,
    "tempCoeff": 0.0,
    "lastTemp": 21.53,
    "tempSensorPresent": True,
    "homeOffsetSteps": 0,
    "apSsid": "Focuser-MOCK01",
}
BANNER = "EFucoser ESP8266 ULN2003 Focuser ver 1105"


def clamp(v: int) -> int:
    return max(0, min(STATE["maxSteps"], v))


def is_moving() -> bool:
    return STATE["target"] != STATE["position"]


def status() -> str:
    return "P %d;M %s#" % (STATE["position"], "true" if is_moving() else "false")


def info_json() -> str:
    o = dict(STATE)
    o["positionSteps"] = o.pop("position")
    o["targetSteps"] = o.pop("target")
    o["isMoving"] = is_moving()
    o["home"] = False
    o["manualStep"] = 50
    o["homeStep"] = 20
    o["settingsPending"] = False
    o["tempCoeff"] = round(o["tempCoeff"], 2)
    o["lastTemp"] = round(o["lastTemp"], 2)
    return json.dumps(o) + "#"


def motor_loop():
    while True:
        with LOCK:
            diff = STATE["target"] - STATE["position"]
            if diff:
                step = max(1, STATE["maxSpeed"] // 10)
                if abs(diff) <= step:
                    STATE["position"] = STATE["target"]
                else:
                    STATE["position"] += step if diff > 0 else -step
        time.sleep(0.1)


def handle(cmd: str) -> str:
    cmd = cmd.strip()
    if cmd.endswith("#"):
        cmd = cmd[:-1]
    cmd = cmd.strip()
    if not cmd:
        return BANNER + "#"
    code, _, tail = cmd.partition(" ")
    try:
        value = int(tail.strip()) if tail.strip() else 0
    except ValueError:
        value = 0
    with LOCK:
        if code == "#":
            return BANNER + "#"
        if code == "G":
            return status()
        if code == "P":
            STATE["position"] = clamp(value)
            STATE["target"] = STATE["position"]
            return status()
        if code == "M":
            if not (0 <= value <= STATE["maxSteps"]):
                return "ERR:out_of_range#"
            STATE["target"] = value
            return status()
        if code == "H":
            return "ERR:home_unavailable#"
        if code == "S":
            STATE["target"] = STATE["position"]
            return "S#"
        if code == "R":
            STATE["reversed"] = value != 0
            return "reversed = %s#" % ("true" if STATE["reversed"] else "false")
        if code == "C":
            STATE["hold"] = value != 0
            return "hold = %s#" % ("true" if STATE["hold"] else "false")
        if code == "V":
            return "V %d#" % STATE["firmware"]
        if code == "I":
            return info_json()
        if code == "D":
            if not (100 <= value <= 9999999):
                return "ERR:max_steps#"
            if value < STATE["position"] or value < STATE["target"]:
                return "ERR:max_steps_below_position_or_target#"
            STATE["maxSteps"] = value
            return "D %d#" % value
        if code == "T":
            STATE["tempCoeff"] = value / 1000.0
            STATE["tempComp"] = value != 0
            return "T %.3f#" % STATE["tempCoeff"]
        if code == "E":
            STATE["lastTemp"] = value / 100.0
            return "E %.2f#" % STATE["lastTemp"]
        if code == "X":
            if not (1 <= value <= 2000):
                return "ERR:speed#"
            STATE["maxSpeed"] = value
            return "X %d#" % value
        if code == "A":
            if not (1 <= value <= 10000):
                return "ERR:acceleration#"
            STATE["acceleration"] = value
            return "A %d#" % value
        return "ERR:%s#" % code


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        buf = b""
        self.request.sendall((BANNER + "\n").encode())
        print("[conn] %s" % (self.client_address[0]), flush=True)
        try:
            while True:
                data = self.request.recv(256)
                if not data:
                    break
                buf += data
                while b"#" in buf:
                    frame, buf = buf.split(b"#", 1)
                    cmd = frame.decode(errors="replace")
                    reply = handle(cmd)
                    print("[rx] %-24s [tx] %s" % (cmd.strip(), reply.strip()), flush=True)
                    self.request.sendall(reply.encode())
        except ConnectionError:
            pass
        finally:
            print("[disc]", flush=True)


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    threading.Thread(target=motor_loop, daemon=True).start()
    with Server(("127.0.0.1", 4030), Handler) as srv:
        print("mock focuser listening on 127.0.0.1:4030", flush=True)
        srv.serve_forever()
