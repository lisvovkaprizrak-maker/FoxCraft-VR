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

            // Corruption must be backed up, not silently overwrite the old file.
            File.WriteAllText(ConfigHandler.configPath, "not valid json");
            ConfigHandler recovered = NewSettings();
            recovered.LoadConfig();
            string[] backups = Directory.GetFiles(folder, "launcher.conf.corrupt-*");
            Check(backups.Length == 1, "invalid config backed up");
            Check(File.ReadAllText(backups[0]) == "not valid json",
                "invalid original bytes retained");
            Check(recovered.config != null && recovered.config.accounts.Count == 0,
                "recovered launcher has valid fresh defaults");

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
