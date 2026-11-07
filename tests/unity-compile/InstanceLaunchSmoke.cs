// Runs the real InstanceButton methods against lightweight Unity/Pojlib fakes.
// This does NOT execute Minecraft or prove Android/Quest hardware support.
using System;
using System.Collections;
using System.Threading;
using UnityEngine;

public static class InstanceLaunchSmoke
{
    private static int checks;

    private static void Assert(bool ok, string message)
    {
        if (!ok)
            throw new InvalidOperationException("FoxCraft VR launch smoke: " + message);
        checks++;
    }

    public static int Main()
    {
        try
        {
            Application.platform = RuntimePlatform.Android;
            Application.persistentDataPath = "/tmp/foxcraft-unit-test";
            SystemInfo.deviceModel = "Generic Android Phone";

            var ui = new UIHandler();
            var prefs = new ConfigHandler();
            var dropdown = new Dropdown { value = 1 };
            dropdown.options.Add(new DropdownOption { text = "Vanilla 1.20.1" });
            dropdown.options.Add(new DropdownOption { text = "Fabric 1.21.5" });
            JNIStorage.instance = new JNIStorage {
                instancesDropdown = dropdown,
                uiHandler = ui
            };
            JNIStorage.apiClass = new AndroidJavaClass("pojlib.API");
            JNIStorage.activity = new AndroidJavaObject();
            JNIStorage.instancesObj = new AndroidJavaObject();
            JNIStorage.accountObj = null;
            JNIStorage.ActiveInstance = null;

            var button = new InstanceButton {
                configHandler = prefs,
                uiHandler = ui,
                ScreenFade = new CanvasGroup { gameObject = new GameObject() }
            };
            button.SelectInstance();
            Assert(InstanceButton.currInstName == "Fabric 1.21.5" &&
                prefs.lastSelectedInstance == 1,
                "selected VR profile persisted from current dropdown");

            dropdown.value = -1;
            button.SelectInstance();
            Assert(prefs.lastSelectedInstance == 1,
                "invalid dropdown index ignored without exception");
            dropdown.value = 1;

            button.LaunchCurrentInstance();
            Assert(ui.lastError != null && ui.lastError.Contains("войдите"),
                "Play rejected without verified Minecraft account");

            JNIStorage.accountObj = new AndroidJavaObject();
            prefs.IsPhoneModeSelected = true;
            button.LaunchCurrentInstance();
            Assert(ui.lastError != null && ui.lastError.Contains("2D-движок"),
                "phone Play reports unavailable 2D runtime instead of launching OpenXR");

            prefs.IsPhoneModeSelected = false;
            button.LaunchCurrentInstance();
            Assert(ui.lastError != null && ui.lastError.Contains("VR-шлем"),
                "VR Minecraft rejected on a normal Android phone");

            // Simulate a Quest after this point; the native source sees a
            // headset model and can access the existing VR Pojlib backend.
            SystemInfo.deviceModel = "Meta Quest 3";
            InstanceButton.currInstName = "old-stale-selection";
            JNIStorage.ActiveInstance = null;
            button.LaunchCurrentInstance();
            Assert(InstanceButton.currInstName == "Fabric 1.21.5",
                "Play reads current dropdown rather than a stale static instance");
            Assert(ui.lastError != null && ui.lastError.Contains("installing"),
                "missing profile reaches Pojlib installation path");

            // A fake Pojlib prelaunch returns false. Verify the coroutine
            // restores Play/Cancel controls and clears progress instead of
            // leaving the user trapped behind a loading screen.
            var installed = new PojlibInstance {
                versionName = "1.21.5", raw = new AndroidJavaObject()
            };
            var prepare = typeof(InstanceButton).GetMethod("PrepareAndLaunch",
                System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic);
            Assert(prepare != null, "prelaunch coroutine can be exercised");
            IEnumerator routine = (IEnumerator)prepare.Invoke(button,
                new object[] { installed });
            bool running = true;
            for (int i = 0; i < 250 && running; i++)
            {
                running = routine.MoveNext();
                if (running)
                    Thread.Sleep(4);
            }
            Assert(!running, "failed Pojlib prelaunch completes rather than hanging");
            Assert(ui.playCalls == 1 && ui.killCalls == 1,
                "Play UI restored after failed Pojlib prelaunch");
            Assert(!ProgressBarManager.started,
                "loading progress reset on Pojlib prelaunch failure");
            Assert(ui.lastError != null && ui.lastError.Contains("prelaunch"),
                "Pojlib prelaunch failure is visible to the user");

            // Simulate a Java prelaunch call stuck longer than 150 seconds
            // by advancing the fake Unity clock, without waiting in CI.
            AndroidJavaClass.PrelaunchDelayMs = 500;
            var slowUi = new UIHandler();
            var slowButton = new InstanceButton {
                configHandler = prefs,
                uiHandler = slowUi,
                ScreenFade = new CanvasGroup { gameObject = new GameObject() }
            };
            Time.realtimeSinceStartup = 1f;
            IEnumerator stalled = (IEnumerator)prepare.Invoke(slowButton,
                new object[] { installed });
            Assert(stalled.MoveNext(), "slow Pojlib worker starts");
            Thread.Sleep(35);
            int pendingCalls = AndroidJavaClass.PrelaunchCallCount;
            Time.realtimeSinceStartup = 152f;
            Assert(!stalled.MoveNext(), "prelaunch times out instead of infinite spinner");
            Assert(slowUi.playCalls == 1 && slowUi.killCalls == 1 &&
                !ProgressBarManager.started,
                "prelaunch timeout restores launcher UI and progress");
            Assert(slowUi.lastError != null &&
                slowUi.lastError.Contains("150 секунд"),
                "timed-out prelaunch tells the user to restart FoxCraft");
            slowButton.LaunchCurrentInstance();
            Assert(slowUi.lastError != null &&
                slowUi.lastError.Contains("ещё выполняется"),
                "new launch blocked while timed-out worker still touches files");
            Assert(AndroidJavaClass.PrelaunchCallCount == pendingCalls,
                "no duplicate Java prelaunch starts during unresolved timeout");
            AndroidJavaClass.PrelaunchDelayMs = 0;

            Console.WriteLine("FoxCraft VR launch smoke: " + checks + " checks passed.");
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine(exception);
            return 1;
        }
    }
}
