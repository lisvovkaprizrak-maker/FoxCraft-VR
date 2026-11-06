"""Offline CI preflight for the experimental FoxCraft Minecraft 1.21.5 APK route.

These tests verify source wiring only; they do not compile Unity or run a Quest.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class KeyboardApkPipelineTests(unittest.TestCase):
    def test_only_matching_fabric_version_is_installed(self):
        launcher = (ROOT / "Assets/Scripts/InstanceButton.cs").read_text(encoding="utf-8")
        installer = (
            ROOT / "Assets/Scripts/FoxCraftInGameKeyboardInstaller.cs"
        ).read_text(encoding="utf-8")

        self.assertIn('selected.versionName, "1.21.5"', launcher)
        self.assertIn("FoxCraftInGameKeyboardInstaller.UsesFabricLoader(selected.raw)", launcher)
        self.assertIn('Get<string>("mainClass")', installer)
        self.assertIn('StartsWith("net.fabricmc.loader."', installer)
        self.assertIn('EndsWith(".KnotClient"', installer)
        self.assertIn("FoxCraftInGameKeyboardInstaller.EnsureInstalled", launcher)
        self.assertIn('foxcraft-keyboard-0.1.0-alpha.jar', installer)
        self.assertIn('Get<string>("gameDir")', installer)
        self.assertIn('GetStatic<string>("USER_HOME")', installer)
        self.assertIn('Path.Combine(resolvedGame, "mods")', installer)
        self.assertIn('SHA256.Create()', installer)

    def test_bundled_pojlib_boolean_prelaunch_before_staging_and_launch(self):
        launcher = (ROOT / "Assets/Scripts/InstanceButton.cs").read_text(encoding="utf-8")
        self.assertIn('CallStatic<bool>("prelaunch",', launcher)
        self.assertIn('Pojlib prelaunch checks failed', launcher)
        self.assertNotIn('CallStatic("prelaunch",', launcher)
        self.assertLess(
            launcher.index('CallStatic<bool>("prelaunch",'),
            launcher.index('FoxCraftInGameKeyboardInstaller.EnsureInstalled('),
        )
        self.assertLess(
            launcher.index('FoxCraftInGameKeyboardInstaller.EnsureInstalled('),
            launcher.index('CallStatic("launchInstance",'),
        )
        self.assertIn("AndroidJNI.DetachCurrentThread()", launcher)
        self.assertNotIn('/sdcard/Android/data/com.qcxr.qcxr', launcher)
        self.assertIn('Path.Combine(Application.persistentDataPath, "natives")', launcher)
        self.assertIn('Path.Combine(Application.persistentDataPath, "import-saves")', launcher)

    def test_manual_apk_build_requires_matching_jar_and_separate_id(self):
        build = (ROOT / "Assets/Editor/FoxCraftKeyboardAndroidBuild.cs").read_text(
            encoding="utf-8"
        )
        self.assertIn('TestPackageId = "com.foxcraftvr.quest"', build)
        self.assertIn('foxcraft-keyboard-0.1.0-alpha.jar', build)
        self.assertIn('Pojlib-release.aar', build)
        self.assertIn("AndroidArchitecture.ARM64", build)
        self.assertIn("BuildResult.Succeeded", build)
        self.assertIn("finally", build)
        self.assertIn("initialId", build)
        self.assertIn("PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, initialId)", build)

    def test_no_unattended_or_unlicensed_apk_builds(self):
        workflow = (ROOT / ".github/workflows/foxcraft-keyboard-apk.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("pull_request:", workflow)
        self.assertIn("if: github.event_name == 'workflow_dispatch'", workflow)
        self.assertIn("secrets.UNITY_LICENSE", workflow)
        self.assertIn("game-ci/unity-builder@v4", workflow)
        self.assertIn("buildMethod: FoxCraftKeyboardAndroidBuild.BuildTestApk", workflow)
        self.assertIn("unityVersion: 2022.3.62f3", workflow)
        self.assertIn("FoxCraft-Quest-1.21.5-UNTESTED-APK", workflow)
        self.assertIn("if-no-files-found: error", workflow)
        self.assertIn('com.foxcraftvr.quest', workflow)
        self.assertIn("tools/stage_foxcraft_ingame_keyboard.py", workflow)
        self.assertIn("tools/check_foxcraft_pojlib_api.py", workflow)
        self.assertLess(workflow.index("Build the matching 1.21.5 Fabric keyboard JAR"),
                        workflow.index("Build separate FoxCraft test APK using Unity"))
        self.assertLess(workflow.index("Verify APK and separate package identifier"),
                        workflow.index("Upload untested Meta Quest APK"))


if __name__ == "__main__":
    unittest.main()
