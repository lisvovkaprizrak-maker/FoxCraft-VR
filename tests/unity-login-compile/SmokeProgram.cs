// Runtime tests for the *real* ConfigHandler and LoginHandler using Unity API
// doubles and System.Text.Json. Runs in GitHub Actions, not on a Quest.
using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using TMPro;
using UnityEngine;

public static class SmokeProgram
{
    private static int checks;

    private static void Check(bool condition, string message)
    {
        if (!condition)
            throw new InvalidOperationException("FoxCraft startup smoke failed: " + message);
        checks++;
    }

    private static ConfigHandler NewSettings()
    {
        return new ConfigHandler
        {
            windowHandler = new WindowHandler(),
            devHandler = new DevHandler(),
        };
    }

    public static int Main()
    {
        string folder = Path.Combine(Path.GetTempPath(),
            "foxcraft-startup-smoke-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(folder);
        try
        {
            Application.persistentDataPath = folder;
            Application.platform = RuntimePlatform.Android;
            // Real Activity has been initialized by Unity before normal
            // launcher configuration is loaded on an ordinary Android phone.
            JNIStorage.activity = new AndroidJavaObject();
            ConfigHandler initial = NewSettings();
            initial.LoadConfig();
            Check(File.Exists(ConfigHandler.configPath), "first-run settings created");
            Check(initial.config.accounts.Count == 0, "first-run accounts empty");
            Check(initial.config.launchMode == ConfigHandler.PhoneMode,
                "phone is selected on non-headset device");

            // Changing modes/phone versions must never reset account/profile data.
            initial.config.accounts.Add(new ConfigHandler.Accounts {
                username = "SavedAccount", uuid = "test-uuid"
            });
            initial.SaveConfig();
            initial.SetLaunchMode(ConfigHandler.VrMode);
            initial.SetPhoneVersion("1.21.5");
            initial.SetLastSelectedInstance(2);
            initial.SetCachedPhoneVersions(new List<string> {
                "1.21.5", "../../invalid", "1.20.1", "1.21.5"
            });
            ConfigHandler restored = NewSettings();
            restored.LoadConfig();
            Check(restored.config.accounts.Count == 1 &&
                restored.config.accounts[0].uuid == "test-uuid",
                "accounts survive mode/version changes");
            Check(restored.config.launchMode == ConfigHandler.VrMode,
                "mode survives restart");
            Check(restored.config.lastSelectedInstance == 2,
                "selected instance survives restart");
            Check(restored.GetCachedPhoneVersions().SequenceEqual(
                new[] { "1.21.5", "1.20.1" }),
                "bad version IDs filtered and duplicates removed");

            var dropdown = new TMP_Dropdown();
            dropdown.AddOptions(new List<string> { "1.20.1", "1.21.1", "1.21.5" });
            JNIStorage.instance = new JNIStorage {
                instancesDropdown = dropdown,
                uiHandler = new UIHandler(),
                configHandler = restored
            };
            restored.RestoreLastSelectedInstance();
            Check(dropdown.value == 2 && UIHandler.selectedInstance == 2,
                "saved instance restored only after options loaded");
            restored.SetLastSelectedInstance(999);
            restored.RestoreLastSelectedInstance();
            Check(dropdown.value == 2, "stale selected instance clamped");

            // Restoring by index can silently select a different Minecraft
            // version after QuestCraft updates/reorders its available profiles.
            // The last selected *name* must win over its old list position.
            restored.SetLastSelectedInstance(1);
            Check(restored.config.lastSelectedInstanceName == "1.21.1",
                "saved Minecraft instance stores its stable name");
            dropdown.ClearOptions();
            dropdown.AddOptions(new List<string> { "1.21.5", "1.20.1", "1.21.1" });
            restored.RestoreLastSelectedInstance();
            Check(dropdown.value == 2 &&
                restored.config.lastSelectedInstanceName == "1.21.1",
                "same Minecraft profile restored after version list reorder");

            ConfigHandler reorderedReload = NewSettings();
            reorderedReload.LoadConfig();
            Check(reorderedReload.config.lastSelectedInstanceName == "1.21.1",
                "profile name persists across full launcher restart");
            reorderedReload.RestoreLastSelectedInstance();
            Check(dropdown.value == 2, "restored profile matches persisted name");

            dropdown.ClearOptions();
            dropdown.AddOptions(new List<string> { "1.21.5", "1.20.1" });
            reorderedReload.RestoreLastSelectedInstance();
            Check(dropdown.value == 0, "removed profile selects safe first entry");
            Check(reorderedReload.config.lastSelectedInstanceName == "1.21.5",
                "missing profile fallback is saved to launcher config");

            // Drive the real phone version picker with a short OFFLINE list.
            // The 10-version shortcut previously generated a negative array
            // index when the cached list contained fewer than 10 releases.
            var selector = new FoxCraftLaunchModeMenu();
            var configField = typeof(FoxCraftLaunchModeMenu).GetField("config",
                BindingFlags.Instance | BindingFlags.NonPublic);
            var listField = typeof(FoxCraftLaunchModeMenu).GetField("phoneVersions",
                BindingFlags.Instance | BindingFlags.NonPublic);
            var stepMethod = typeof(FoxCraftLaunchModeMenu).GetMethod("StepPhoneVersion",
                BindingFlags.Instance | BindingFlags.NonPublic);
            Check(configField != null && listField != null && stepMethod != null,
                "phone release picker controls are testable");
            configField.SetValue(selector, reorderedReload);
            var available = (List<string>)listField.GetValue(selector);
            available.AddRange(new[] { "1.21.5", "1.20.1", "1.19.4" });
            reorderedReload.SetPhoneVersion("1.20.1");
            stepMethod.Invoke(selector, new object[] { -10 });
            Check(reorderedReload.SelectedPhoneVersion == "1.21.5",
                "back ten wraps correctly with only three offline releases");
            stepMethod.Invoke(selector, new object[] { 10 });
            Check(reorderedReload.SelectedPhoneVersion == "1.20.1",
                "forward ten wraps correctly with three offline releases");
            reorderedReload.SetPhoneVersion("1.18.2");
            stepMethod.Invoke(selector, new object[] { -1 });
            Check(reorderedReload.SelectedPhoneVersion == "1.19.4",
                "removed selected phone version navigates to last cached version");
            ConfigHandler phoneReload = NewSettings();
            phoneReload.LoadConfig();
            Check(phoneReload.SelectedPhoneVersion == "1.19.4" &&
                phoneReload.config.lastSelectedInstanceName == "1.21.5",
                "phone version and VR profile survive separately across restart");

            // Simulate a startup where LoginHandler starts BEFORE JNIStorage.
            JNIStorage.instance = null;
            JNIStorage.apiClass = null;
            JNIStorage.activity = null;
            AndroidJavaClass.LoginCallCount = 0;
            LoginHandler login = new LoginHandler {
                loginText = new GameObject(),
                handler = new WindowHandler()
            };
            var accountField = typeof(LoginHandler).GetField("accountDropdown",
                BindingFlags.Instance | BindingFlags.NonPublic);
            accountField.SetValue(login, new TMP_Dropdown());
            var start = typeof(LoginHandler).GetMethod("Start",
                BindingFlags.Instance | BindingFlags.NonPublic);
            IEnumerator routine = (IEnumerator)start.Invoke(login, null);
            Check(routine.MoveNext(), "login waits a frame for Unity Start");
            Check(routine.MoveNext() && AndroidJavaClass.LoginCallCount == 0,
                "login does not attempt JNI before initialization");
            JNIStorage.instance = new JNIStorage {
                instancesDropdown = dropdown,
                uiHandler = new UIHandler(),
                configHandler = restored
            };
            JNIStorage.apiClass = new AndroidJavaClass();
            JNIStorage.activity = new AndroidJavaObject();
            Check(!routine.MoveNext() && AndroidJavaClass.LoginCallCount == 1,
                "account login begins exactly once when storage ready");
            Check(!login.HasAuthenticatedAccount,
                "Play remains blocked until Pojlib confirms Microsoft account");

            var verifiedLogin = typeof(LoginHandler).GetMethod("CompleteVerifiedLogin",
                BindingFlags.Instance | BindingFlags.NonPublic);
            Check(verifiedLogin != null, "verified account completion is testable");
            var original = new AndroidJavaObject {
                uuid = "test-uuid", username = "UpdatedMinecraftName", isDemoMode = false
            };
            verifiedLogin.Invoke(login, new object[] {
                original, original.username, original.uuid
            });
            Check(login.HasAuthenticatedAccount,
                "Play unlocks only after verified MinecraftAccount is present");
            Check(restored.config.accounts.Count == 1 &&
                restored.config.accounts[0].username == "UpdatedMinecraftName",
                "renamed Minecraft accounts retain their stored UUID without duplicates");
            Check(restored.config.lastSelectedAccount == 0,
                "restored account selection points to the authenticated account");

            login.Login();
            Check(!login.HasAuthenticatedAccount && JNIStorage.accountObj == null,
                "switching Microsoft accounts immediately clears previous Play access");
            Check(AndroidJavaClass.LoginCallCount == 2,
                "new login attempt invokes Pojlib again exactly once");
            var another = new AndroidJavaObject {
                uuid = "different-uuid", username = "AnotherAccount", isDemoMode = true
            };
            verifiedLogin.Invoke(login, new object[] {
                another, another.username, another.uuid
            });
            Check(restored.config.accounts.Count == 2 &&
                restored.config.lastSelectedAccount == 1,
                "new Microsoft account is saved and selected by UUID");
            Check(login.isDemoMode && login.HasAuthenticatedAccount,
                "verified demo-mode flag comes from Java MinecraftAccount");
            var persistedAccounts = NewSettings();
            persistedAccounts.LoadConfig();
            Check(persistedAccounts.config.accounts.Count == 2 &&
                persistedAccounts.config.lastSelectedAccount == 1,
                "verified Microsoft identities and choice survive restart");
            typeof(LoginHandler).GetMethod("OnDestroy",
                BindingFlags.Instance | BindingFlags.NonPublic).Invoke(login, null);

            // A corrupted Java/Pojlib startup should show the original JNI
            // error, never leave LoginHandler polling the destroyed Activity.
            JNIStorage.instance = new JNIStorage {
                configHandler = restored, InitializationFailed = true
            };
            JNIStorage.apiClass = null;
            JNIStorage.activity = null;
            LoginHandler failedLogin = new LoginHandler {
                loginText = new GameObject(), handler = new WindowHandler()
            };
            accountField.SetValue(failedLogin, new TMP_Dropdown());
            IEnumerator failedStart = (IEnumerator)start.Invoke(failedLogin, null);
            Check(failedStart.MoveNext(), "login waits first Unity startup frame");
            Check(!failedStart.MoveNext(), "login exits after fatal Pojlib init error");

            // Migrate a genuine older QuestCraft config without deleting accounts,
            // restoring compatibility with pre-FoxCraft launcher.conf files.
            File.WriteAllText(ConfigHandler.configPath,
                "{\"acceptedLegal\":true,\"accounts\":[{\"username\":\"LegacyAccount\"," +
                "\"uuid\":\"legacy-uuid\"}],\"lastSelectedInstance\":1," +
                "\"customRAMValue\":\"3072\"}");
            ConfigHandler migrated = NewSettings();
            migrated.LoadConfig();
            Check(migrated.config.accounts.Count == 1 &&
                migrated.config.accounts[0].username == "LegacyAccount",
                "legacy Microsoft accounts retained on migration");
            Check(migrated.config.lastSelectedInstance == 1 &&
                migrated.config.customRAMValue == "3072",
                "legacy instance and memory preferences retained");
            Check(migrated.config.launchMode == ConfigHandler.PhoneMode &&
                migrated.config.phoneVersion == "1.21.5",
                "missing FoxCraft phone settings initialized on migration");

            // Some Meta/Quest and Pico hardware uses internal model codenames.
            // Hardware capability detection must work when the model string
            // does not contain a familiar consumer brand.
            var cachedHeadset = typeof(FoxCraftDevice).GetField(
                "detectedHeadset", BindingFlags.Static | BindingFlags.NonPublic);
            Check(cachedHeadset != null, "headset detection cache is testable");
            cachedHeadset.SetValue(null, null);
            SystemInfo.deviceModel = "unrecognized-vr-model";
            JNIStorage.activity = new AndroidJavaObject();
            AndroidJavaObject.VrTrackingFeature = true;
            AndroidJavaObject.OculusVrFeature = false;
            Check(FoxCraftDevice.IsVrHeadset, "Android headtracking feature recognizes VR");
            Check(!FoxCraftDevice.IsAndroidPhone, "VR headset must not use phone controls");

            cachedHeadset.SetValue(null, null);
            AndroidJavaObject.VrTrackingFeature = false;
            AndroidJavaObject.OculusVrFeature = false;
            Check(!FoxCraftDevice.IsVrHeadset && FoxCraftDevice.IsAndroidPhone,
                "standard Android remains a phone");
            cachedHeadset.SetValue(null, null);
            SystemInfo.deviceModel = "Meta Quest 3";
            Check(FoxCraftDevice.IsVrHeadset, "Quest model recognized without system feature");
            cachedHeadset.SetValue(null, null);
            SystemInfo.deviceModel = null;

            // Real QuestCraft VR keyboard handler must survive an empty
            // Backspace and missing optional audio fields. On phones its old
            // floating keyboard must not cover the system Android IME.
            var keyboard = new KeyboardHandler();
            var search = new TMP_InputField { text = "", enabled = true };
            var ram = new TMP_InputField { text = "4096", enabled = false };
            var instanceName = new TMP_InputField { text = "Fabric", enabled = false };
            var keyGrid = new GameObject();
            keyGrid.SetActive(true);
            foreach (var assignment in new (string, object)[] {
                ("searchBox", search), ("ramSetter", ram),
                ("instanceEditor", instanceName), ("keyboard", keyGrid)
            })
            {
                typeof(KeyboardHandler).GetField(assignment.Item1,
                    BindingFlags.NonPublic | BindingFlags.Instance)
                    .SetValue(keyboard, assignment.Item2);
            }
            keyboard.BackspacePress();
            Check(search.text == "", "empty Backspace never deletes beyond zero");
            keyboard.KeyPress("AB");
            Check(search.text == "ab", "keys enter lower-case text");
            keyboard.ShiftPress();
            keyboard.KeyPress("c");
            Check(search.text == "abC", "Shift enters upper-case text");
            keyboard.BackspacePress();
            Check(search.text == "ab", "Backspace removes last character");
            keyboard.SpacePress();
            Check(search.text == "ab ", "Space enters a space");
            search.enabled = false;
            ram.enabled = true;
            keyboard.KeyPress("7");
            Check(ram.text == "40967", "RAM field receives keys after switching tabs");
            ram.enabled = false;
            instanceName.enabled = true;
            keyboard.KeyPress("x");
            Check(instanceName.text == "FabricX",
                "profile-name field remains editable with shift active");
            instanceName.enabled = false;
            keyboard.BackspacePress();
            keyboard.KeyPress("z");
            keyboard.SpacePress();
            Check(instanceName.text == "FabricX", "hidden fields ignore stray VR key presses");
            var keyboardUpdate = typeof(KeyboardHandler).GetMethod("Update",
                BindingFlags.Instance | BindingFlags.NonPublic);
            keyboardUpdate.Invoke(keyboard, null);
            Check(!keyGrid.activeSelf, "QuestCraft floating key grid hidden on Android phone");

            // Use real FoxCraftLaunchGate rules to stop Pojlib progress
            // from overriding Microsoft account verification.
            Check(!FoxCraftLaunchGate.CanPlay(false, true, false),
                "Play stays locked without authenticated account");
            Check(!FoxCraftLaunchGate.CanPlay(true, false, false),
                "Play stays locked while Pojlib downloads Minecraft assets");
            Check(!FoxCraftLaunchGate.CanPlay(true, true, true),
                "Play stays locked while VR launch is already underway");
            Check(FoxCraftLaunchGate.CanPlay(true, true, false),
                "Play becomes available after login and completed downloads");
            Check(!FoxCraftLaunchGate.CanManageInstances(true, true, true, false),
                "demo Minecraft account cannot edit VR instances/mods");
            Check(FoxCraftLaunchGate.CanManageInstances(true, false, true, false),
                "verified full Minecraft account can edit downloaded instances");

            // Real ProgressBarManager must never re-enable Play itself. The
            // old QuestCraft manager used to turn it on on every Update
            // even before LoginHandler confirmed the Microsoft account.
            var downloads = new ProgressBarManager {
                progressBar = new UnityEngine.UI.Slider(),
                downloadText = new TextMeshProUGUI(),
                playButton = new UnityEngine.UI.Button(),
                accountDropdown = new TMP_Dropdown()
            };
            var updateDownloads = typeof(ProgressBarManager).GetMethod("Update",
                BindingFlags.Instance | BindingFlags.NonPublic);
            Check(updateDownloads != null, "download progress update is testable");
            ProgressBarManager.started = false;
            JNIStorage.apiClass = null;
            downloads.playButton.interactable = true;
            updateDownloads.Invoke(downloads, null);
            Check(!ProgressBarManager.DownloadsReady &&
                !downloads.playButton.interactable,
                "missing Pojlib disables Play without throwing");

            JNIStorage.apiClass = new AndroidJavaClass();
            AndroidJavaClass.DownloadsCompleted = false;
            AndroidJavaClass.DownloadPercentage = 0.42f;
            updateDownloads.Invoke(downloads, null);
            Check(!ProgressBarManager.DownloadsReady &&
                downloads.progressBar.gameObject.activeSelf &&
                downloads.progressBar.value > 0.4f,
                "active download shows progress and keeps Play locked");

            AndroidJavaClass.DownloadsCompleted = true;
            updateDownloads.Invoke(downloads, null);
            Check(ProgressBarManager.DownloadsReady &&
                !downloads.playButton.interactable &&
                !downloads.progressBar.gameObject.activeSelf,
                "complete download hides progress but never independently enables Play");

            AndroidJavaClass.FailDownloadProbe = true;
            updateDownloads.Invoke(downloads, null);
            Check(!ProgressBarManager.DownloadsReady &&
                !downloads.playButton.interactable &&
                downloads.downloadText.gameObject.activeSelf,
                "Pojlib download error leaves Play disabled and shows warning");
            AndroidJavaClass.FailDownloadProbe = false;

            // A Pojlib restartLauncher can restart the Unity scene inside
            // the same Android process: static completion/launch state from
            // the previous player must never unlock the new Play button.
            AndroidJavaClass.DownloadsCompleted = true;
            updateDownloads.Invoke(downloads, null);
            Check(ProgressBarManager.DownloadsReady,
                "previous Unity session can complete downloads");
            ProgressBarManager.started = true;
            var awakeDownloads = typeof(ProgressBarManager).GetMethod("Awake",
                BindingFlags.Instance | BindingFlags.NonPublic);
            Check(awakeDownloads != null, "download state boot reset is testable");
            awakeDownloads.Invoke(downloads, null);
            Check(!ProgressBarManager.started && !ProgressBarManager.DownloadsReady,
                "new Unity launcher resets stale Play/download flags");

            // Corruption must be backed up, not silently overwrite the old file.
            File.WriteAllText(ConfigHandler.configPath, "not valid json");
            ConfigHandler recovered = NewSettings();
            recovered.LoadConfig();
            string[] backups = Directory.GetFiles(folder, "launcher.conf.corrupt-*");
            Check(backups.Length == 1, "invalid config backed up");
            Check(File.ReadAllText(backups[0]) == "not valid json",
                "invalid original bytes retained");
            Check(recovered.config != null &&
                recovered.config.accounts.Count == 1 &&
                recovered.config.accounts[0].username == "LegacyAccount",
                "valid previous config restores saved Microsoft accounts");
            Check(!File.Exists(ConfigHandler.configPath + ".writing"),
                "temporary config file removed after successful write");

            // A backup can itself be corrupted. Keep the two invalid
            // originals, and only then fall back to clean defaults.
            File.WriteAllText(ConfigHandler.configPath + ".previous", "bad backup");
            File.WriteAllText(ConfigHandler.configPath, "bad primary");
            ConfigHandler noValidBackup = NewSettings();
            noValidBackup.LoadConfig();
            backups = Directory.GetFiles(folder, "launcher.conf.corrupt-*");
            Check(backups.Length == 2,
                "later corrupt primary is separately backed up");
            Check(noValidBackup.config != null &&
                noValidBackup.config.accounts.Count == 0,
                "fresh defaults used when primary and previous are invalid");

            // Hand-edited or legacy launcher.conf files may carry invalid
            // Minecraft version IDs; never feed unsafe or duplicate entries
            // into the phone version picker after a cold restart.
            noValidBackup.config.phoneVersion = "1.21.5";
            noValidBackup.config.phoneVersionCache = new List<string> {
                "../outside", "1.21.5", "1.21.5", "1.20.1", "", "1.20.1"
            };
            noValidBackup.SaveConfig();
            ConfigHandler cleanCache = NewSettings();
            cleanCache.LoadConfig();
            Check(cleanCache.GetCachedPhoneVersions().SequenceEqual(
                new[] { "1.21.5", "1.20.1" }),
                "invalid and duplicated cached phone versions are cleaned on startup");
            cleanCache.config.phoneVersionCache = new List<string> { "../../invalid" };
            cleanCache.SaveConfig();
            ConfigHandler fallbackCache = NewSettings();
            fallbackCache.LoadConfig();
            Check(fallbackCache.GetCachedPhoneVersions().SequenceEqual(
                new[] { "1.21.5" }),
                "all-invalid Minecraft cache falls back to selected safe version");

            // Android phones with mobile data must not be declared offline
            // just because Pojlib's historical connectivity test sees no Wi-Fi.
            JNIStorage.activity = new AndroidJavaObject();
            AndroidJavaObject.HasActiveNetwork = true;
            AndroidJavaObject.NetworkValidated = true;
            Check(FoxCraftNetworkStatus.HasValidatedInternet(),
                "validated Android cellular network is treated as online");
            AndroidJavaObject.NetworkValidated = false;
            Check(!FoxCraftNetworkStatus.HasValidatedInternet(),
                "unvalidated Android network is not asserted as internet");
            AndroidJavaObject.HasActiveNetwork = false;
            Check(!FoxCraftNetworkStatus.HasValidatedInternet(),
                "offline Android phone has no active network");
            AndroidJavaObject.HasActiveNetwork = true;
            AndroidJavaObject.NetworkValidated = true;
            JNIStorage.activity = null;
            Check(!FoxCraftNetworkStatus.HasValidatedInternet(),
                "network query safely handles missing Android activity");
            JNIStorage.activity = new AndroidJavaObject();
            Application.platform = RuntimePlatform.WindowsEditor;
            Check(!FoxCraftNetworkStatus.HasValidatedInternet(),
                "network query skips Java on desktop Unity");
            Application.platform = RuntimePlatform.Android;

            // Delayed Android classification must never commit a guessed
            // device mode. Confirm unknown headsets default conservatively to
            // VR, but switch to phone after the actual Android features load.
            var detectorCache = typeof(FoxCraftDevice).GetField(
                "detectedHeadset", BindingFlags.Static | BindingFlags.NonPublic);
            var updateConfig = typeof(ConfigHandler).GetMethod("Update",
                BindingFlags.Instance | BindingFlags.NonPublic);
            Check(detectorCache != null && updateConfig != null,
                "deferred Android mode selection is testable");

            detectorCache.SetValue(null, null);
            SystemInfo.deviceModel = "unknown-android-device";
            JNIStorage.activity = null;
            Check(FoxCraftDevice.IsDetectionPending && !FoxCraftDevice.IsAndroidPhone,
                "phone detection stays pending until Activity exists");

            string delayedFolder = Path.Combine(folder, "delayed-device");
            Directory.CreateDirectory(delayedFolder);
            Application.persistentDataPath = delayedFolder;
            ConfigHandler pendingMode = NewSettings();
            pendingMode.LoadConfig();
            Check(pendingMode.config.launchMode == ConfigHandler.VrMode &&
                pendingMode.config.autoLaunchMode,
                "undetected Android does not default to phone prematurely");

            JNIStorage.activity = new AndroidJavaObject();
            AndroidJavaObject.VrTrackingFeature = false;
            AndroidJavaObject.OculusVrFeature = false;
            Check(FoxCraftDevice.IsAndroidPhone && !FoxCraftDevice.IsDetectionPending,
                "generic Android switches to phone once PackageManager responds");
            updateConfig.Invoke(pendingMode, null);
            Check(pendingMode.config.launchMode == ConfigHandler.PhoneMode &&
                !pendingMode.config.autoLaunchMode,
                "initial auto-selected launch mode resolves to phone");

            ConfigHandler afterDetection = NewSettings();
            afterDetection.LoadConfig();
            Check(afterDetection.config.launchMode == ConfigHandler.PhoneMode &&
                !afterDetection.config.autoLaunchMode,
                "resolved phone mode persists across a full restart");

            string manualFolder = Path.Combine(folder, "manual-device");
            Directory.CreateDirectory(manualFolder);
            Application.persistentDataPath = manualFolder;
            detectorCache.SetValue(null, null);
            JNIStorage.activity = null;
            ConfigHandler manual = NewSettings();
            manual.LoadConfig();
            manual.SetLaunchMode(ConfigHandler.VrMode);
            JNIStorage.activity = new AndroidJavaObject();
            updateConfig.Invoke(manual, null);
            Check(manual.config.launchMode == ConfigHandler.VrMode &&
                !manual.config.autoLaunchMode,
                "user-selected VR mode is never overwritten by late phone detection");

            // The same conservative fallback is used by both the camera and
            // touchscreen when Android never publishes its hardware features.
            detectorCache.SetValue(null, null);
            JNIStorage.activity = null;
            Check(FoxCraftDevice.IsDetectionPending,
                "missing Activity keeps device classification unresolved");
            FoxCraftDevice.CompleteDetectionWithPhoneFallback();
            Check(FoxCraftDevice.IsAndroidPhone && !FoxCraftDevice.IsDetectionPending,
                "timeout fallback enables the flat camera and phone input together");
            detectorCache.SetValue(null, null);
            Application.persistentDataPath = folder;

            Console.WriteLine("FoxCraft configuration/login smoke: " +
                checks + " checks passed.");
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            return 1;
        }
        finally
        {
            Directory.Delete(folder, recursive: true);
        }
    }
}
