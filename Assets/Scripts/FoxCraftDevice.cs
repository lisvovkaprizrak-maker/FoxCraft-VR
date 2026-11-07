using System;
using UnityEngine;

public static class FoxCraftDevice
{
    private static bool? detectedHeadset;
    private static bool warningLogged;

    public static bool IsVrHeadset
    {
        get
        {
            if (Application.platform != RuntimePlatform.Android)
                return false;

            // These models are recognizable even before the UnityPlayer
            // Android activity is available during the first frame.
            string model = (SystemInfo.deviceModel ?? "").ToLowerInvariant();
            if (model.Contains("quest") || model.Contains("oculus") ||
                model.Contains("pico") || model.Contains("yvr"))
            {
                detectedHeadset = true;
                return true;
            }

            if (detectedHeadset.HasValue)
                return detectedHeadset.Value;

            // Android system features are a better source of truth for devices
            // whose model names are codenames instead of consumer headset names.
            // Do not cache a negative result until a PackageManager is present.
            try
            {
                using (AndroidJavaClass unityPlayer =
                    new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (AndroidJavaObject activity =
                    unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
                {
                    if (activity == null)
                        return false;

                    using (AndroidJavaObject manager =
                        activity.Call<AndroidJavaObject>("getPackageManager"))
                    {
                        if (manager == null)
                            return false;

                        bool headTracking = manager.Call<bool>("hasSystemFeature",
                            "android.hardware.vr.headtracking");
                        bool oculusVr = manager.Call<bool>("hasSystemFeature",
                            "com.oculus.feature.VR");
                        detectedHeadset = headTracking || oculusVr;
                        return detectedHeadset.Value;
                    }
                }
            }
            catch (Exception exception)
            {
                if (!warningLogged)
                {
                    warningLogged = true;
                    Debug.LogWarning("[FoxCraft] Headset capability detection unavailable: " +
                        exception.GetType().Name);
                }
                return false;
            }
        }
    }

    // Device model codenames are ambiguous until Android's PackageManager
    // initializes. Callers that select cameras or replace input modules
    // should defer the decision during the earliest Unity frames.
    public static bool IsDetectionPending
    {
        get
        {
            if (Application.platform != RuntimePlatform.Android)
                return false;
            // This resolves known Quest/Pico models without JNI and probes
            // Android hardware features for unrecognized device models.
            bool isHeadset = IsVrHeadset;
            return !isHeadset && !detectedHeadset.HasValue;
        }
    }

    // If Android never exposes its PackageManager, use the same flat
    // fallback for both camera and touch. This is called only after a bounded
    // startup wait; Quest/Pico models and reported VR capabilities win first.
    public static void CompleteDetectionWithPhoneFallback()
    {
        if (Application.platform == RuntimePlatform.Android &&
            IsDetectionPending)
        {
            detectedHeadset = false;
            Debug.LogWarning("[FoxCraft] VR capabilities unavailable after startup; " +
                "using flat Android launcher controls.");
        }
    }

    public static bool IsAndroidPhone =>
        Application.platform == RuntimePlatform.Android &&
        !IsDetectionPending && !IsVrHeadset;
}
