"""Compile real scheduling helpers; optionally exercise the actual AccelStepper."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SKETCH = ROOT / "ESP8266FocuserFirmware"


def function(name):
    source = (SKETCH / "ESP8266FocuserFirmware.ino").read_text()
    match = re.search(r"^(?:void|bool) " + name + r"\([^\n]*\) \{", source, re.M)
    assert match, name
    end, depth = match.end(), 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[match.start():end]


class FirmwareSchedulingTests(unittest.TestCase):
    def compile_run(self, source, *, stepper=False):
        compiler = shutil.which("g++")
        if not compiler:
            self.skipTest("C++ compiler required")
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "check.cpp").write_text(source)
            command = [compiler, "-std=c++11", "-O2", "-I" + str(SKETCH)]
            if stepper:
                library = Path(os.environ.get("EFUCOSER_ACCELSTEPPER_SOURCE", ""))
                if not (library / "AccelStepper.cpp").is_file():
                    self.skipTest("Set EFUCOSER_ACCELSTEPPER_SOURCE to the real library src directory")
                (directory / "Arduino.h").write_text(r'''#pragma once
#include <stdint.h>
#include <math.h>
#include <algorithm>
using std::max; using std::min;
typedef bool boolean; typedef uint8_t byte;
#define HIGH 1
#define LOW 0
#define OUTPUT 1
extern unsigned long clockMicros;
inline unsigned long micros() { return clockMicros; }
inline void delay(unsigned long ms) { clockMicros += ms * 1000; }
inline void delayMicroseconds(unsigned int us) { clockMicros += us; }
inline void pinMode(uint8_t, uint8_t) {}
inline void digitalWrite(uint8_t, uint8_t) {}
inline void yield() {}
template<class T> T constrain(T x, T lo, T hi) { return x < lo ? lo : x > hi ? hi : x; }
''')
                command += ["-DARDUINO=10819", "-I" + str(directory), "-I" + str(library), str(library / "AccelStepper.cpp")]
            command += [str(directory / "check.cpp"), "-o", str(directory / "check")]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            result = subprocess.run([str(directory / "check")], capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_bounded_ordered_reply_and_partial_writes(self):
        self.compile_run(r'''#include "CooperativeReply.h"
#include <cassert>
#include <string>
struct Port {
 int space = 0; size_t limit = 8; std::string output;
 int availableForWrite() { return space; }
 size_t write(const uint8_t* p, size_t n) {
  assert(n <= 8 && n < static_cast<size_t>(space));
  if (n > limit) n = limit;
  output.append(reinterpret_cast<const char*>(p), n); return n;
 }
};
int main() {
 CooperativeReply<16> reply; Port port;
 assert(reply.assign("abcdefghijklmnop", 16));
 assert(!reply.assign("x", 1)); // never replace an outstanding frame
 assert(reply.drain(port, 1) == 0 && reply.busy());
 port.space = 1; assert(reply.drain(port, 1) == 0);
 port.space = 128; port.limit = 3;
 while (reply.busy()) reply.drain(port, 1);
 assert(port.output == "abcdefghijklmnop");
 assert(!reply.assign("abcdefghijklmnopq", 17) && !reply.busy());
 assert(reply.assign("S#", 2)); while(reply.busy()) reply.drain(port, 1);
 assert(port.output == "abcdefghijklmnopS#");
 reply.assign("x",1); reply.reset(); assert(!reply.busy());
}
''')

    def test_flash_temperature_and_broadcast_wait_for_stationary(self):
        prelude = r'''#include <cassert>
#include <string>
using String = std::string;
unsigned long now = 20000; unsigned long millis() { return now; }
struct Motor { long remaining=1; long distanceToGo(){return remaining;} long currentPosition(){return 123;} } stepper;
bool findingHome=false,settingsSavePending=false,statusBroadcastPending=false;
struct Settings { unsigned magic=0; long position=0; float lastTemp=20; } settings;
const unsigned SETTINGS_MAGIC=0xEF0C2003;
long driverToPhysicalSteps(long value){return value;}
struct Storage { int puts=0,commits=0; bool ok=true; void put(int,const Settings&){++puts;} bool commit(){++commits;return ok;} } EEPROM;
struct Network { int sends=0; int connectedClients(){return 1;} void broadcastTXT(String&){++sends;} } webSocket;
String statusJson(){return "{}";}
unsigned long lastStatusBroadcast=0,lastTempSampleMillis=0,tempRequestMillis=0;
const unsigned long tempRetryIntervalMs=10000,tempConversionMs=750,tempReadIntervalMs=5000;
bool tempSensorPresent=false,tempConversionPending=false;
const float DEVICE_DISCONNECTED_C=-127;
struct Sensors { int scans=0,reads=0,requests=0; void begin(){++scans;} int getDeviceCount(){return 1;} float getTempCByIndex(int){++reads;return 12.5;} void requestTemperatures(){++requests;} } tempSensors;
'''
        bodies = "\n".join(function(name) for name in (
            "motionActive", "serviceSettingsSave", "saveSettings", "broadcastStatus", "serviceStatusBroadcast",
            "requestTemperatureConversion", "serviceTemperatureSensor"))
        self.compile_run(prelude + bodies + r'''
int main() {
 saveSettings(); broadcastStatus(); serviceSettingsSave(); serviceTemperatureSensor(); serviceStatusBroadcast();
 assert(settingsSavePending && statusBroadcastPending);
 assert(EEPROM.commits==0 && tempSensors.scans==0 && webSocket.sends==0);
 stepper.remaining=0; findingHome=true;
 serviceSettingsSave(); serviceTemperatureSensor(); serviceStatusBroadcast();
 assert(EEPROM.commits==0 && tempSensors.scans==0 && webSocket.sends==0);
 findingHome=false; serviceSettingsSave(); serviceTemperatureSensor(); serviceStatusBroadcast();
 assert(!settingsSavePending && settings.position==123 && settings.magic==SETTINGS_MAGIC);
 assert(EEPROM.commits==1 && tempSensors.scans==1 && tempSensors.requests==1 && webSocket.sends==1);
 stepper.remaining=1; now+=1000; serviceTemperatureSensor(); assert(tempSensors.reads==0);
 stepper.remaining=0; serviceTemperatureSensor(); assert(tempSensors.reads==1 && settings.lastTemp==12.5);
 EEPROM.ok=false; saveSettings(); assert(settingsSavePending);
 int before=EEPROM.commits; stepper.remaining=1; serviceSettingsSave(); assert(EEPROM.commits==before);
 stepper.remaining=0; EEPROM.ok=true; serviceSettingsSave(); assert(!settingsSavePending);
}
''')

    def test_real_accelstepper_keeps_running_during_long_serial_replies(self):
        prelude = r'''#include <AccelStepper.h>
#include "CooperativeReply.h"
#include <cassert>
#include <cmath>
#include <string>
#include <algorithm>
using String=std::string;
unsigned long clockMicros=0;
AccelStepper stepper(AccelStepper::HALF4WIRE,5,14,4,12);
struct SerialPort {
 std::string input,output; size_t cursor=0; double queued=0; unsigned long updated=0;
 void advance(){queued=std::max(0.0,queued-(clockMicros-updated)*960.0/1000000.0);updated=clockMicros;}
 int availableForWrite(){advance();return 128-static_cast<int>(std::ceil(queued));}
 size_t write(const uint8_t* bytes,size_t n){
  assert(n<=8 && n<static_cast<size_t>(availableForWrite()));
  queued+=n;output.append(reinterpret_cast<const char*>(bytes),n);return n;
 }
 int available(){return static_cast<int>(input.size()-cursor);}
 int read(){return input[cursor++];}
} Serial;
String serialBuffer; CooperativeReply<1024> serialReply; bool serialCommandOverflow=false;
int commands=0;
String processCommand(const String& command){++commands;return command=="I"?String(447,'a')+"#":"P 123;M true#";}
'''
        self.compile_run(prelude + function("handleSerial") + r'''
int main(){
 stepper.setMaxSpeed(1200);stepper.setAcceleration(300);stepper.moveTo(100000);
 unsigned long last=0,gap=0,next=6000000;
 while(clockMicros<16000000){
  clockMicros+=100;
  if(clockMicros>=next && next<=12000000){Serial.input+="I#";next+=6000000;}
  handleSerial();long before=stepper.currentPosition();stepper.run();
  if(stepper.currentPosition()!=before){if(clockMicros>=5000000 && last)gap=std::max(gap,clockMicros-last);last=clockMicros;}
 }
 assert(commands==2 && Serial.output==(std::string(447,'a')+"#"+std::string(447,'a')+"#"));
 assert(gap<1000 && stepper.speed()>1199);
 // A bounded malformed command is discarded through its delimiter. The next
 // valid command is still recognized, without executing a truncated command.
 Serial.input+=std::string(200,'M')+"#G#";
 for(int i=0;i<20000;++i){clockMicros+=100;handleSerial();stepper.run();}
 assert(commands==3);
 assert(Serial.output.find("ERR:command_too_long#P 123;M true#")!=std::string::npos);
}
''', stepper=True)


if __name__ == "__main__":
    unittest.main()
