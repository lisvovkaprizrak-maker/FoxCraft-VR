"""Offline CI preflight for the experimental FoxCraft Minecraft 1.21.5 APK route.

These tests verify source wiring only; they do not compile Unity or run a Quest.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

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
        self.assertIn('Get<string>("versionName")', installer)
        self.assertIn('!IsCorrectMinecraftVersion(instance)', installer)
        self.assertIn('!UsesFabricLoader(instance)', installer)
        self.assertIn('StartsWith("net.fabricmc.loader."', installer)
        self.assertIn('EndsWith(".KnotClient"', installer)
        self.assertIn("FoxCraftInGameKeyboardInstaller.EnsureInstalled", launcher)
        self.assertIn('foxcraft-keyboard-0.1.0-alpha.jar', installer)
        self.assertIn('Get<string>("gameDir")', installer)
        self.assertIn('GetStatic<string>("USER_HOME")', installer)
        self.assertIn('Path.Combine(resolvedGame, "mods")', installer)
        self.assertIn('SHA256.Create()', installer)
        self.assertIn('Guid.NewGuid().ToString("N")', installer)
        self.assertIn('FileMode.CreateNew', installer)
        self.assertIn('sha.ComputeHash(staged)', installer)
        self.assertLess(installer.index("FileMode.CreateNew"),
                        installer.index("sha.ComputeHash(staged)"))
        self.assertLess(installer.index("sha.ComputeHash(staged)"),
                        installer.index("File.Move(temporary, destination)"))
        self.assertIn('if (File.Exists(destination))\n                return false;', installer)

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
        self.assertIn("bool initialCustomKeystore = PlayerSettings.Android.useCustomKeystore", build)
        self.assertIn("PlayerSettings.Android.useCustomKeystore = false;", build)
        self.assertIn("PlayerSettings.Android.useCustomKeystore = initialCustomKeystore;", build)
        self.assertLess(
            build.index("PlayerSettings.Android.useCustomKeystore = false;"),
            build.index("BuildPipeline.BuildPlayer("),
        )

    def test_main_unity_scene_is_enabled_for_apk(self):
        settings = (ROOT / "ProjectSettings/EditorBuildSettings.asset").read_text(
            encoding="utf-8"
        )
        self.assertIn("- enabled: 1\n    path: Assets/Scenes/Main.unity", settings)
        self.assertTrue((ROOT / "Assets/Scenes/Main.unity").is_file())

    def test_android_manifest_preserves_pojlib_vr_activity(self):
        android = "{http://schemas.android.com/apk/res/android}"
        root = ET.parse(ROOT / "Assets/Plugins/Android/AndroidManifest.xml").getroot()
        activity = root.find("./application/activity")
        self.assertIsNotNone(activity)
        self.assertEqual(activity.attrib[android + "name"],
                         "pojlib.UnityPlayerActivity")
        self.assertEqual(activity.attrib[android + "exported"], "true")
        self.assertEqual(activity.attrib[android + "screenOrientation"], "landscape")
        categories = {
            el.attrib[android + "name"]
            for el in activity.findall("./intent-filter/category")
        }
        self.assertIn("com.oculus.intent.category.VR", categories)
        permissions = {
            el.attrib[android + "name"] for el in root.findall("./uses-permission")
        }
        self.assertIn("org.khronos.openxr.permission.OPENXR_SYSTEM", permissions)

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
        apk_verifier = (ROOT / "tools/verify_foxcraft_test_apk.py").read_text(encoding="utf-8")
        self.assertIn('PACKAGE = "com.foxcraftvr.quest"', apk_verifier)
        self.assertIn('tools/verify_foxcraft_test_apk.py Builds/FoxCraft-1.21.5-UNTESTED.apk', workflow)
        self.assertIn("tools/stage_foxcraft_ingame_keyboard.py", workflow)
        self.assertIn("tools/check_foxcraft_pojlib_api.py", workflow)
        self.assertLess(workflow.index("Build the matching 1.21.5 Fabric keyboard JAR"),
                        workflow.index("Build separate FoxCraft test APK using Unity"))
        self.assertLess(workflow.index("Verify signed APK, ARM64, launch activity and embedded Fabric keyboard"),
                        workflow.index("Upload untested Meta Quest APK"))


if __name__ == "__main__":
    unittest.main()
