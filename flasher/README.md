# EFucoser ESP8266 ULN2003 固件刷写包

这个目录提供面向用户的 Windows 固件刷写脚本。刷写包内包含当前编译好的
EFucoser ESP8266 ULN2003 固件、便携版 Python、esptool 和 pyserial，
用户不需要安装 Arduino、Python 或其他开发环境。

## 用户使用

1. 解压整个 ZIP，保留 `runtime`、`firmware` 与脚本在同一目录下。
2. 用可传输数据的 USB 线连接 ESP8266 控制板。
3. 关闭 Arduino IDE、串口监视器或其他占用串口的程序。
4. 双击 `flash-firmware.bat`。
5. 如果检测到多个 COM 口，选择 ESP8266 对应的串口。
6. 等待窗口显示“烧录成功”。

如果旧设备无法启动、WiFi 异常或需要清空原设置，双击 `flash-erase.bat`。
该脚本会先擦除 Flash，再写入当前固件。

烧录完成后，控制板会自动重启。手机连接 `Focuser-xxxxxx` WiFi，
密码为 `012345678`，然后访问：

```text
http://192.168.4.1
http://192.168.4.1/api/status
```

## 接线

```text
ESP8266 D1/GPIO5  -> ULN2003 IN1
ESP8266 D2/GPIO4  -> ULN2003 IN2
ESP8266 D5/GPIO14 -> ULN2003 IN3
ESP8266 D6/GPIO12 -> ULN2003 IN4
```

ESP8266 GND 必须与 ULN2003 GND、传感器 GND 和 12V 电源负极共地。
ULN2003 电机电源使用 12V，ESP8266 使用 5V 供电，12V 不能接到 ESP8266
的 `3V3`、`5V` 或 GPIO。

## 开发者构建

生成最新刷写 ZIP：

```powershell
.\flasher\Build-Flasher.ps1
```

脚本默认使用仓库根目录下的 `build\flasher\compile` 编译当前
`ESP8266FocuserFirmware` 源码，并读取便携运行时目录生成 ZIP。若本机已有
便携运行时，也可以显式指定：

```powershell
.\flasher\Build-Flasher.ps1 -RuntimeDir .\build\EFucoser_ULN2003_Flasher_Win32\runtime
```

构建要求本机安装 `arduino-cli`、ESP8266 core 和以下 Arduino 库：

```text
AccelStepper
WebSockets
OneWire
DallasTemperature
```

生成的 ZIP 会输出到 `dist\`。ZIP 内置 `firmware\manifest.json`，刷写脚本会
按 SHA256 校验固件，防止损坏文件被写入控制板。
