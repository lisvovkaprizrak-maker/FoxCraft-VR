using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using UnityEngine;
using UnityEngine.XR;
using UnityEngine.XR.Management;

public class InstanceButton : MonoBehaviour
{
    public static string currInstName;
    private bool hasDefaulted;
    public ConfigHandler configHandler;
    public UIHandler uiHandler;
    public CanvasGroup ScreenFade;
    bool gameReady = false;
    private bool launchInProgress;
    private const float PrelaunchTimeoutSeconds = 150f;
    // A timed-out Java worker cannot be safely killed. Keep blocking duplicate
    // prelaunch calls until that worker actually terminates.
    private PrelaunchState pendingPrelaunch;

    public void Update()
    {
        // The QuestCraft gameReady flag and OpenXR transition belong only to
        // VR Minecraft. A future phone 2D engine has its own lifecycle.
        if (Application.platform != RuntimePlatform.Android ||
            !launchInProgress ||
            FoxCraftDevice.IsAndroidPhone ||
            (configHandler != null && configHandler.IsPhoneModeSelected) ||
            JNIStorage.instance == null || JNIStorage.apiClass == null ||
            JNIStorage.instance.instancesDropdown == null)
            return;

        var dropdown = JNIStorage.instance.instancesDropdown;
        if (dropdown.options == null || dropdown.options.Count == 0 ||
            dropdown.value < 0 || dropdown.value >= dropdown.options.Count)
            return;

        currInstName = dropdown.options[dropdown.value].text;

        if (JNIStorage.apiClass.GetStatic<bool>("gameReady") && !gameReady) {
            gameReady = true;
            Debug.Log("[FoxCraft] Pojlib reports Minecraft gameReady.");
            async Task FinishAnim()
            {
                await Task.Delay(200);
                var manager = XRGeneralSettings.Instance != null
                    ? XRGeneralSettings.Instance.Manager : null;
                if (manager != null)
                    manager.DeinitializeLoader();
            }

            LeanTween.value(ScreenFade.gameObject,0, 1, 1).setOnUpdate(alpha => ScreenFade.alpha = alpha).setOnComplete(() => FinishAnim());
        }
    }

    public void SelectInstance()
    {
        if (Application.platform != RuntimePlatform.Android) return;
        if (JNIStorage.instance == null || JNIStorage.instance.instancesDropdown == null ||
            JNIStorage.instance.instancesDropdown.options == null ||
            JNIStorage.instance.instancesDropdown.options.Count == 0)
            return;

        var dropdown = JNIStorage.instance.instancesDropdown;
        int index = dropdown.value;
        if (index < 0 || index >= dropdown.options.Count)
            return;
        currInstName = dropdown.options[index].text;
        if (configHandler != null)
            configHandler.SetLastSelectedInstance(index);
    }

    private static void CreateDefaultInstance(string name)
    {
        if (string.IsNullOrWhiteSpace(name) || JNIStorage.apiClass == null ||
            JNIStorage.activity == null || JNIStorage.instancesObj == null)
            return;

        JNIStorage.apiClass.CallStatic<AndroidJavaObject>("createNewInstance",
            JNIStorage.activity, JNIStorage.instancesObj, name, true, name,
            "Fabric", null);
        JNIStorage.instance.uiHandler.SetAndShowError(name + " is now installing.");
        JNIStorage.instance.UpdateInstances();
    }

    private sealed class PrelaunchState
    {
        // Background Pojlib work signals completion to the Unity coroutine.
        public volatile bool completed;
        public string error;
    }

    public void LaunchCurrentInstance()
    {
        if (Application.platform != RuntimePlatform.Android || launchInProgress)
            return;
        if (pendingPrelaunch != null)
        {
            if (!pendingPrelaunch.completed)
            {
                if (uiHandler != null)
                    uiHandler.SetAndShowError("Предыдущая подготовка Minecraft " +
                        "ещё выполняется. Для безопасного повтора перезапустите FoxCraft.");
                return;
            }
            pendingPrelaunch = null;
        }

        if (JNIStorage.accountObj == null)
        {
            if (uiHandler != null)
                uiHandler.SetAndShowError("Сначала войдите в аккаунт Minecraft.");
            return;
        }

        // Phone and VR each have their own renderer. Never pass a phone
        // profile to the bundled Vivecraft/OpenXR Pojlib game launcher.
        if (configHandler != null && configHandler.IsPhoneModeSelected)
        {
            if (!FoxCraftPhoneRuntime.TryLaunch(configHandler.SelectedPhoneVersion,
                out string phoneError))
                uiHandler.SetAndShowError(phoneError);
            return;
        }

        if (FoxCraftDevice.IsAndroidPhone)
        {
            // Never initialize Vivecraft/OpenXR rendering on a phone lacking
            // a headset. The selection is still allowed for future devices.
            uiHandler.SetAndShowError("Для VR-режима нужен совместимый VR-шлем. " +
                "На телефоне выберите режим ТЕЛЕФОН.");
            return;
        }

        // Read the dropdown at the moment of pressing Play. The old static
        // currInstName may still refer to a previous profile after UI refresh.
        if (JNIStorage.instance == null ||
            JNIStorage.instance.instancesDropdown == null ||
            JNIStorage.instance.instancesDropdown.options == null)
        {
            uiHandler.SetAndShowError("Список Minecraft-профилей ещё не загружен.");
            return;
        }
        var selectedDropdown = JNIStorage.instance.instancesDropdown;
        int selectedIndex = selectedDropdown.value;
        if (selectedIndex < 0 ||
            selectedIndex >= selectedDropdown.options.Count ||
            selectedDropdown.options[selectedIndex] == null)
        {
            uiHandler.SetAndShowError("Сначала выберите сборку Minecraft.");
            return;
        }
        currInstName = selectedDropdown.options[selectedIndex].text;
        if (string.IsNullOrWhiteSpace(currInstName))
        {
            uiHandler.SetAndShowError("Название сборки Minecraft пустое.");
            return;
        }

        PojlibInstance selected = JNIStorage.GetInstance(currInstName);
        if (selected == null)
        {
            Debug.Log("Instance is null!");
            CreateDefaultInstance(currInstName);
            return;
        }

        launchInProgress = true;
        StartCoroutine(PrepareAndLaunch(selected));
    }

    private IEnumerator PrepareAndLaunch(PojlibInstance selected)
    {
        uiHandler.PlaySetter();
        ProgressBarManager.started = true;

        // The BUNDLED Pojlib-release.aar exposes boolean API.prelaunch(...)
        // (confirmed with javap in GitHub Actions). Upstream Pojlib source can
        // differ, so the JNI return signature must match the shipped AAR.
        // Prelaunch updates installed mods; run it before staging FoxCraft.
        PrelaunchState state = new PrelaunchState();
        pendingPrelaunch = state;
        Thread worker = new Thread(() =>
        {
            bool attached = false;
            try
            {
                attached = AndroidJNI.AttachCurrentThread() >= 0;
                if (!attached)
                    throw new InvalidOperationException("Failed to attach to Android JVM");
                if (!JNIStorage.apiClass.CallStatic<bool>("prelaunch",
                    JNIStorage.activity, JNIStorage.instancesObj, selected.raw))
                    state.error = "Pojlib prelaunch checks failed";
            }
            catch (Exception ex)
            {
                state.error = "Minecraft prelaunch failed: " + ex.GetType().Name;
            }
            finally
            {
                if (attached) AndroidJNI.DetachCurrentThread();
                state.completed = true;
            }
        });
        worker.IsBackground = true;
        worker.Start();

        float deadline = Time.realtimeSinceStartup + PrelaunchTimeoutSeconds;
        while (!state.completed && Time.realtimeSinceStartup < deadline)
            yield return null;
        if (!state.completed)
        {
            // The Java worker may still be changing instance files. Unlock
            // the UI but prevent another launch until it finishes or the app
            // restarts. Thread.Abort would risk corrupting the Minecraft JRE.
            launchInProgress = false;
            ProgressBarManager.started = false;
            if (uiHandler != null)
            {
                uiHandler.KillSetter();
                uiHandler.SetAndShowError("Подготовка Minecraft превысила " +
                    "150 секунд. Перезапустите FoxCraft перед повторным запуском.");
            }
            yield break;
        }
        pendingPrelaunch = null;
        if (state.error != null)
        {
            launchInProgress = false;
            ProgressBarManager.started = false;
            if (uiHandler != null)
            {
                uiHandler.KillSetter();
                uiHandler.SetAndShowError(state.error);
            }
            else Debug.LogError("[FoxCraft] " + state.error);
            yield break;
        }

        Debug.Log("[FoxCraft] Pojlib prelaunch completed successfully.");

        // Only Fabric 1.21.5 receives the separate Minecraft keyboard mod.
        // This must happen after Pojlib's mod sync, before launchInstance.
        if (String.Equals(selected.versionName, "1.21.5", StringComparison.Ordinal) &&
            FoxCraftInGameKeyboardInstaller.UsesFabricLoader(selected.raw))
        {
            bool keyboardInstalled = false;
            yield return FoxCraftInGameKeyboardInstaller.EnsureInstalled(
                selected.raw, installed => keyboardInstalled = installed);
            if (!keyboardInstalled)
                Debug.LogWarning("[FoxCraft] Keyboard JAR is not installed. " +
                    "Minecraft can still start without this optional feature.");
        }
        else
        {
            Debug.Log("[FoxCraft] Skipped in-game keyboard: this profile is not Fabric 1.21.5.");
        }

        Debug.Log("[FoxCraft] Starting Minecraft through Pojlib.");

        // Do JNI work off Unity's render thread, but report its result on the
        // Unity coroutine. SynchronizationContext.Current can be null after
        // some Android lifecycle transitions; relying on Post() left Play
        // permanently disabled if launchInstance threw on such devices.
        PrelaunchState launchState = new PrelaunchState();
        Thread launchWorker = new Thread(() =>
        {
            bool attached = false;
            try
            {
                attached = AndroidJNI.AttachCurrentThread() >= 0;
                if (!attached)
                    throw new InvalidOperationException("Failed to attach to Android JVM");
                JNIStorage.apiClass.CallStatic("launchInstance",
                    JNIStorage.activity, JNIStorage.accountObj, selected.raw);
            }
            catch (Exception ex)
            {
                launchState.error = "Minecraft launch failed: " + ex.GetType().Name;
            }
            finally
            {
                if (attached)
                    AndroidJNI.DetachCurrentThread();
                launchState.completed = true;
            }
        });
        launchWorker.IsBackground = true;
        launchWorker.Start();

        // Only touch UI objects on the Unity main thread; do not do so from
        // Java workers. On a successful handoff gameReady handles the fade.
        while (!launchState.completed)
            yield return null;
        if (launchState.error != null)
        {
            launchInProgress = false;
            ProgressBarManager.started = false;
            if (uiHandler != null)
            {
                uiHandler.KillSetter();
                uiHandler.SetAndShowError(launchState.error);
            }
            else
            {
                Debug.LogError("[FoxCraft] " + launchState.error);
            }
        }
    }

    public void KillInstance()
    {
        if (JNIStorage.apiClass == null || JNIStorage.activity == null)
            return;
        JNIStorage.apiClass.CallStatic("restartLauncher", JNIStorage.activity);
    }

    public void MirrorNativesForInstance()
    {
        PojlibInstance selected = JNIStorage.GetInstance(currInstName);
        if (selected == null || JNIStorage.apiClass == null ||
            JNIStorage.activity == null || JNIStorage.instancesObj == null)
        {
            if (uiHandler != null)
                uiHandler.SetAndShowError("Сначала установите и выберите сборку Minecraft.");
            return;
        }
        JNIStorage.apiClass.CallStatic("mirrorNativesInFolder",
            JNIStorage.activity, JNIStorage.instancesObj, selected.raw,
            Path.Combine(Application.persistentDataPath, "natives"));
    }

    public void ImportSaves()
    {
        PojlibInstance selected = JNIStorage.GetInstance(currInstName);
        if (selected == null || JNIStorage.apiClass == null)
        {
            if (uiHandler != null)
                uiHandler.SetAndShowError("Сначала установите и выберите сборку Minecraft.");
            return;
        }
        JNIStorage.apiClass.CallStatic("unzipSavesFromFolder",
            selected.raw, Path.Combine(Application.persistentDataPath, "import-saves"));
    }
}