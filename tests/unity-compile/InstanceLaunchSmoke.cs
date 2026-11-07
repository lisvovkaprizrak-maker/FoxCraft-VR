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

            // When Unity's Android Activity is not ready, classification must
            // stay pending instead of immediately modifying the VR rig.
            JNIStorage.activity = null;
            Assert(FoxCraftDevice.IsDetectionPending &&
                !FoxCraftDevice.IsAndroidPhone,
                "Android classification waits for UnityPlayerActivity");

            JNIStorage.activity = new AndroidJavaObject();
            Assert(!FoxCraftDevice.IsDetectionPending &&
                FoxCraftDevice.IsAndroidPhone,
                "normal phone is recognized after Android PackageManager loads");

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

            // launchInstance can throw after a successful Fabric/Vanilla
            // prelaunch. Unity must restore Play on the coroutine's main
            // thread; an Android worker may have no SynchronizationContext.
            AndroidJavaClass.PrelaunchResult = true;
            AndroidJavaClass.FailLaunchInstance = true;
            var javaLaunchUi = new UIHandler();
            var javaLaunchButton = new InstanceButton {
                configHandler = prefs,
                uiHandler = javaLaunchUi,
                ScreenFade = new CanvasGroup { gameObject = new GameObject() }
            };
            typeof(InstanceButton).GetField("launchInProgress",
                System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic)
                .SetValue(javaLaunchButton, true);
            IEnumerator launchError = (IEnumerator)prepare.Invoke(javaLaunchButton,
                new object[] {
                    new PojlibInstance {
                        versionName = "1.20.1", raw = new AndroidJavaObject()
                    }
                });
            running = true;
            for (int i = 0; i < 250 && running; i++)
            {
                running = launchError.MoveNext();
                if (running) Thread.Sleep(4);
            }
            Assert(!running, "Pojlib launch failure completes its Unity coroutine");
            Assert(javaLaunchUi.playCalls == 1 && javaLaunchUi.killCalls == 1 &&
                !ProgressBarManager.started,
                "failed Java Minecraft launch restores Play and clears progress");
            Assert(javaLaunchUi.lastError != null &&
                javaLaunchUi.lastError.Contains("Minecraft launch failed"),
                "Java launch failure is displayed instead of silently logged");
            Assert(AndroidJavaClass.LaunchInstanceCallCount == 1,
                "launchInstance is invoked exactly once after successful prelaunch");

            AndroidJavaClass.FailLaunchInstance = false;
            var successfulUi = new UIHandler();
            var successfulButton = new InstanceButton {
                configHandler = prefs, uiHandler = successfulUi,
                ScreenFade = new CanvasGroup { gameObject = new GameObject() }
            };
            typeof(InstanceButton).GetField("launchInProgress",
                System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic)
                .SetValue(successfulButton, true);
            IEnumerator success = (IEnumerator)prepare.Invoke(successfulButton,
                new object[] {
                    new PojlibInstance {
                        versionName = "1.20.1", raw = new AndroidJavaObject()
                    }
                });
            running = true;
            for (int i = 0; i < 250 && running; i++)
            {
                running = success.MoveNext();
                if (running) Thread.Sleep(4);
            }
            Assert(!running && AndroidJavaClass.LaunchInstanceCallCount == 2,
                "successful Java launch hands off exactly once");
            Assert(successfulUi.killCalls == 0 && ProgressBarManager.started,
                "successful launch leaves VR handoff active until gameReady");
            ProgressBarManager.started = false;

            // An old gameReady flag can survive from a previous VR session.
            // Starting the launcher must not fade/deinitialize OpenXR before
            // the user actually presses Play for this new session.
            AndroidJavaClass.GameReady = true;
            int previousFades = LeanTween.ValueCallCount;
            var update = typeof(InstanceButton).GetMethod("Update");
            update.Invoke(button, null);
            Assert(LeanTween.ValueCallCount == previousFades,
                "stale gameReady does not trigger OpenXR handoff without Play");

            typeof(InstanceButton).GetField("launchInProgress",
                System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic)
                .SetValue(successfulButton, true);
            var notReadyFlag = typeof(InstanceButton).GetField("sawGameNotReady",
                System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic);
            Assert((bool)notReadyFlag.GetValue(successfulButton),
                "fast Java launches capture false gameReady before JNI dispatch");
            // Simulate a NEW run whose previous Java session still had
            // gameReady=true when the worker was dispatched.
            notReadyFlag.SetValue(successfulButton, false);
            update.Invoke(successfulButton, null);
            Assert(LeanTween.ValueCallCount == previousFades,
                "stale true gameReady never fades a newly launched game");
            AndroidJavaClass.GameReady = false;
            update.Invoke(successfulButton, null);
            AndroidJavaClass.GameReady = true;
            update.Invoke(successfulButton, null);
            Assert(LeanTween.ValueCallCount == previousFades + 1,
                "gameReady must transition false-to-true after Java launch");
            update.Invoke(successfulButton, null);
            Assert(LeanTween.ValueCallCount == previousFades + 1,
                "repeated gameReady polls never queue duplicate VR transitions");
            AndroidJavaClass.GameReady = false;

            // A scene missing the optional fade CanvasGroup should still
            // complete the VR handoff, without throwing NullReferenceException
            // or attempting to animate a destroyed Unity GameObject.
            AndroidJavaClass.GameReady = true;
            int beforeCoroutines = MonoBehaviour.CoroutineStarts;
            var missingFade = new InstanceButton {
                configHandler = prefs,
                uiHandler = new UIHandler(),
                ScreenFade = null
            };
            typeof(InstanceButton).GetField("launchInProgress",
                System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic)
                .SetValue(missingFade, true);
            typeof(InstanceButton).GetField("javaLaunchDispatched",
                System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic)
                .SetValue(missingFade, true);
            AndroidJavaClass.GameReady = false;
            update.Invoke(missingFade, null);
            AndroidJavaClass.GameReady = true;
            update.Invoke(missingFade, null);
            Assert(MonoBehaviour.CoroutineStarts == beforeCoroutines + 1,
                "missing fade schedules fallback VR handoff on Unity thread");
            update.Invoke(missingFade, null);
            Assert(MonoBehaviour.CoroutineStarts == beforeCoroutines + 1,
                "missing fade never schedules duplicate handoffs");

            // The XR loader must be deinitialized via a Unity yield
            // instruction, not Task.Delay on a JNI/background thread.
            var manager = new UnityEngine.XR.Management.XRManagerSettings();
            UnityEngine.XR.Management.XRGeneralSettings.Instance =
                new UnityEngine.XR.Management.XRGeneralSettings {
                    Manager = manager
                };
            var finish = typeof(InstanceButton).GetMethod("FinishVrHandoff",
                System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic);
            Assert(finish != null, "VR handoff uses a dedicated Unity coroutine");
            var handoff = (IEnumerator)finish.Invoke(missingFade, null);
            Assert(handoff.MoveNext() &&
                handoff.Current is WaitForSecondsRealtime,
                "VR completion first yields a Unity realtime delay");
            Assert(manager.DeinitializeCalls == 0,
                "OpenXR loader is not deinitialized ahead of Unity fade");
            Assert(!handoff.MoveNext() && manager.DeinitializeCalls == 1,
                "OpenXR loader is deinitialized once on the Unity coroutine");
            AndroidJavaClass.GameReady = false;

            // Pojlib may catch Java exceptions internally. The JVM can keep
            // loading forever without ever publishing gameReady. Detect this
            // after a bounded period, restore the launcher and give guidance.
            var watchUi = new UIHandler();
            var watchdog = new InstanceButton {
                configHandler = prefs, uiHandler = watchUi,
                ScreenFade = new CanvasGroup { gameObject = new GameObject() }
            };
            var flags = System.Reflection.BindingFlags.Instance |
                System.Reflection.BindingFlags.NonPublic;
            typeof(InstanceButton).GetField("launchInProgress", flags)
                .SetValue(watchdog, true);
            typeof(InstanceButton).GetField("javaLaunchDispatched", flags)
                .SetValue(watchdog, true);
            typeof(InstanceButton).GetField("waitingForGameReady", flags)
                .SetValue(watchdog, true);
            typeof(InstanceButton).GetField("gameLaunchStartedAt", flags)
                .SetValue(watchdog, 1f);
            ProgressBarManager.started = true;
            Time.realtimeSinceStartup = 180f;
            update.Invoke(watchdog, null);
            Assert(watchUi.killCalls == 0,
                "VR launch remains active before 180-second gameReady timeout");
            Time.realtimeSinceStartup = 182f;
            update.Invoke(watchdog, null);
            Assert(watchUi.killCalls == 1 && !ProgressBarManager.started,
                "expired gameReady watchdog restores original QuestCraft menu");
            Assert(watchUi.lastError != null &&
                watchUi.lastError.Contains("180 секунд"),
                "timeout displays a meaningful Java/Pojlib diagnostic");
            update.Invoke(watchdog, null);
            Assert(watchUi.killCalls == 1,
                "timed-out watchdog cannot repeatedly dismiss the launcher");

            // A timed-out Java worker may still be modifying the instance.
            // Never start another copy until it has terminated.
            var nestedWorker = typeof(InstanceButton).GetNestedType(
                "PrelaunchState", System.Reflection.BindingFlags.NonPublic);
            Assert(nestedWorker != null, "pending Java worker state is testable");
            var pendingWorker = System.Activator.CreateInstance(nestedWorker, true);
            nestedWorker.GetField("completed").SetValue(pendingWorker, false);
            typeof(InstanceButton).GetField("pendingGameLaunch", flags)
                .SetValue(watchdog, pendingWorker);
            JNIStorage.ActiveInstance = null;
            int javaCalls = AndroidJavaClass.LaunchInstanceCallCount;
            watchdog.LaunchCurrentInstance();
            Assert(watchUi.lastError != null &&
                watchUi.lastError.Contains("Предыдущий запуск Java"),
                "timed-out native worker blocks overlapping Minecraft launch");
            Assert(AndroidJavaClass.LaunchInstanceCallCount == javaCalls,
                "blocked reattempt does not create a second Java worker");
            nestedWorker.GetField("completed").SetValue(pendingWorker, true);
            watchdog.LaunchCurrentInstance();
            Assert(watchUi.lastError != null &&
                watchUi.lastError.Contains("installing"),
                "the player can try again after the old Java worker finishes");

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
