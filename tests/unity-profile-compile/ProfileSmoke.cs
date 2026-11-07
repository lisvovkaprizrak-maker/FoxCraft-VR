using System;
using System.Collections;
using System.Collections.Generic;
using System.Threading;
using TMPro;
using UnityEngine;
using UnityEngine.UI;

public static class ProfileSmoke
{
    private static int assertions;
    private static void Check(bool pass, string message)
    {
        if (!pass) throw new InvalidOperationException("Profile smoke: " + message);
        assertions++;
    }
    private static void FinishJobs()
    {
        DateTime deadline = DateTime.UtcNow.AddSeconds(5);
        while (MonoBehaviour.Pending.Count != 0 && DateTime.UtcNow < deadline)
        {
            for (int i = MonoBehaviour.Pending.Count - 1; i >= 0; i--)
            {
                IEnumerator operation = MonoBehaviour.Pending[i];
                if (!operation.MoveNext()) MonoBehaviour.Pending.RemoveAt(i);
            }
            Thread.Sleep(5);
        }
        Check(MonoBehaviour.Pending.Count == 0, "coroutine completed");
    }
    public static int Main()
    {
        try
        {
            JNIStorage.instance = new JNIStorage {
                uiHandler = new UIHandler(), configHandler = new ConfigHandler()
            };
            JNIStorage.apiClass = new AndroidJavaClass();
            JNIStorage.activity = new AndroidJavaObject();
            JNIStorage.instancesObj = new AndroidJavaObject();
            InstanceManager manager = new InstanceManager {
                instanceName = new TMP_InputField(),
                loaderDropdown = new TMP_Dropdown(),
                versionDropdown = new TMP_Dropdown(),
                defaultModsToggle = new Toggle { isOn = true },
                windowHandler = new WindowHandler()
            };
            manager.loaderDropdown.options.Add(new TMP_Dropdown.OptionData { text = "Fabric" });
            manager.versionDropdown.options.Add(new TMP_Dropdown.OptionData { text = "1.21.5" });
            manager.instanceName.text = "../unsafe";
            manager.CreateCustomInstance();
            Check(!InstanceManager.IsUpdatingProfiles && AndroidJavaClass.CreateCalls == 0,
                "unsafe profile names must never reach JNI");

            AndroidJavaObject.Profiles = new[] {
                new AndroidJavaObject { profileName = "Existing" }
            };
            manager.instanceName.text = "existing";
            manager.CreateCustomInstance();
            Check(!InstanceManager.IsUpdatingProfiles && AndroidJavaClass.CreateCalls == 0,
                "profile duplicates are case-insensitive");

            AndroidJavaObject.Profiles = Array.Empty<AndroidJavaObject>();
            manager.instanceName.text = "FoxCraft Test";
            AndroidJavaClass.WorkerDelayMs = 75;
            manager.CreateCustomInstance();
            Check(InstanceManager.IsUpdatingProfiles && InstanceManager.IsCreatingProfile,
                "creation starts asynchronous profile worker");
            manager.CreateCustomInstance();
            Thread.Sleep(20);
            Check(AndroidJavaClass.CreateCalls == 1, "double click cannot create twice");
            FinishJobs();
            Check(!InstanceManager.IsUpdatingProfiles && AndroidJavaClass.CreateCalls == 1,
                "successful create unlocks profile worker");
            Check(JNIStorage.instance.refreshCount == 1 &&
                JNIStorage.instance.configHandler.config.lastSelectedInstanceName == "FoxCraft Test",
                "new profile selected and list refreshed after success");
            Check(manager.windowHandler.Created == 1, "creator panel closes after success");

            AndroidJavaClass.CreateSucceeds = false;
            manager.instanceName.text = "Broken";
            manager.CreateCustomInstance();
            FinishJobs();
            Check(!InstanceManager.IsUpdatingProfiles &&
                JNIStorage.instance.refreshCount == 1,
                "failed install cannot report success or refresh installed profiles");
            AndroidJavaClass.CreateSucceeds = true;

            manager.RemoveInstance();
            Check(AndroidJavaClass.DeleteCalls == 0 && !InstanceManager.IsUpdatingProfiles,
                "missing selection cannot be deleted");
            JNIStorage.instance.selected = new PojlibInstance {
                instanceName = "Existing", raw = new AndroidJavaObject()
            };
            // instanceTitle is private serialized Unity field.
            typeof(InstanceManager).GetField("instanceTitle",
                System.Reflection.BindingFlags.NonPublic |
                System.Reflection.BindingFlags.Instance)
                .SetValue(manager, new TextMeshProUGUI { text = "Existing" });
            AndroidJavaClass.DeleteSucceeds = false;
            manager.RemoveInstance();
            FinishJobs();
            Check(JNIStorage.instance.refreshCount == 1 &&
                manager.windowHandler.Deleted == 0, "failed delete does not dismiss panel");
            AndroidJavaClass.DeleteSucceeds = true;
            manager.RemoveInstance();
            Check(InstanceManager.IsUpdatingProfiles && !InstanceManager.IsCreatingProfile,
                "deletion uses separate non-UI Java worker");
            FinishJobs();
            Check(JNIStorage.instance.refreshCount == 2 &&
                manager.windowHandler.Deleted == 1, "successful deletion refreshes list");
            Check(AndroidJNI.Attached == AndroidJNI.Detached,
                "all background JVM attachments released");
            Console.WriteLine("FoxCraft profile smoke passed: " + assertions + " checks");
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine(ex);
            return 1;
        }
    }
}
