using System;
using UnityEngine;

// Pojlib's existing hasConnection() checks Wi-Fi only. Android phones can
// use cellular, Ethernet or USB tethering; do not report them offline solely
// because they are not on a Wi-Fi network.
public static class FoxCraftNetworkStatus
{
    private const int NetCapabilityValidated = 16;

    public static bool HasValidatedInternet()
    {
        if (Application.platform != RuntimePlatform.Android)
            return false;

        try
        {
            using (AndroidJavaClass unityPlayer =
                new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            using (AndroidJavaObject activity =
                unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                if (activity == null)
                    return false;

                using (AndroidJavaObject connectivity =
                    activity.Call<AndroidJavaObject>("getSystemService",
                        "connectivity"))
                {
                    if (connectivity == null)
                        return false;

                    using (AndroidJavaObject network =
                        connectivity.Call<AndroidJavaObject>("getActiveNetwork"))
                    {
                        if (network == null)
                            return false;

                        using (AndroidJavaObject capabilities =
                            connectivity.Call<AndroidJavaObject>(
                                "getNetworkCapabilities", network))
                        {
                            return capabilities != null &&
                                capabilities.Call<bool>("hasCapability",
                                    NetCapabilityValidated);
                        }
                    }
                }
            }
        }
        catch (Exception exception)
        {
            Debug.LogWarning("[FoxCraft] Android network capability unavailable: " +
                exception.GetType().Name);
            return false;
        }
    }
}
