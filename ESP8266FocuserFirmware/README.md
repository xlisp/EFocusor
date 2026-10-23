# EFucoser ESP8266 ULN2003 Main Firmware

This sketch is the primary ESP8266 firmware for a 35BYJ46 12V geared stepper
motor driven through a ULN2003 or compatible 4-channel low-side driver board.

The ASCOM driver, TCP protocol, serial protocol, WiFi control page, WebSocket
status updates, saved position, and DS18B20 temperature reading are kept
compatible with the main EFucoser firmware. The motor control layer uses
four phase outputs instead of STEP/DIR/ENABLE.

## Intended Hardware

- ESP8266 board: Wemos D1 mini or NodeMCU
- 35BYJ46 12V geared stepper motor
- ULN2003, ULN2803, or equivalent 4-channel driver board
- 12V motor power supply
- DS18B20 temperature sensor
- Optional CW/CCW manual buttons
- Optional Hall sensor, disabled by default in firmware

## Wiring

| ESP8266 pad | GPIO | Connect to | Notes |
| --- | ---: | --- | --- |
| D1 | GPIO5 | ULN2003 IN1 | Motor phase output. |
| D2 | GPIO4 | ULN2003 IN2 | Motor phase output. |
| D5 | GPIO14 | ULN2003 IN3 | Motor phase output. |
| D6 | GPIO12 | ULN2003 IN4 | Motor phase output. |
| D7 | GPIO13 | CW manual button | Wire the other side of the button to GND. |
| D3 | GPIO0 | CCW manual button | Wire the other side of the button to GND. GPIO0 must be HIGH during boot. |
| D4 | GPIO2 | DS18B20 DATA | Add a 4.7k pull-up from DATA to 3V3. GPIO2 must be HIGH during boot. |
| D0 | GPIO16 | Optional Hall sensor output | Disabled by default. Requires external 10k pull-up to 3V3 when enabled. |
| 3V3 | - | DS18B20 VCC, sensor logic VCC | Use for low-current logic and sensors only. |
| GND | - | ULN2003 GND, sensor GND, 12V negative | Common ground is required. |
| 12V supply + | - | ULN2003 motor VCC | Motor power input. |

### Power Wiring: 12V DC to Motor and ESP8266

Set the buck converter output to 5.0V with a multimeter before connecting the
ESP8266. Use a converter rated for at least 1A.

```text
12V DC positive ──┬── ULN2003 motor VCC
                  └── Buck converter IN+

12V DC negative ──┬── ULN2003 GND
                  └── Buck converter IN-

Buck converter OUT+ (5.0V) ── Wemos D1 mini 5V / NodeMCU VIN
Buck converter OUT-          ── ESP8266 GND
```

All ground connections belong to the same electrical node:

```text
12V negative = buck IN- = buck OUT- = ESP8266 GND
             = ULN2003 GND = DS18B20 GND
```

Connect 5V only to the Wemos D1 mini `5V` pin or NodeMCU `VIN` pin. Keep 5V
away from `3V3`. Keep 12V away from every ESP8266 power and GPIO pin.

### DS18B20 Temperature Sensor Wiring

Use three-wire mode:

```text
ESP8266 3V3 ───────── DS18B20 VCC
      │
      └── 4.7kΩ ──┐
                   │
ESP8266 D4/GPIO2 ──┴─ DS18B20 DATA
ESP8266 GND ───────── DS18B20 GND
```

The 4.7kΩ resistor connects between `D4/GPIO2` and `3V3`; it is required for
the DATA bus and keeps GPIO2 HIGH during boot. Common waterproof-probe colors
are red for VCC, yellow or white for DATA, and black for GND. Verify the
supplier pinout because wire colors can vary.

Connect the 35BYJ46 motor wires to the ULN2003 motor output connector or output
terminals according to the driver board documentation. If the motor vibrates or
moves in an incorrect sequence, swap the IN2/IN3 phase mapping in the
`AccelStepper` constructor or adjust the physical motor wire order.

## Pin Constraints

GPIO0 and GPIO2 are ESP8266 boot strap pins. They must be HIGH during reset.
The DS18B20 pull-up keeps GPIO2 HIGH. The CCW button on GPIO0 should remain
released during boot.

GPIO16 has no normal internal pull-up on common ESP8266 boards. When the
optional Hall sensor is enabled, add an external 10k pull-up from D0/GPIO16 to
3V3 and use an active-low Hall output.

## Optional Hall Sensor

Hall homing is disabled by default:

```cpp
#define USE_HALL_SENSOR 0
```

To enable Hall homing, change it to:

```cpp
#define USE_HALL_SENSOR 1
```

With Hall disabled, the protocol command `H#` returns:

```text
ERR:home_unavailable#
```

The web page Home button still moves to the saved logical zero position. Use
Set 0 after mechanically placing the focuser at the desired zero point.

## Temperature

The DS18B20 conversion is asynchronous. Version 1105 defers discovery, bus
reads, and conversion requests while moving; temperature resumes updating when
stationary. The last available value is retained during a move. The
latest value is exposed as `lastTemp` in `/api/status` and `I#`. A working
sensor also reports `"tempSensorPresent":true` at:

```text
http://192.168.4.1/api/status
```

Device-side temperature compensation movement is intentionally disabled. NINA
should read the ASCOM `Temperature` property and handle temperature drift or
autofocus behavior.

## Default Motion Settings

Defaults are tuned for a typical 35BYJ46 described as `7.5 deg / 85` and driven
in half-step mode:

```text
stepsPerRev = 8160
maxSteps = 816000
maxSpeed = 800
acceleration = 1000
manualMoveStepSize = 100
findHomeStepSize = 200
```

Actual 35BYJ46 gear ratios and coil order can vary. Calibrate `stepsPerRev`,
`maxSteps`, speed, and direction on the real focuser mechanism.

## Build

Install these Arduino libraries:

```text
AccelStepper
WebSockets
OneWire
DallasTemperature
```

Compile for Wemos D1 mini:

```powershell
arduino-cli compile --fqbn esp8266:esp8266:d1_mini ESP8266FocuserFirmware
```

Compile for NodeMCU 1.0:

```powershell
arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 ESP8266FocuserFirmware
```

## Protocol Compatibility

The following public identities and protocol surfaces are retained:

- Mobile control URL: `http://192.168.4.1`
- ASCOM TCP port: `4030`
- WebSocket port: `81`
- AP SSID pattern: `Focuser-<chipid>`
- AP password: `012345678`
- ASCOM driver can use the same TCP or serial protocol

The firmware version is `1105`, and the identification string
is:

```text
EFucoser ESP8266 ULN2003 Focuser ver 1105
```

Version 1103 adds the `X <speed>#` and `A <acceleration>#` commands required by
the native INDI driver.

Version 1104 makes serial/TCP `S#` and HTTP Halt stop at the current physical
step immediately, clearing the target and velocity without resetting the
logical zero or changing saved motor speed. Earlier firmware used a deceleration
move; at 1200 steps/s and 300 steps/s² this can continue for about four seconds.
This change does not establish that 1200 steps/s is mechanically reliable under
load; missed steps and the usable maximum speed require a physical test.

Version 1105 drains serial replies in chunks of at most eight bytes, bounded by
the UART's currently available space. A long `I#` reply therefore allows the
stepper loop to run between chunks at the existing 9600 baud rate. Replies stay
ordered and commands are not executed from truncated input. EEPROM commits,
OneWire operations, and unsolicited WebSocket broadcasts wait until stationary;
`settingsPending` reports an accepted setting awaiting persistence. EEPROM layout
and saved motor parameters are unchanged. Other synchronous HTTP/TCP handlers
remain cooperative code, so this is not a hard real-time guarantee.

Firmware scheduling tests use the real reply helper, extracted sketch functions,
and optionally the actual AccelStepper source:

```sh
EFUCOSER_ACCELSTEPPER_SOURCE=/path/to/AccelStepper/src \
  python -m unittest discover -s indi_ikunfocuser_native/tests -p 'test_firmware*.py' -v
```

Without that environment variable the real-library timing test is skipped;
offline results do not establish the motor's usable speed under load.
