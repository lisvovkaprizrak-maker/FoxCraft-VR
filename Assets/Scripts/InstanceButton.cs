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

    public void LaunchCurrentInstance()
    {
        if (Application.platform != RuntimePlatform.Android || launchInProgress)
            return;

        PojlibInstance instance = JNIStorage.GetInstance(currInstName);
        if (instance == null)
        {
            Debug.Log("Instance is null!");
            CreateDefaultInstance(currInstName);
            return;
        }

        launchInProgress = true;
        StartCoroutine(LaunchAfterKeyboardInstall(instance));
    }

    private IEnumerator LaunchAfterKeyboardInstall(PojlibInstance instance)
    {
        // Install the matching in-game keyboard before Pojlib creates the
        // Minecraft JVM. Only the Fabric 1.21.5 profile can use this JAR.
        if (String.Equals(instance.versionName, "1.21.5", StringComparison.Ordinal))
        {
            bool keyboardInstalled = false;
            yield return FoxCraftInGameKeyboardInstaller.EnsureInstalled(
                instance.raw, result => keyboardInstalled = result);
            if (!keyboardInstalled)
                Debug.LogWarning("[FoxCraft] Minecraft will launch without the optional " +
                    "in-game keyboard. Check the staged Fabric JAR and instance version.");
        }

        uiHandler.PlaySetter();
        ProgressBarManager.started = true;

        new Thread(() =>
        {
            AndroidJNI.AttachCurrentThread();
            try
            {
                if (!JNIStorage.apiClass.CallStatic<bool>("prelaunch",
                    JNIStorage.activity, JNIStorage.instancesObj, instance.raw))
                {
                    Debug.LogWarning("[FoxCraft] Pojlib prelaunch checks failed.");
                    launchInProgress = false;
                }
                else
                {
                    JNIStorage.apiClass.CallStatic("launchInstance",
                        JNIStorage.activity, JNIStorage.accountObj, instance.raw);
                }
            }
            catch (Exception ex)
            {
                Debug.LogError("[FoxCraft] Minecraft launch failed: " + ex.GetType().Name);
                launchInProgress = false;
            }
            finally
            {
                AndroidJNI.DetachCurrentThread();
            }
        }).Start();
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