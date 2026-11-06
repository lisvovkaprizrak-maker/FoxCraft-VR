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

    public void Update()
    {
        if (Application.platform != RuntimePlatform.Android)
            return;
        currInstName = JNIStorage.instance.instancesDropdown.options[JNIStorage.instance.instancesDropdown.value].text;

        if (JNIStorage.apiClass.GetStatic<bool>("gameReady") && !gameReady) {
            gameReady = true;
            async Task FinishAnim()
            {
                await Task.Delay(200);
                XRGeneralSettings.Instance.Manager.DeinitializeLoader();
            }

            LeanTween.value(ScreenFade.gameObject,0, 1, 1).setOnUpdate(alpha => ScreenFade.alpha = alpha).setOnComplete(() => FinishAnim());
        }
    }

    public void SelectInstance()
    {
        if (Application.platform != RuntimePlatform.Android) return;
        currInstName = JNIStorage.instance.instancesDropdown.options[JNIStorage.instance.instancesDropdown.value].text;
        configHandler.SetLastSelectedInstance(JNIStorage.instance.instancesDropdown.value);
    }

    private static void CreateDefaultInstance(string name)
    {
        JNIStorage.apiClass.CallStatic<AndroidJavaObject>("createNewInstance", JNIStorage.activity, JNIStorage.instancesObj, name, true, name, "Fabric", null);
        JNIStorage.instance.uiHandler.SetAndShowError(currInstName + " is now installing.");
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

        // API.prelaunch is VOID in QuestCraftPlusPlus/Pojlib QuestCraft-6.0.0.
        // It updates the instance's mods. Run it before staging FoxCraft so
        // updateMods cannot remove our custom JAR on this launch.
        PrelaunchState state = new PrelaunchState();
        Thread worker = new Thread(() =>
        {
            bool attached = false;
            try
            {
                attached = AndroidJNI.AttachCurrentThread() >= 0;
                if (!attached)
                    throw new InvalidOperationException("Failed to attach to Android JVM");
                JNIStorage.apiClass.CallStatic("prelaunch",
                    JNIStorage.activity, JNIStorage.instancesObj, selected.raw);
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
            if (uiHandler != null) uiHandler.SetAndShowError(state.error);
            else Debug.LogError("[FoxCraft] " + state.error);
            yield break;
        }

        // Only Fabric 1.21.5 receives the separate Minecraft keyboard mod.
        // This must happen after Pojlib's mod sync, before launchInstance.
        if (String.Equals(selected.versionName, "1.21.5", StringComparison.Ordinal))
        {
            bool keyboardInstalled = false;
            yield return FoxCraftInGameKeyboardInstaller.EnsureInstalled(
                selected.raw, installed => keyboardInstalled = installed);
            if (!keyboardInstalled)
                Debug.LogWarning("[FoxCraft] Keyboard JAR is not installed. " +
                    "Minecraft can still start without this optional feature.");
        }

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
                        if (uiHandler != null) uiHandler.SetAndShowError(message);
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
        JNIStorage.apiClass.CallStatic("mirrorNativesInFolder", JNIStorage.activity, JNIStorage.instancesObj, instance.raw, "/sdcard/Android/data/com.qcxr.qcxr/files/natives");
    }

    public async void ImportSaves()
    {
        PojlibInstance instance = JNIStorage.GetInstance(currInstName);
        JNIStorage.apiClass.CallStatic("unzipSavesFromFolder", instance.raw, "/sdcard/Android/data/com.qcxr.qcxr/files/import-saves");
    }
}