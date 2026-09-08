#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""EFucoser ESP8266 ULN2003 firmware flasher."""

from __future__ import print_function

import argparse
import hashlib
import json
import os
import subprocess
import sys

try:
    from serial.tools import list_ports
except ImportError:
    list_ports = None

PACKAGE_DIR = os.path.dirname(os.path.abspath(__file__))
RUNTIME_DIR = os.path.join(PACKAGE_DIR, "runtime")
FIRMWARE_DIR = os.path.join(PACKAGE_DIR, "firmware")
MANIFEST_PATH = os.path.join(FIRMWARE_DIR, "manifest.json")
ESPTOOL_PATH = os.path.join(RUNTIME_DIR, "esptool.py")


def pause(message="\n按回车键关闭窗口..."):
    try:
        input(message)
    except (EOFError, KeyboardInterrupt):
        pass


def fail(message):
    print("\n错误：{0}".format(message))
    pause()
    return 1


def normalize_port(value):
    value = value.strip().upper()
    if value.isdigit():
        return "COM" + value
    return value


def choose_port(requested_port=None):
    if requested_port:
        return normalize_port(requested_port)

    if list_ports is None:
        print("未找到串口检测组件，请使用包内 runtime\\python.exe 运行。")
        while True:
            value = input("请手动输入串口号，例如 COM3 或 3：").strip()
            if value:
                return normalize_port(value)

    ports = list(list_ports.comports())
    if len(ports) == 1:
        selected = ports[0].device
        print("检测到串口：{0}  {1}".format(selected, ports[0].description))
        return selected

    if ports:
        print("检测到多个串口，请选择 ESP8266 所在串口：")
        for index, port in enumerate(ports, 1):
            print("  {0}. {1}  {2}".format(index, port.device, port.description))
        while True:
            answer = input("输入序号：").strip()
            if answer.isdigit() and 1 <= int(answer) <= len(ports):
                return ports[int(answer) - 1].device
            print("请输入列表中的有效序号。")
        return None

    print("没有自动检测到串口。")
    while True:
        value = input("请手动输入串口号，例如 COM3 或 3：").strip()
        if value:
            return normalize_port(value)


def load_manifest():
    if not os.path.isfile(MANIFEST_PATH):
        return {}
    try:
        with open(MANIFEST_PATH, "r", encoding="utf-8") as manifest_file:
            return json.load(manifest_file)
    except (OSError, ValueError):
        return {}


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as firmware_file:
        while True:
            block = firmware_file.read(1024 * 1024)
            if not block:
                break
            digest.update(block)
    return digest.hexdigest()


def find_firmware():
    manifest = load_manifest()
    if manifest.get("fileName"):
        path = os.path.join(FIRMWARE_DIR, manifest["fileName"])
        if not os.path.isfile(path):
            return None, {}, "找不到固件文件：{0}".format(path)

        expected = manifest.get("sha256")
        actual = sha256_file(path)
        if expected and actual.lower() != str(expected).lower():
            return None, {}, "固件校验失败，文件可能损坏。\n实际 SHA256：{0}".format(actual)
        return path, manifest, ""

    candidates = sorted(
        name for name in os.listdir(FIRMWARE_DIR) if name.lower().endswith(".bin")
    )
    if len(candidates) != 1:
        return None, {}, "firmware\\manifest.json 不存在，且 firmware 目录中没有唯一固件文件。"
    path = os.path.join(FIRMWARE_DIR, candidates[0])
    return path, {"version": "未知"}, ""


def run_esptool(arguments):
    python_exe = sys.executable
    runtime_python = os.path.join(RUNTIME_DIR, "python.exe")
    if os.path.isfile(runtime_python):
        python_exe = runtime_python
    command = [python_exe, ESPTOOL_PATH] + arguments
    return subprocess.call(command, cwd=PACKAGE_DIR)


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--port")
    parser.add_argument("--erase", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    options = parser.parse_args()

    print("=" * 58)
    print(" EFucoser ESP8266 ULN2003 固件一键烧录")
    print(" 适用：Wemos D1 mini / NodeMCU（ESP8266，1MB Flash）")
    print("=" * 58)
    print("\n请用 USB 线连接控制板，并关闭 Arduino 串口监视器等程序。")

    firmware_path, manifest, error = find_firmware()
    if error:
        return fail(error)
    print("固件文件：{0}".format(os.path.basename(firmware_path)))
    print("固件版本：{0}".format(manifest.get("version", "未知")))

    if options.dry_run:
        print("\n自检通过：运行环境、固件校验和参数读取正常。")
        return 0

    port = choose_port(options.port)
    if not port:
        return fail("未提供串口号。")

    print("\n准备烧录到 {0}。".format(port))
    print("连接失败时，请按住 FLASH/BOOT 键，短按 RESET，松开 FLASH/BOOT 后重试。")

    if options.erase:
        print("\n正在清空旧固件和设置...")
        erase_result = run_esptool(
            [
                "--chip",
                "esp8266",
                "--port",
                port,
                "--baud",
                "115200",
                "erase_flash",
            ]
        )
        if erase_result != 0:
            return fail("清空失败，请检查 USB 线、串口驱动和下载模式。")

    print("\n正在写入固件，请勿断开 USB 或关闭窗口...")
    flash_result = run_esptool(
        [
            "--chip",
            "esp8266",
            "--port",
            port,
            "--baud",
            "115200",
            "write_flash",
            "--flash_mode",
            "dio",
            "--flash_freq",
            "40m",
            "--flash_size",
            "detect",
            "0x00000",
            firmware_path,
        ]
    )
    if flash_result != 0:
        return fail("烧录失败，请检查 USB 线、串口驱动和下载模式。")

    print("\n烧录成功。控制板会自动重启。")
    print("手机连接 Focuser-xxxxxx WiFi 后访问：http://192.168.4.1")
    print("版本检查地址：http://192.168.4.1/api/status")
    print('正确版本字段为："firmware":{0}'.format(manifest.get("version", "?")))
    pause()
    return 0


if __name__ == "__main__":
    sys.exit(main())
