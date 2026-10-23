import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
FIRMWARE = {
    "ESP8266 ULN2003": (
        ROOT / "ESP8266FocuserFirmware" / "ESP8266FocuserFirmware.ino",
        1105,
    ),
    "ESP8266 STEP/DIR": (
        ROOT
        / "ESP8266FocuserFirmware_STEP_DIR"
        / "ESP8266FocuserFirmware_STEP_DIR.ino",
        1005,
    ),
    "Arduino Nano ULN2003": (
        ROOT
        / "ArduinoNanoFocuserFirmware_ULN2003"
        / "ArduinoNanoFocuserFirmware_ULN2003.ino",
        1201,
    ),
}


class FirmwareProtocolConsistencyTests(unittest.TestCase):
    def test_release_versions(self):
        for name, (path, expected_version) in FIRMWARE.items():
            with self.subTest(controller=name):
                source = path.read_text(encoding="utf-8")
                match = re.search(r"#define FIRMWARE_VERSION\s+(\d+)", source)
                self.assertIsNotNone(match)
                self.assertEqual(int(match.group(1)), expected_version)

    def test_device_identity(self):
        for name, (path, _) in FIRMWARE.items():
            source = path.read_text(encoding="utf-8")
            with self.subTest(controller=name):
                self.assertRegex(
                    source,
                    r'#define DEVICE_RESPONSE "EFucoser .+ Focuser ver \d+"',
                )

    def test_required_commands_exist(self):
        commands = "GPMHSRCVIDTEXA"
        for name, (path, _) in FIRMWARE.items():
            source = path.read_text(encoding="utf-8")
            with self.subTest(controller=name):
                for command in commands:
                    self.assertIn(f"case '{command}':", source)

    def test_speed_and_acceleration_ranges_match(self):
        for name, (path, _) in FIRMWARE.items():
            source = path.read_text(encoding="utf-8")
            with self.subTest(controller=name):
                self.assertRegex(
                    source,
                    r"case 'X':[\s\S]{0,400}value < 1 \|\| value > 2000",
                )
                self.assertRegex(
                    source,
                    r"case 'A':[\s\S]{0,400}value < 1 \|\| value > 10000",
                )

    def test_maximum_travel_range_matches(self):
        for name, (path, _) in FIRMWARE.items():
            source = path.read_text(encoding="utf-8")
            with self.subTest(controller=name):
                self.assertRegex(
                    source,
                    r"case 'D':[\s\S]{0,500}value < 100 \|\| value > 9999999L",
                )


if __name__ == "__main__":
    unittest.main()
