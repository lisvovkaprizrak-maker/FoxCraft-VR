"""Offline checks for the opt-in and non-destructive FoxCraft ADB helper."""
from __future__ import annotations

import importlib.util
from pathlib import Path
from io import StringIO
from contextlib import redirect_stdout
from tempfile import TemporaryDirectory
import sys
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "foxcraft_quest_smoke.py"
spec = importlib.util.spec_from_file_location("foxcraft_quest_smoke", SCRIPT)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class QuestSmokeTests(unittest.TestCase):
    def test_verify_apk_requires_separate_foxcraft_package(self):
        with TemporaryDirectory() as tmp:
            apk = Path(tmp) / "test.apk"
            apk.write_bytes(b"x" * 110_000)
            with patch.object(module, "resolve_aapt", return_value="/aapt"):
                with patch.object(module, "command",
                                  return_value="package: name='com.foxcraftvr.quest' versionCode='101'\n"):
                    module.verify_apk(apk)
                with patch.object(module, "command",
                                  return_value="package: name='com.qcxr.qcxr'\n"):
                    with self.assertRaises(module.DeviceError):
                        module.verify_apk(apk)

    def test_missing_and_tiny_apk_are_rejected(self):
        with TemporaryDirectory() as tmp:
            tiny = Path(tmp) / "tiny.apk"
            tiny.write_bytes(b"PK")
            with self.assertRaises(module.DeviceError):
                module.verify_apk(tiny)
            with self.assertRaises(module.DeviceError):
                module.verify_apk(Path(tmp) / "missing.apk")

    def test_only_online_authorized_quest_is_selected(self):
        listing = (
            "List of devices attached\n"
            "QuestA device\n"
            "QuestB unauthorized\n"
        )
        with patch.object(module, "command", return_value=listing):
            self.assertEqual(module.choose_device("/adb", None), "QuestA")
            self.assertEqual(module.choose_device("/adb", "QuestA"), "QuestA")
            with self.assertRaises(module.DeviceError):
                module.choose_device("/adb", "QuestB")

        with patch.object(module, "command", return_value=(
            "List of devices attached\nQuestA device\nQuestC device\n"
        )):
            with self.assertRaises(module.DeviceError):
                module.choose_device("/adb", None)
            self.assertEqual(module.choose_device("/adb", "QuestC"), "QuestC")

    def test_default_mode_never_installs_or_launches(self):
        with patch.object(sys, "argv", ["foxcraft_quest_smoke.py"]):
            with patch.object(module.shutil, "which", return_value="/adb"):
                with patch.object(module, "choose_device", return_value="QuestA"):
                    with patch.object(module, "command", return_value="package:/data/app/foxcraft/base.apk\n") as called:
                        self.assertEqual(module.main(), 0)
        self.assertEqual(len(called.call_args_list), 1)
        self.assertEqual(called.call_args.args[0],
                         ["/adb", "-s", "QuestA", "shell", "pm", "path", module.PACKAGE])

    def test_install_requires_explicit_apk(self):
        with patch.object(sys, "argv", ["foxcraft_quest_smoke.py", "--install"]):
            with patch.object(module, "command") as called:
                self.assertEqual(module.main(), 1)
                called.assert_not_called()

    def test_logs_include_java_mod_marker_without_exposing_other_apps(self):
        log_lines = (
            "11-06 I/Other: token=should-not-appear\\n"
            "11-06 I/Java: [FoxCraftKeyboard] initialized for Minecraft 1.21.5\\n"
            "11-06 I/Unity: [FoxCraft] Starting Minecraft through Pojlib\\n"
            "11-06 I/Other: private chat message\\n"
        )
        with patch.object(sys, "argv", ["foxcraft_quest_smoke.py", "--logs"]):
            with patch.object(module.shutil, "which", return_value="/adb"):
                with patch.object(module, "choose_device", return_value="QuestA"):
                    with patch.object(module, "command", side_effect=[
                        "package:/data/app/foxcraft/base.apk\\n", log_lines
                    ]) as called:
                        output = StringIO()
                        with redirect_stdout(output):
                            self.assertEqual(module.main(), 0)

        self.assertEqual(called.call_args_list[-1].args[0],
                         ["/adb", "-s", "QuestA", "logcat", "-d", "-t", "1200"])
        self.assertIn("FoxCraftKeyboard", output.getvalue())
        self.assertIn("[FoxCraft] Starting Minecraft", output.getvalue())
        self.assertNotIn("should-not-appear", output.getvalue())
        self.assertNotIn("private chat message", output.getvalue())

    def test_launch_is_explicit_and_package_specific(self):
        with patch.object(sys, "argv", ["foxcraft_quest_smoke.py", "--launch"]):
            with patch.object(module.shutil, "which", return_value="/adb"):
                with patch.object(module, "choose_device", return_value="QuestA"):
                    with patch.object(module, "command", side_effect=[
                        "package:/data/app/foxcraft/base.apk\n", "Starting: Intent\n"
                    ]) as called:
                        self.assertEqual(module.main(), 0)
        self.assertEqual(called.call_args_list[-1].args[0],
                         ["/adb", "-s", "QuestA", "shell", "am", "start", "-n",
                          module.PACKAGE + "/" + module.ACTIVITY])


if __name__ == "__main__":
    unittest.main()
