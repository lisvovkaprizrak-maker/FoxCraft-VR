using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using TMPro;
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

    public void Update()
    {
        // The QuestCraft gameReady flag and OpenXR transition belong only to
        // VR Minecraft. A future phone 2D engine has its own lifecycle.
        if (Application.platform != RuntimePlatform.Android ||
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
        TMP_Dropdown selectedDropdown = JNIStorage.instance.instancesDropdown;
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

        while (!state.completed) yield return null;
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
        SynchronizationContext mainThread = SynchronizationContext.Current;
        Thread launchWorker = new Thread(() =>
        {
            bool attached = false;
            string error = null;
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
                error = "Minecraft launch failed: " + ex.GetType().Name;
            }
            finally
            {
                if (attached) AndroidJNI.DetachCurrentThread();
            }

            if (error != null)
            {
                string message = error;
                if (mainThread != null)
                    mainThread.Post(_ =>
                    {
                        launchInProgress = false;
                        ProgressBarManager.started = false;
                        if (uiHandler != null)
                        {
                            uiHandler.KillSetter();
                            uiHandler.SetAndShowError(message);
                        }
                        else Debug.LogError("[FoxCraft] " + message);
                    }, null);
                else
                    Debug.LogError("[FoxCraft] " + message);
            }
        });
        launchWorker.IsBackground = true;
        launchWorker.Start();
    }

    public void KillInstance()
    {
        JNIStorage.apiClass.CallStatic("restartLauncher", JNIStorage.activity);
    }

    public async void MirrorNativesForInstance()
    {
        PojlibInstance instance = JNIStorage.GetInstance(currInstName);
        JNIStorage.apiClass.CallStatic("mirrorNativesInFolder", JNIStorage.activity, JNIStorage.instancesObj, instance.raw, Path.Combine(Application.persistentDataPath, "natives"));
    }

    public async void ImportSaves()
    {
        PojlibInstance instance = JNIStorage.GetInstance(currInstName);
        JNIStorage.apiClass.CallStatic("unzipSavesFromFolder", instance.raw, Path.Combine(Application.persistentDataPath, "import-saves"));
    }
}