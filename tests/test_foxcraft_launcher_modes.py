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
        self.assertIn("lastSelectedInstanceName", config)
        self.assertIn("dropdown.options.FindIndex", config)
        self.assertIn("RestoreLastSelectedInstance()", read("Assets/Scripts/JNIStorage.cs"))
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
        self.assertIn("modeToggle", menu)
        self.assertIn("expanded = !expanded", menu)
        self.assertIn('new Vector2(-145f, 55f)', menu)
        self.assertIn('root.anchorMin = new Vector2(0.5f, 0.5f)', menu)
        self.assertIn("closeButton.onClick.AddListener", menu)
        self.assertNotIn('root.anchoredPosition = new Vector2(0f, 160f)', menu)

    def test_questcraft_profile_changes_save_immediately_across_four_dropdowns(self):
        ui = read("Assets/Scripts/UIHandler.cs")
        config = read("Assets/Scripts/ConfigHandler.cs")
        for dropdown in (
            "dropdownMain", "dropdownInstanceCreator",
            "dropdownModInfo", "dropdownModSearch"
        ):
            self.assertIn(f"OnSelectedInstanceChanged({dropdown})", ui)
        self.assertIn("private void OnSelectedInstanceChanged(TMP_Dropdown origin)", ui)
        self.assertIn("origin.options[origin.value] == null", ui)
        self.assertIn("UpdateDropdowns(false, null)", ui)
        self.assertIn("configHandler.SetLastSelectedInstance(", ui)
        self.assertIn("SetValueWithoutNotify(selectedInstance)", ui)
        self.assertIn("config.lastSelectedInstanceName =", config)

    def test_failed_launch_rechecks_real_play_readiness_before_unlocking_ui(self):
        ui = read("Assets/Scripts/UIHandler.cs")
        kill = ui.split("public void KillSetter()", 1)[1]
        self.assertIn("UILoginCheck();", kill)
        self.assertIn("FoxCraftLaunchGate.CanPlay", ui)
        self.assertIn("FoxCraftLaunchGate.CanManageInstances", ui)

    def test_play_button_requires_downloads_and_verified_account(self):
        ui = read("Assets/Scripts/UIHandler.cs")
        progress = read("Assets/Scripts/ProgressBarManager.cs")
        gate = read("Assets/Scripts/FoxCraftLaunchGate.cs")
        self.assertIn("FoxCraftLaunchGate.CanPlay", ui)
        self.assertIn("FoxCraftLaunchGate.CanManageInstances", ui)
        self.assertIn("ProgressBarManager.DownloadsReady", ui)
        self.assertIn("ProgressBarManager.started", ui)
        self.assertIn("public static bool DownloadsReady", progress)
        self.assertIn("if (JNIStorage.apiClass == null)", progress)
        self.assertIn("if (!completed && playButton != null)", progress)
        self.assertNotIn("playButton.interactable = true;", progress)
        self.assertIn("authenticated && downloadsReady && !launchInProgress", gate)
        self.assertIn("!demoAccount", gate)

    def test_unity_launcher_restart_clears_stale_jni_and_download_flags(self):
        storage = read("Assets/Scripts/JNIStorage.cs")
        progress = read("Assets/Scripts/ProgressBarManager.cs")
        self.assertIn("Application.unloading -= CloseXR;", storage)
        self.assertIn("Application.unloading += CloseXR;", storage)
        startup = storage.split("private void Start()", 1)[1].split(
            "private void FillSupportedVersions", 1
        )[0]
        for assignment in (
            "apiClass = null;", "accountObj = null;",
            "instancesObj = null;", "activity = null;"
        ):
            self.assertIn(assignment, startup)
        awake = progress.split("private void Awake()", 1)[1].split(
            "void Update()", 1
        )[0]
        self.assertIn("started = false;", awake)
        self.assertIn("DownloadsReady = false;", awake)

    def test_phone_launch_never_uses_vr_renderer(self):
        button = read("Assets/Scripts/InstanceButton.cs")
        runtime = read("Assets/Scripts/FoxCraftPhoneRuntime.cs")
        self.assertIn("configHandler.IsPhoneModeSelected", button)
        self.assertIn("FoxCraftPhoneRuntime.TryLaunch", button)
        self.assertIn("JNIStorage.accountObj == null", button)
        self.assertIn("HasAuthenticatedAccount", read("Assets/Scripts/UIHandler.cs"))
        self.assertIn("FoxCraftDevice.IsAndroidPhone", button)
        self.assertIn("configHandler.IsPhoneModeSelected", button)
        self.assertIn("XRGeneralSettings.Instance != null", button)
        self.assertIn("manager.DeinitializeLoader()", button)
        self.assertIn("launchInstance", button)
        self.assertIn("MobileMinecraftBridge", runtime)
        self.assertIn('isAvailable', runtime)
        self.assertIn('new AndroidJavaClass(BridgeClass)', runtime)
        self.assertNotIn('CallStatic("launchInstance"', runtime)

    def test_mod_manager_handles_missing_game_and_escaped_modrinth_queries(self):
        source = read("Assets/Scripts/ModManager.cs")
        self.assertIn("UnityWebRequest.EscapeURL(", source)
        self.assertIn("string encodedFacets", source)
        self.assertIn("PojlibInstance installed = JNIStorage.GetInstance(", source)
        self.assertIn("installed == null || installed.raw == null", source)
        self.assertIn("action.interactable = false", source)
        self.assertIn("Сначала установите выбранную сборку Minecraft.", source)
        self.assertNotIn(
            "fetchedmod.Contains(JNIStorage.GetInstance(InstanceButton.currInstName).versionName)",
            source,
        )

    def test_new_minecraft_profile_installation_is_nonblocking(self):
        source = read("Assets/Scripts/InstanceButton.cs")
        progress = read("Assets/Scripts/ProgressBarManager.cs")
        ui = read("Assets/Scripts/UIHandler.cs")
        self.assertIn("IEnumerator InstallDefaultInstance(string name)", source)
        self.assertIn('new Thread(() =>', source)
        self.assertIn('"createNewInstance"', source)
        self.assertIn("pendingInstall", source)
        self.assertIn("InstallTimeoutSeconds = 600f", source)
        self.assertIn("private static PrelaunchState activeInstall;", source)
        self.assertIn("activeInstall = state;", source)
        self.assertIn("!activeInstall.completed", source)
        self.assertIn("if (IsInstallingInstance)", source)
        self.assertIn("StartCoroutine(InstallDefaultInstance(currInstName))", source)
        self.assertIn("configHandler.config.lastSelectedInstanceName = name;", source)
        self.assertIn("InstanceButton.IsInstallingInstance", ui)
        self.assertIn("InstanceButton.IsInstallingInstance", progress)
        self.assertIn("Установка сборки Minecraft", progress)

    def test_failed_vr_launch_restores_play_button_and_never_uses_stale_selection(self):
        button = read("Assets/Scripts/InstanceButton.cs")
        self.assertIn("var selectedDropdown = JNIStorage.instance.instancesDropdown;", button)
        self.assertIn("currInstName = selectedDropdown.options[selectedIndex].text;", button)
        self.assertIn("ProgressBarManager.started = false;", button)
        self.assertIn("uiHandler.KillSetter();", button)
        self.assertIn("PrelaunchTimeoutSeconds = 150f", button)
        self.assertIn("GameReadyTimeoutSeconds = 180f", button)
        self.assertIn("waitingForGameReady", button)
        self.assertIn("sawGameNotReady", button)
        self.assertIn("javaLaunchDispatched", button)
        self.assertIn('!JNIStorage.apiClass.GetStatic<bool>("gameReady")', button)
        self.assertIn("if (pendingGameLaunch != null)", button)
        self.assertIn("pendingPrelaunch", button)
        self.assertIn("FinishVrHandoff()", button)
        self.assertIn("new WaitForSecondsRealtime(0.2f)", button)
        self.assertIn("ScreenFade == null", button)
        self.assertIn("manager.DeinitializeLoader()", button)
        self.assertNotIn("Task.Delay(200)", button)
        self.assertIn("Time.realtimeSinceStartup < deadline", button)
        self.assertIn("if (!pendingPrelaunch.completed)", button)
        self.assertIn("selected == null || JNIStorage.apiClass == null", button)
        self.assertIn("Сначала выберите сборку Minecraft", button)

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

    def test_phone_waits_for_android_detection_without_disabling_vr_input(self):
        device = read("Assets/Scripts/FoxCraftDevice.cs")
        camera = read("Assets/Scripts/DesktopCam.cs")
        ui = read("Assets/Scripts/UIHandler.cs")
        touch = read("Assets/Scripts/FoxCraftPhoneTouchInput.cs")
        config = read("Assets/Scripts/ConfigHandler.cs")
        self.assertIn("IsDetectionPending", device)
        self.assertIn("CompleteDetectionWithPhoneFallback", device)
        self.assertIn("FoxCraftDevice.IsDetectionPending", camera)
        self.assertIn("yield break;", camera)
        self.assertIn("CompleteDetectionWithPhoneFallback();", camera)
        self.assertIn("Application.platform == RuntimePlatform.Android", ui)
        self.assertIn("gameObject.AddComponent<FoxCraftPhoneTouchInput>()", ui)
        self.assertIn("while (FoxCraftDevice.IsDetectionPending", touch)
        self.assertIn("CompleteDetectionWithPhoneFallback();", touch)
        self.assertIn("if (!FoxCraftDevice.IsAndroidPhone)", touch)
        self.assertIn("autoLaunchMode", config)
        self.assertIn("config.autoLaunchMode = false;", config)

    def test_android_activity_failure_does_not_leave_login_waiting_forever(self):
        storage = read("Assets/Scripts/JNIStorage.cs")
        login = read("Assets/Scripts/LoginHandler.cs")
        self.assertIn("if (activity == null)", storage)
        self.assertIn("UnityPlayerActivity was not created", storage)
        self.assertIn("InitializationFailed = true", storage)
        self.assertIn("JNIStorage.instance.InitializationFailed", login)
        self.assertIn("yield break;", login)

    def test_phone_network_detection_accepts_validated_mobile_data(self):
        network = read("Assets/Scripts/FoxCraftNetworkStatus.cs")
        main = read("Assets/Scripts/JNIStorage.cs")
        manifest = read("Assets/Plugins/Android/AndroidManifest.xml")
        self.assertIn('getSystemService', network)
        self.assertIn('getActiveNetwork', network)
        self.assertIn('getNetworkCapabilities', network)
        self.assertIn('NetCapabilityValidated = 16', network)
        self.assertIn('FoxCraftNetworkStatus.HasValidatedInternet()', main)
        self.assertIn('android.permission.ACCESS_NETWORK_STATE', manifest)

    def test_app_can_be_installed_on_android_without_vr_tracking(self):
        manifest = read("Assets/Plugins/Android/AndroidManifest.xml")
        flags = re.findall(
            r'<uses-feature[^>]*android:name="android.hardware.vr.headtracking"[^>]*>',
            manifest,
        )
        self.assertEqual(len(flags), 1)
        self.assertIn('tools:replace="android:required"', flags[0])
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
        android_icons = settings.split("  - m_BuildTarget: Android", 1)[1].split(
            "  - m_BuildTarget: iPhone", 1)[0]
        self.assertGreaterEqual(
            android_icons.count("guid: 54840f8d81764639a139cdd31fc683f3"), 13
        )
        self.assertNotIn("839b852ee4fcea3409f7f8e239536537", settings)
        self.assertIn("androidUseCustomKeystore: 0", settings)
        manifest = read("Assets/Plugins/Android/AndroidManifest.xml")
        self.assertIn('android:label="FoxCraft"', manifest)
        self.assertNotIn('android:value="vr_only"', manifest)
        self.assertIn("FoxCraft VR", builder)
        self.assertIn("FoxCraftIcon.png", builder)
        self.assertIn("Pojlib-release.aar", builder)
        self.assertIn("VerifyAsset", builder)
        self.assertTrue((ROOT / "Assets/Resources/FoxCraftIcon.png").stat().st_size > 1024)

    def test_instance_mod_menu_handles_empty_profiles_and_offline_modrinth(self):
        manager = read("Assets/Scripts/InstanceManager.cs")
        parser = read("Assets/Scripts/APIParser.cs")
        self.assertIn("if (instance == null)", manager)
        self.assertIn("if (modList.Length == 0)", manager)
        self.assertIn("if (slugs.Count == 0)", manager)
        self.assertIn("UnityWebRequest.EscapeURL(JsonConvert.SerializeObject(slugs))", manager)
        self.assertIn("www.timeout = 15;", manager)
        self.assertIn("if (response != null)", manager)
        self.assertIn("catch (JsonException exception)", manager)
        self.assertIn("if (requestId != instanceInfoRequestId)", manager)
        self.assertIn('icon_url = "OFFLINE"', manager)
        self.assertNotIn('request = request.Remove(request.Length - 1, 1)', manager)
        self.assertIn("if (installedMods == null)", parser)
        self.assertIn("if (mod != null)", parser)

    def test_releases_come_from_foxcraft_not_questcraft(self):
        feed = read("Assets/Scripts/LoadReleases.cs")
        self.assertIn("lisvovkaprizrak-maker/FoxCraft-VR/releases", feed)
        self.assertNotIn("QuestCraftPlusPlus/Questcraft/releases", feed)
        self.assertIn("releases.Count == 0", feed)


if __name__ == "__main__":
    unittest.main()
