using System;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using TMPro;
using UnityEngine;
using UnityEngine.CrashReportHandler;
using UnityEngine.XR.Management;

public class JNIStorage : MonoBehaviour
{
    public static AndroidJavaClass apiClass;
    public static AndroidJavaObject accountObj;
    public static AndroidJavaObject activity;
    public static AndroidJavaObject instancesObj;
    public APIHandler apiHandler;
    public static JNIStorage instance;
    public List<string> supportedVersions;
    public UIHandler uiHandler;
    public TMP_Dropdown instancesDropdown;
    public ConfigHandler configHandler;
    public GameObject instancePrefab;
    public GameObject instanceArray;
    
    public bool connected;
    // Set after a fatal Pojlib/JNI startup error so LoginHandler can exit
    // rather than waiting forever for an Activity that never initialized.
    public bool InitializationFailed { get; private set; }

    static void CloseXR()
    {
        // A phone may have no initialized OpenXR loader.
        var manager = XRGeneralSettings.Instance != null ? XRGeneralSettings.Instance.Manager : null;
        if (manager == null || manager.activeLoader == null)
            return;
        manager.activeLoader.Stop();
        manager.activeLoader.Deinitialize();
    }
    
    [RuntimeInitializeOnLoadMethod]
    static void RunOnStart()
    {
        Application.unloading += CloseXR;
    }

    private void Start()
    {
        instance = this;
        InitializationFailed = false;
        if (Application.platform != RuntimePlatform.Android)
            return;

        CrashReportHandler.enableCaptureExceptions = false;

        // QuestCraft's VR microphone should never be opened in the flat phone
        // launcher. A Quest also needs an already-granted Android permission.
        if (FoxCraftDevice.IsVrHeadset &&
            UnityEngine.Android.Permission.HasUserAuthorizedPermission(
                UnityEngine.Android.Permission.Microphone) &&
            Microphone.devices.Length > 0)
        {
            Microphone.Start(Microphone.devices[0], true, 1, 44100);
        }

        try
        {
            apiClass = new AndroidJavaClass("pojlib.API");
            instancesObj = apiClass.CallStatic<AndroidJavaObject>("loadAll");
            if (instancesObj == null)
                throw new InvalidOperationException("Pojlib returned no installed Minecraft instances");

            if (configHandler == null || uiHandler == null)
                throw new InvalidOperationException("QuestCraft launcher scene references are missing");

            configHandler.LoadConfig();
            UpdateInstances();
        }
        catch (Exception exception)
        {
            InitializationFailed = true;
            Debug.LogError("[FoxCraft] Launcher initialization failed: " +
                exception.GetType().Name);
            if (uiHandler != null)
                uiHandler.SetAndShowError("Не удалось инициализировать Pojlib/лаунчер. " +
                    "Проверьте установку FoxCraft и журналы приложения.");
            return;
        }

        // OpenXRFeatureSystemInfo is meaningful only on a headset. Querying
        // it on a phone can fail before the flat Unity camera is initialized.
        if (FoxCraftDevice.IsVrHeadset)
        {
            try
            {
                string headsetModel = OpenXRFeatureSystemInfo.GetHeadsetName();
                if (!string.IsNullOrEmpty(headsetModel))
                    apiClass.SetStatic("model", headsetModel);
            }
            catch (Exception exception)
            {
                Debug.LogWarning("[FoxCraft] OpenXR headset name unavailable: " +
                    exception.GetType().Name);
            }
        }
        CheckConnection();
    }

    private void FillSupportedVersions(string[] supportedVersionsArray)
    {
        if (supportedVersions == null)
            supportedVersions = new List<string>();
        supportedVersions.Clear();
        if (supportedVersionsArray != null)
        {
            foreach (string version in supportedVersionsArray)
                if (!string.IsNullOrWhiteSpace(version) && !supportedVersions.Contains(version))
                    supportedVersions.Add(version);
        }

        if (instancesObj == null)
            return;

        AndroidJavaObject[] installed = instancesObj.Call<AndroidJavaObject[]>("toArray");
        if (installed == null)
            return;
        foreach (AndroidJavaObject installedInstance in installed)
        {
            if (installedInstance == null)
                continue;
            string name = installedInstance.Get<string>("instanceName");
            if (!string.IsNullOrWhiteSpace(name) && !supportedVersions.Contains(name))
                supportedVersions.Add(name);
        }
    }

    public static PojlibInstance GetInstance(string name)
    {
        if (instancesObj == null || string.IsNullOrWhiteSpace(name))
            return null;

        AndroidJavaObject[] instances = instancesObj.Call<AndroidJavaObject[]>("toArray");
        if (instances == null)
            return null;
        foreach (AndroidJavaObject entry in instances)
        {
            if (entry == null)
                continue;
            PojlibInstance profile = PojlibInstance.Parse(entry);
            if (profile != null && string.Equals(profile.instanceName, name,
                StringComparison.Ordinal))
                return profile;
        }
        return null;
    }

    public void UpdateInstances()
    {
        if (Application.platform != RuntimePlatform.Android ||
            apiClass == null || instancesObj == null || uiHandler == null)
            return;

        using (AndroidJavaClass unityPlayer =
            new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
        {
            activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
        }
        if (activity == null)
        {
            uiHandler.SetAndShowError("Android Activity недоступна. Перезапустите FoxCraft.");
            return;
        }

        string[] available = apiClass.CallStatic<string[]>("getQCSupportedVersions", activity);
        FillSupportedVersions(available);
        uiHandler.ClearDropdowns();
        uiHandler.UpdateDropdowns(true, supportedVersions);
        // TMP must receive its options before restoring the saved profile index.
        if (configHandler != null)
            configHandler.RestoreLastSelectedInstance();
    }
    
    public void CheckConnection()
    {
        if (apiClass == null || activity == null)
            return;
        try
        {
            connected = apiClass.CallStatic<bool>("hasConnection", activity);
        }
        catch (Exception exception)
        {
            connected = false;
            Debug.LogWarning("[FoxCraft] Pojlib connectivity probe unavailable: " +
                exception.GetType().Name);
        }
        if (!connected)
        {
            // The bundled QuestCraft Pojlib connectivity check recognizes Wi-Fi
            // only. A phone using cellular data is not necessarily offline.
            if (FoxCraftDevice.IsAndroidPhone)
                uiHandler.SetAndShowError("FoxCraft: проверка сети Pojlib пока поддерживает Wi-Fi. " +
                    "Для первоначального входа подключитесь к Wi-Fi; поддержка мобильной сети будет добавлена.");
            else
                uiHandler.SetAndShowError("Unable to connect to Microsoft servers, are you offline? Some features may not work properly!");
        }
    }

    public async void FixFilePermissions()
    {
        bool result = apiClass.CallStatic<bool>("fixDataPermissions");
        if (!result)
        {
            uiHandler.SetAndShowError("Fixing data permissions failed. Please report this.");
        }
        else
        {
            uiHandler.SetAndShowError("Fixing data permissions succeeded!");
        }
    }
}
