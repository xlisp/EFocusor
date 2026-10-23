# EFucoser Development Guide for Agents

This repository contains firmware, ASCOM driver code, and test utilities for the EFucoser Arduino Nano and ESP8266 electronic focuser.

## Project Map

- `ESP8266FocuserFirmware/`
  - Primary ESP8266 + ULN2003 Arduino sketch with AP+STA WiFi, mobile web UI, HTTP API, WebSocket status updates, TCP ASCOM-compatible text protocol, and serial debug protocol.
- `ESP8266FocuserFirmware_STEP_DIR/`
  - Compatibility ESP8266 firmware for STEP/DIR/ENABLE motor drivers.
- `driver/EFucoserFocuserDriver/`
  - ASCOM .NET Framework focuser driver source implementing `IFocuserV3`.
- `driver/FocuserTest/`
  - Simple Windows Forms test client using ASCOM DriverAccess.

## Fixed Public Identities

Keep these stable unless a new ASCOM driver slot is intentionally required:

- ASCOM ProgID: `ASCOM.EFucoser.Focuser`
- ASCOM Chooser name: `EFucoser Universal Focuser`
- ASCOM driver description: `ASCOM Focuser Driver for EFucoser Arduino Nano and ESP8266 controllers.`
- Driver DLL assembly name: `ASCOM.EFucoser.Focuser.dll`
- ESP8266 AP SSID pattern: `Focuser-<chipid>`
- ESP8266 AP password: `012345678`
- Mobile control URL: `http://192.168.4.1`
- ASCOM TCP port: `4030`
- WebSocket port: `81`

## Firmware Notes

Primary file: `ESP8266FocuserFirmware/ESP8266FocuserFirmware.ino`

Expected Arduino environment:

- Board: Wemos D1 mini or NodeMCU
- Arduino ESP8266 core
- Libraries: `AccelStepper`, `WebSocketsServer`, `OneWire`, `DallasTemperature`

Core behavior:

- Uses `AccelStepper::HALF4WIRE` with a ULN2003 board.
- Main wiring: D1/GPIO5 to IN1, D2/GPIO4 to IN2, D5/GPIO14 to IN3,
  and D6/GPIO12 to IN4.
- Stores settings in EEPROM with `SETTINGS_MAGIC` = `0xEF0C2003`.
- Default `maxSteps` = 816000.
- Default `stepsPerRev` = 8160 for a typical 35BYJ46 `7.5° / 85` geared
  stepper in half-step mode.
- Position is linear from 0 to maxSteps. No angular conversion.
- The optional Hall sensor uses D0/GPIO16 and is disabled by default.
- With Hall disabled, the web Home action returns to the saved logical zero and
  protocol homing returns `ERR:home_unavailable#`.
- `Set 0` stores `homeOffsetSteps` to define a logical zero.
- The mobile web page is embedded in `INDEX_HTML` and served from `/`.
- ULN2003 firmware 1105 sends bounded, ordered serial reply chunks instead of
  blocking the motor loop; `CooperativeReply.h` is part of the sketch. Flash
  commits, OneWire operations, and unsolicited WebSocket broadcasts defer until
  stationary. `settingsPending` identifies settings not yet persisted.
- Native INDI driver 1.3 keeps motion/stop polling on short `G#` frames and
  postpones the long `I#` refresh until a successful stationary status. Do not
  reintroduce full-state polling during movement on firmware 1103/1104.
- Status JSON at `GET /api/status`. Movement via `POST /api/move`.
- Settings via `GET/POST /api/settings`.

Text commands (`#`-terminated):

- `G#`: current position and moving state (`P <steps>;M <true|false>#`)
- `M <steps>#`: move to absolute step position
- `P <steps>#`: set current logical step position
- `H#`: start optional Hall homing; returns `ERR:home_unavailable#` when disabled
- `S#`: stop movement
- `R <0|1>#`: set direction inversion
- `C <0|1>#`: set continuous hold
- `V#`: firmware version
- `I#`: JSON status
- `D <maxSteps>#`: set max steps range
- `X <stepsPerSecond>#`: set maximum motor speed
- `A <stepsPerSecondSquared>#`: set motor acceleration
- `T <coeff*1000>#`: set temperature coefficient
- `E <temp*100>#`: update current temperature

Hardware constraints:

- ESP8266 GPIO is 3.3V logic only.
- The intended 35BYJ46 motor receives external 12V through ULN2003 motor VCC.
- Shared GND between ESP8266, ULN2003, sensors, and 12V negative.
- 12V isolated from ESP8266 5V, 3.3V, GPIO.
- The legacy STEP/DIR firmware is
  `ESP8266FocuserFirmware_STEP_DIR/ESP8266FocuserFirmware_STEP_DIR.ino`.

## ASCOM Driver Notes

Primary files:

- `driver/EFucoserFocuserDriver/Driver.cs` — Implements `IFocuserV3`
- `driver/EFucoserFocuserDriver/FocuserConnection.cs` — TCP and Serial connection abstractions
- `driver/EFucoserFocuserDriver/SetupDialogForm.cs` — Setup UI

Architecture:

- `Driver.cs` implements `IFocuserV3` with `Absolute = true`.
- `IFocuserConnection` abstracts communication (same pattern as rotator reference).
- `SerialFocuserConnection`: ASCOM Serial utility, 9600-8N1.
- `TcpFocuserConnection`: Raw TCP with `#`-terminated protocol.
- Setup UI stores `Transport`, `ComPort`, `TcpHost`, `TcpPort`, `CommandTimeoutMs`, `MaxStep`, `ContHold` in ASCOM Profile.
- On TCP connection, driver sends `D <maxStep>#` to align range.

Key differences from rotator:

- Linear position (0 to maxSteps) — no angular math.
- `IFocuserV3` interface: `Move(int)`, `Position` (int), `IsMoving`, `Halt()`, `MaxStep`, `MaxIncrement`, `StepSize`, `TempComp`, `TempCompAvailable`, `Temperature`, `TemperatureSet`.
- `TempComp` and temperature compensation supported.
- No `Reverse` or `CanReverse` properties (not in IFocuserV3).

Build environment:

- .NET Framework v4.8
- ASCOM Platform 6 Developer Components
- Visual Studio or MSBuild with .NET Framework 4.8

## Protocol Flow (TCP)

1. Driver connects, sends `D <maxStep>#` to set range.
2. Driver sends `C 1#` or `C 0#` for hold mode.
3. Status polling: `G#` → `P <pos>;M <moving>#`.
4. Move: `M <target>#` → firmware moves stepper.
5. Temperature: `E <temp*100>#` to update, `T <coeff*1000>#` to set coefficient.
