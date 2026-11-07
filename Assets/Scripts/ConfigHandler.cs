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
        File.WriteAllText(configPath, JsonConvert.SerializeObject(config, Formatting.Indented));
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

    public void SetCachedPhoneVersions(List<string> versions)
    {
        if (config == null || versions == null || versions.Count == 0)
            return;
        List<string> safe = new List<string>();
        foreach (string version in versions)
        {
            if (safe.Count >= 160)
                break;
            if (IsSafeVersionId(version) && !safe.Contains(version))
                safe.Add(version);
        }
        if (safe.Count == 0)
            return;
        config.phoneVersionCache = safe;
        File.WriteAllText(configPath, JsonConvert.SerializeObject(config, Formatting.Indented));
    }

    public void SetPhoneVersion(string version)
    {
        // Version IDs come from the official Mojang version manifest.
        if (config == null || !IsSafeVersionId(version))
            return;
        config.phoneVersion = version;
        File.WriteAllText(configPath, JsonConvert.SerializeObject(config, Formatting.Indented));
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
        return FoxCraftDevice.IsVrHeadset ? VrMode : PhoneMode;
    }

    public void LoadConfig()
    {
        configPath = Application.persistentDataPath + "/launcher.conf";
        
        if (File.Exists(configPath))
        { 
            string configFile = File.ReadAllText(configPath);
            config = JsonConvert.DeserializeObject<Config>(configFile);
            if (config == null)
                throw new InvalidDataException("launcher.conf is not valid FoxCraft configuration JSON");
            if (config.accounts == null)
                config.accounts = new List<Accounts>();
            if (config.launchMode != VrMode && config.launchMode != PhoneMode)
            {
                config.launchMode = InitialModeForDevice();
                File.WriteAllText(configPath, JsonConvert.SerializeObject(config, Formatting.Indented));
            }
            
            if (!config.acceptedLegal) { windowHandler.LegalSetter(); }
            if (config.setDevMods) { devHandler._devToggle.isOn = true; }
            if (config.setCustomRAM)
            {
                devHandler._ramSetterToggle.isOn = true;
                devHandler._ramSetterField.text = config.customRAMValue;
                JNIStorage.apiClass.SetStatic("memoryValue", config.customRAMValue);
            }
            
            JNIStorage.instance.instancesDropdown.value = config.lastSelectedInstance;
        }
        else
        {
            config = new Config
            {
                acceptedLegal = false,
                setDevMods = false,
                setCustomRAM = false,
                customRAMValue = "2048",
                lastSelectedInstance = 0,
                lastSelectedAccount = 0,
                launchMode = InitialModeForDevice(),
                phoneVersion = "1.21.5",
                phoneVersionCache = new List<string> { "1.21.5" },
                accounts = new List<Accounts>()
            };

            string JSON = JsonConvert.SerializeObject(config, Formatting.Indented);
            File.WriteAllText(configPath, JSON);
            string configFile = File.ReadAllText(configPath);
            config = JsonConvert.DeserializeObject<Config>(configFile);
            windowHandler.LegalSetter();
        }
    }

    public void SetLastSelectedInstance(int instance)
    {
        string configFile = File.ReadAllText(configPath);
        config = JsonConvert.DeserializeObject<Config>(configFile);
        config.lastSelectedInstance = instance;
        string JSON = JsonConvert.SerializeObject(config, Formatting.Indented);
        File.WriteAllText(configPath, JSON);
    }

    public void SetLastSelectedAccount(int account)
    {
        string configFile = File.ReadAllText(configPath);
        config = JsonConvert.DeserializeObject<Config>(configFile);
        config.lastSelectedAccount = account;
        string JSON = JsonConvert.SerializeObject(config, Formatting.Indented);
        File.WriteAllText(configPath, JSON);
    }
    
    public void SetMemoryValue()
    {
        string configFile = File.ReadAllText(configPath);
        config = JsonConvert.DeserializeObject<Config>(configFile);
        string ramValue = devHandler._ramSetterField.text;
        
        if (ramValue != null)
        {
            config.customRAMValue = ramValue;
            string JSON = JsonConvert.SerializeObject(config, Formatting.Indented);
            File.WriteAllText(configPath, JSON);
            JNIStorage.apiClass.SetStatic("memoryValue", ramValue);
        }
    }

    public void AgreeToLegal()
    {
        string configFile = File.ReadAllText(configPath);
        config = JsonConvert.DeserializeObject<Config>(configFile);
        config.acceptedLegal = true;
        string JSON = JsonConvert.SerializeObject(config, Formatting.Indented);
        File.WriteAllText(configPath, JSON);
    }

    public class Config
    {
        public bool acceptedLegal;
        public bool setDevMods;
        public bool setCustomRAM;
        public string customRAMValue;
        public int lastSelectedInstance;
        public int lastSelectedAccount;
        public string launchMode;
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