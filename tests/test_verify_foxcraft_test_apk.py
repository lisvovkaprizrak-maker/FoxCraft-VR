"""Offline checks for the FoxCraft Quest APK publish gate (no Android SDK needed)."""
from __future__ import annotations

from io import BytesIO
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from zipfile import ZipFile, ZIP_STORED

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "verify_foxcraft_test_apk.py"
spec = importlib.util.spec_from_file_location("foxcraft_apk_verify", SCRIPT)
mod = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(mod)

BADGING = (
    "package: name='com.foxcraftvr.quest' versionCode='101'\n"
    "launchable-activity: name='pojlib.UnityPlayerActivity'\n"
    "native-code: 'arm64-v8a'\n"
    "application-label:'FoxCraft'\n"
    "uses-feature-not-required: name='android.hardware.vr.headtracking'\n"
)


class VerifyFoxCraftTestApkTests(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.apk = Path(temp.name) / "test.apk"

    def make_apk(self, *, minecraft="1.21.5", mod_id="foxcraft_keyboard",
                 include_jar=True, include_runtime=True, include_classes=True,
                 jar_name=None, include_manifest=True, armv7=False):
        embedded = BytesIO()
        with ZipFile(embedded, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({
                "id": mod_id, "depends": {"minecraft": minecraft}
            }))
            if include_classes:
                for cls in mod.CLASSES:
                    jar.writestr(cls, b"CAFEBABE")
        with ZipFile(self.apk, "w", compression=ZIP_STORED) as apk:
            if include_manifest:
                apk.writestr("AndroidManifest.xml", b"Compiled")
            if include_runtime:
                apk.writestr("lib/arm64-v8a/libunity.so", b"ELF")
            if armv7:
                apk.writestr("lib/armeabi-v7a/libunity.so", b"ELF")
            if include_jar:
                apk.writestr(jar_name or "assets/" + mod.JAR_NAME, embedded.getvalue())
            apk.writestr("assets/large-other-asset.bin", b"u" * 150_000)

    def verify_with_mock_aapt(self, badging=BADGING):
        def simulated_sdk_tool(name):
            return "/android/" + name
        def simulated_run(arguments):
            if arguments[0].endswith("aapt"):
                return badging
            if arguments[0].endswith("apksigner"):
                return "Verified"
            raise AssertionError("Unexpected external command")
        with patch.object(mod, "sdk_tool", side_effect=simulated_sdk_tool):
            with patch.object(mod, "run_tool", side_effect=simulated_run) as called:
                result = mod.verify_apk(self.apk)
        self.assertEqual(called.call_count, 2)
        return result

    def test_valid_apk_checks_integrity_and_signature(self):
        self.make_apk()
        self.assertIn("valid signature", self.verify_with_mock_aapt())

    def test_nested_streaming_assets_location_supported(self):
        self.make_apk(jar_name="assets/StreamingAssets/" + mod.JAR_NAME)
        self.assertIn("Minecraft 1.21.5", self.verify_with_mock_aapt())

    def test_missing_jar_is_rejected_before_aapt(self):
        self.make_apk(include_jar=False)
        with self.assertRaisesRegex(mod.ApkVerificationError, "exactly one"):
            self.verify_with_mock_aapt()

    def test_mismatched_minecraft_jar_is_rejected(self):
        self.make_apk(minecraft="1.21.1")
        with self.assertRaisesRegex(mod.ApkVerificationError, "Minecraft version"):
            self.verify_with_mock_aapt()

    def test_wrong_jar_entrypoint_is_rejected(self):
        self.make_apk(include_classes=False)
        with self.assertRaisesRegex(mod.ApkVerificationError, "classes"):
            self.verify_with_mock_aapt()

    def test_missing_arm64_unity_runtime_rejected(self):
        self.make_apk(include_runtime=False)
        with self.assertRaisesRegex(mod.ApkVerificationError, "ARM64"):
            self.verify_with_mock_aapt()

    def test_armv7_code_rejected(self):
        self.make_apk(armv7=True)
        with self.assertRaisesRegex(mod.ApkVerificationError, "ARMv7"):
            self.verify_with_mock_aapt()

    def test_android_phone_incompatible_required_headtracking_rejected(self):
        self.make_apk()
        badging = BADGING.replace(
            "uses-feature-not-required: name='android.hardware.vr.headtracking'",
            "uses-feature: name='android.hardware.vr.headtracking'",
        )
        with self.assertRaisesRegex(mod.ApkVerificationError, "phone install blocked"):
            self.verify_with_mock_aapt(badging)

    def test_missing_foxcraft_brand_rejected(self):
        self.make_apk()
        badging = BADGING.replace("application-label:'FoxCraft'",
                                 "application-label:'QuestCraft'")
        with self.assertRaisesRegex(mod.ApkVerificationError, "application label"):
            self.verify_with_mock_aapt(badging)

    def test_questcraft_package_id_rejected(self):
        self.make_apk()
        badging = BADGING.replace(mod.PACKAGE, "com.qcxr.qcxr")
        with self.assertRaisesRegex(mod.ApkVerificationError, "identifier"):
            self.verify_with_mock_aapt(badging)

    def test_unexpected_launcher_activity_rejected(self):
        self.make_apk()
        badging = BADGING.replace(mod.ACTIVITY, "another.MainActivity")
        with self.assertRaisesRegex(mod.ApkVerificationError, "activity"):
            self.verify_with_mock_aapt(badging)


if __name__ == "__main__":
    unittest.main()
