using System;
using System.Collections.Generic;
using System.IO;
using Newtonsoft.Json;
using TMPro;
using UnityEngine;

public class ConfigHandler : MonoBehaviour
{
    public TMP_Dropdown accountsDropdown;
    public WindowHandler windowHandler;
    public DevHandler devHandler;
    public Config config;
    public static string configPath;

    public const string VrMode = "vr";
    public const string PhoneMode = "phone";
    public const int MaxCachedPhoneVersions = 512;

    public bool IsLoaded => config != null;

    private void Update()
    {
        // On the very first Android boot the Quest/Pico model may be a codename
        // and PackageManager may not yet be available. Resolve the default
        // launch mode only after hardware classification; never override a mode
        // explicitly chosen by the player.
        if (config == null || !config.autoLaunchMode ||
            FoxCraftDevice.IsDetectionPending)
            return;

        config.launchMode = InitialModeForDevice();
        config.autoLaunchMode = false;
        SaveConfig();
    }

    // Keep a valid previous version on disk so an interrupted save cannot
    // destroy the user's Microsoft account list, preferences and VR profiles.
    // This does NOT store account passwords or access tokens.
    public void SaveConfig()
    {
        if (config == null || string.IsNullOrEmpty(configPath))
            return;

        string temporary = configPath + ".writing";
        string previous = configPath + ".previous";
        string json = JsonConvert.SerializeObject(config, Formatting.Indented);
        File.WriteAllText(temporary, json);
        try
        {
            if (!File.Exists(configPath))
            {
                File.Move(temporary, configPath);
            }
            else
            {
                try
                {
                    File.Replace(temporary, configPath, previous);
                }
                catch (PlatformNotSupportedException)
                {
                    // Some Android/Unity file-system implementations do not
                    // support File.Replace. Keep a backup before the fallback.
                    File.Copy(configPath, previous, true);
                    File.Delete(configPath);
                    File.Move(temporary, configPath);
                }
            }
        }
        finally
        {
            if (File.Exists(temporary))
                File.Delete(temporary);
        }
    }

    // Restore after JNIStorage has populated the version drop-down. Otherwise
    // TMP clamps an empty list to index 0 and loses the saved selection.
    public void RestoreLastSelectedInstance()
    {
        if (config == null || JNIStorage.instance == null ||
            JNIStorage.instance.instancesDropdown == null)
            return;
        TMP_Dropdown dropdown = JNIStorage.instance.instancesDropdown;
        if (dropdown.options == null || dropdown.options.Count == 0)
            return;

        int selected = Mathf.Clamp(config.lastSelectedInstance, 0, dropdown.options.Count - 1);
        if (!string.IsNullOrWhiteSpace(config.lastSelectedInstanceName))
        {
            // The QuestCraft/Pojlib list can be reordered when Minecraft
            // versions are added. Restore the actual game profile, not an old
            // list position that may point to a different Minecraft version.
            int matching = dropdown.options.FindIndex(option =>
                option != null && option.text == config.lastSelectedInstanceName);
            selected = matching >= 0 ? matching : 0;
        }
        UIHandler.selectedInstance = selected;
        dropdown.SetValueWithoutNotify(selected);
        string selectedName = dropdown.options[selected] != null
            ? dropdown.options[selected].text : "";
        bool updated = config.lastSelectedInstance != selected ||
            config.lastSelectedInstanceName != selectedName;
        config.lastSelectedInstance = selected;
        config.lastSelectedInstanceName = selectedName;
        if (updated)
            SaveConfig();
        if (JNIStorage.instance.uiHandler != null)
            JNIStorage.instance.uiHandler.UpdateDropdowns(false, null);
    }

    public bool IsPhoneModeSelected => config != null && config.launchMode == PhoneMode;

    // The existing launcher.conf is shared by both modes. Missing launchMode values
    // are migrated without resetting accounts, Minecraft instances or RAM settings.
    public void SetLaunchMode(string mode)
    {
        if (mode != VrMode && mode != PhoneMode)
            return;

        if (config == null)
            return;

        config.launchMode = mode;
        config.autoLaunchMode = false;
        SaveConfig();
    }

    public string SelectedPhoneVersion => config != null && !string.IsNullOrWhiteSpace(config.phoneVersion)
        ? config.phoneVersion : "1.21.5";

    // Cached, verified version IDs are available when the phone is offline.
    // They never overwrite the separate VR instance selector.
    public List<string> GetCachedPhoneVersions()
    {
        return config != null && config.phoneVersionCache != null
            ? new List<string>(config.phoneVersionCache)
            : new List<string>();
    }

    private static List<string> NormalizePhoneVersions(
        IList<string> versions, string fallback)
    {
        List<string> safe = new List<string>();
        if (versions != null)
        {
            foreach (string version in versions)
            {
                if (safe.Count >= MaxCachedPhoneVersions)
                    break;
                if (IsSafeVersionId(version) && !safe.Contains(version))
                    safe.Add(version);
            }
        }
        if (safe.Count == 0)
            safe.Add(IsSafeVersionId(fallback) ? fallback : "1.21.5");
        return safe;
    }

    public void SetCachedPhoneVersions(List<string> versions)
    {
        if (config == null || versions == null || versions.Count == 0)
            return;
        List<string> safe = NormalizePhoneVersions(versions, config.phoneVersion);
        config.phoneVersionCache = safe;
        SaveConfig();
    }

    public void SetPhoneVersion(string version)
    {
        // Version IDs come from the official Mojang version manifest.
        if (config == null || !IsSafeVersionId(version))
            return;
        config.phoneVersion = version;
        SaveConfig();
    }

    private static bool IsSafeVersionId(string version)
    {
        if (string.IsNullOrWhiteSpace(version) || version.Length > 48)
            return false;
        foreach (char c in version)
        {
            bool asciiLetter = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            bool asciiDigit = c >= '0' && c <= '9';
            if (!asciiLetter && !asciiDigit && c != '-' && c != '_' && c != '.')
                return false;
        }
        return true;
    }

    private static string InitialModeForDevice()
    {
        return FoxCraftDevice.IsDetectionPending ||
            FoxCraftDevice.IsVrHeadset ? VrMode : PhoneMode;
    }

    private static Config CreateDefaultConfig()
    {
        return new Config
        {
            acceptedLegal = false,
            setDevMods = false,
            setCustomRAM = false,
            customRAMValue = "2048",
            lastSelectedInstance = 0,
            lastSelectedInstanceName = "",
            lastSelectedAccount = 0,
            launchMode = InitialModeForDevice(),
            autoLaunchMode = true,
            phoneVersion = "1.21.5",
            phoneVersionCache = new List<string> { "1.21.5" },
            accounts = new List<Accounts>()
        };
    }

    public void LoadConfig()
    {
        configPath = Path.Combine(Application.persistentDataPath, "launcher.conf");
        bool createNew = !File.Exists(configPath);

        if (!createNew)
        {
            try
            {
                config = JsonConvert.DeserializeObject<Config>(File.ReadAllText(configPath));
            }
            catch (JsonException ex)
            {
                Debug.LogWarning("[FoxCraft] Invalid launcher.conf JSON: " + ex.GetType().Name);
                config = null;
            }

            if (config == null)
            {
                // Never overwrite a damaged file containing account/profile data.
                // Preserve it before creating a clean first-run configuration.
                string backup = configPath + ".corrupt-" +
                    DateTime.UtcNow.ToString("yyyyMMddHHmmssfff");
                File.Move(configPath, backup);
                Debug.LogWarning("[FoxCraft] Original launcher settings preserved as " +
                    Path.GetFileName(backup));
                createNew = true;
            }
        }

        if (createNew)
        {
            // Recover account and game profile settings from a completed older
            // write if the main file was corrupted or lost during replacement.
            string previous = configPath + ".previous";
            if (File.Exists(previous))
            {
                try
                {
                    config = JsonConvert.DeserializeObject<Config>(
                        File.ReadAllText(previous));
                    if (config != null)
                    {
                        createNew = false;
                        SaveConfig();
                        Debug.LogWarning("[FoxCraft] Restored previous launcher settings.");
                    }
                }
                catch (JsonException)
                {
                    Debug.LogWarning("[FoxCraft] Previous launcher settings are also invalid.");
                }
            }
        }

        if (createNew)
        {
            config = CreateDefaultConfig();
            SaveConfig();
        }

        bool migrated = false;
        if (config.accounts == null)
        {
            config.accounts = new List<Accounts>();
            migrated = true;
        }
        if (config.launchMode != VrMode && config.launchMode != PhoneMode)
        {
            config.launchMode = InitialModeForDevice();
            config.autoLaunchMode = true;
            migrated = true;
        }
        if (!IsSafeVersionId(config.phoneVersion))
        {
            config.phoneVersion = "1.21.5";
            migrated = true;
        }
        // launcher.conf is editable and may come from an older FoxCraft build.
        // Sanitize version IDs read from disk too, not just Mojang fetch results.
        List<string> sanitized = NormalizePhoneVersions(
            config.phoneVersionCache, config.phoneVersion);
        bool changed = config.phoneVersionCache == null ||
            config.phoneVersionCache.Count != sanitized.Count;
        if (!changed)
        {
            for (int i = 0; i < sanitized.Count; i++)
            {
                if (config.phoneVersionCache[i] != sanitized[i])
                {
                    changed = true;
                    break;
                }
            }
        }
        if (changed)
        {
            config.phoneVersionCache = sanitized;
            migrated = true;
        }
        if (string.IsNullOrWhiteSpace(config.customRAMValue))
        {
            config.customRAMValue = "2048";
            migrated = true;
        }
        if (migrated)
            SaveConfig();

        if (!config.acceptedLegal && windowHandler != null)
            windowHandler.LegalSetter();
        if (config.setDevMods && devHandler != null)
            devHandler._devToggle.isOn = true;
        if (config.setCustomRAM && devHandler != null)
        {
            devHandler._ramSetterToggle.isOn = true;
            devHandler._ramSetterField.text = config.customRAMValue;
            if (JNIStorage.apiClass != null)
                JNIStorage.apiClass.SetStatic("memoryValue", config.customRAMValue);
        }
        // JNIStorage.UpdateInstances restores the saved profile after its
        // available versions and installed instances are populated.
    }

    public void SetLastSelectedInstance(int instance)
    {
        if (config == null)
            return;
        config.lastSelectedInstance = instance;
        if (JNIStorage.instance != null && JNIStorage.instance.instancesDropdown != null)
        {
            TMP_Dropdown dropdown = JNIStorage.instance.instancesDropdown;
            if (dropdown.options != null && instance >= 0 &&
                instance < dropdown.options.Count)
                config.lastSelectedInstanceName = dropdown.options[instance].text;
        }
        SaveConfig();
    }

    public void SetLastSelectedAccount(int account)
    {
        if (config == null)
            return;
        config.lastSelectedAccount = account;
        SaveConfig();
    }
    
    public void SetMemoryValue()
    {
        if (config == null)
            return;
        string ramValue = devHandler._ramSetterField.text;
        
        if (ramValue != null)
        {
            config.customRAMValue = ramValue;
            SaveConfig();
            JNIStorage.apiClass.SetStatic("memoryValue", ramValue);
        }
    }

    public void AgreeToLegal()
    {
        if (config == null)
            return;
        config.acceptedLegal = true;
        SaveConfig();
    }

    public class Config
    {
        public bool acceptedLegal;
        public bool setDevMods;
        public bool setCustomRAM;
        public string customRAMValue;
        public int lastSelectedInstance;
        public string lastSelectedInstanceName;
        public int lastSelectedAccount;
        public string launchMode;
        public bool autoLaunchMode;
        public string phoneVersion;
        public List<string> phoneVersionCache;
        public List<Accounts> accounts;
    }
    
    public class Accounts
    {
        public string username;
        public string uuid;
    }
}