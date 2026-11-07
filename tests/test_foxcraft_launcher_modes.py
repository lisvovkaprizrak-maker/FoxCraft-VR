"""Static preflight for FoxCraft's single VR/phone QuestCraft-based launcher.

These tests intentionally do NOT claim phone Minecraft or Quest XR hardware works.
A real device and the separate 2D bridge are required for that.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")


class FoxCraftLauncherModeTests(unittest.TestCase):
    def test_original_questcraft_menu_still_present(self):
        main = read("Assets/Scripts/UIHandler.cs")
        for name in ("dropdownMain", "dropdownModSearch", "dropdownModInfo",
                     "dropdownInstanceCreator", "playButton", "modsButton",
                     "instancesButton", "logoutButton", "needHelpButton"):
            self.assertIn(name, main)
        self.assertIn("FoxCraftLaunchModeMenu", main)

    def test_phone_version_is_independent_from_vr_instance(self):
        config = read("Assets/Scripts/ConfigHandler.cs")
        self.assertIn('public const string VrMode = "vr"', config)
        self.assertIn('public const string PhoneMode = "phone"', config)
        self.assertIn("public string phoneVersion;", config)
        self.assertIn("phoneVersionCache", config)
        self.assertIn("GetCachedPhoneVersions", config)
        self.assertIn("SetCachedPhoneVersions", config)
        self.assertIn("public void SetPhoneVersion", config)
        self.assertIn("public void SetLaunchMode", config)
        self.assertIn("config.accounts", config)
        menu = read("Assets/Scripts/FoxCraftLaunchModeMenu.cs")
        self.assertIn("config.SelectedPhoneVersion", menu)
        self.assertIn("version_manifest_v2.json", menu)
        self.assertIn("raycastTarget = false", menu)
        self.assertIn('entry.type == "release"', menu)

    def test_phone_launch_never_uses_vr_renderer(self):
        button = read("Assets/Scripts/InstanceButton.cs")
        runtime = read("Assets/Scripts/FoxCraftPhoneRuntime.cs")
        self.assertIn("configHandler.IsPhoneModeSelected", button)
        self.assertIn("FoxCraftPhoneRuntime.TryLaunch", button)
        self.assertIn("FoxCraftDevice.IsAndroidPhone", button)
        self.assertIn("launchInstance", button)
        self.assertIn("MobileMinecraftBridge", runtime)
        self.assertIn('isAvailable', runtime)
        self.assertIn('new AndroidJavaClass(BridgeClass)', runtime)
        self.assertNotIn('CallStatic("launchInstance"', runtime)

    def test_phone_uses_existing_flat_questcraft_camera_and_android_keyboard(self):
        device = read("Assets/Scripts/FoxCraftDevice.cs")
        camera = read("Assets/Scripts/DesktopCam.cs")
        keyboard = read("Assets/Scripts/KeyboardHandler.cs")
        config = read("Assets/Scripts/ConfigHandler.cs")
        self.assertIn('model.Contains("quest")', device)
        self.assertIn('model.Contains("pico")', device)
        self.assertIn("FoxCraftDevice.IsVrHeadset", camera)
        self.assertIn("DesktopCamera.SetActive(true)", camera)
        self.assertIn("screenCanvas.worldCamera = flatCamera", camera)
        self.assertIn("FoxCraftDevice.IsAndroidPhone", keyboard)
        self.assertIn("keyboard.SetActive(false)", keyboard)
        self.assertIn("FoxCraftDevice.IsVrHeadset", config)
        touch = read("Assets/Scripts/FoxCraftPhoneTouchInput.cs")
        self.assertIn("FoxCraftDevice.IsAndroidPhone", touch)
        self.assertIn("InputSystemUIInputModule", touch)
        self.assertIn("touch.AssignDefaultActions()", touch)
        self.assertIn("GraphicRaycaster", touch)
        self.assertIn("FoxCraftPhoneTouchInput", read("Assets/Scripts/UIHandler.cs"))

    def test_app_can_be_installed_on_android_without_vr_tracking(self):
        manifest = read("Assets/Plugins/Android/AndroidManifest.xml")
        flags = re.findall(
            r'<uses-feature[^>]*android:name="android.hardware.vr.headtracking"[^>]*>',
            manifest,
        )
        self.assertTrue(flags)
        for flag in flags:
            self.assertIn('android:required="false"', flag)
        self.assertIn('android.intent.category.LAUNCHER', manifest)

    def test_branding_and_apk_must_include_real_runtime_assets(self):
        builder = read("Assets/Editor/FoxCraftKeyboardAndroidBuild.cs")
        self.assertIn("com.foxcraftvr.quest", builder)
        settings = read("ProjectSettings/ProjectSettings.asset")
        self.assertIn("productName: FoxCraft", settings)
        self.assertIn("companyName: FoxCraft Project", settings)
        self.assertIn("Android: com.foxcraftvr.quest", settings)
        self.assertIn("androidUseCustomKeystore: 0", settings)
        manifest = read("Assets/Plugins/Android/AndroidManifest.xml")
        self.assertIn('android:label="FoxCraft"', manifest)
        self.assertNotIn('android:value="vr_only"', manifest)
        self.assertIn("FoxCraft VR", builder)
        self.assertIn("FoxCraftIcon.png", builder)
        self.assertIn("Pojlib-release.aar", builder)
        self.assertIn("VerifyAsset", builder)
        self.assertTrue((ROOT / "Assets/Resources/FoxCraftIcon.png").stat().st_size > 1024)

    def test_releases_come_from_foxcraft_not_questcraft(self):
        feed = read("Assets/Scripts/LoadReleases.cs")
        self.assertIn("lisvovkaprizrak-maker/FoxCraft-VR/releases", feed)
        self.assertNotIn("QuestCraftPlusPlus/Questcraft/releases", feed)
        self.assertIn("releases.Count == 0", feed)


if __name__ == "__main__":
    unittest.main()
