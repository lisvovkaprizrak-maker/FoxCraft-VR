using UnityEngine;

public static class FoxCraftDevice
{
    public static bool IsVrHeadset
    {
        get
        {
            if (Application.platform != RuntimePlatform.Android)
                return false;

            string model = (SystemInfo.deviceModel ?? "").ToLowerInvariant();
            return model.Contains("quest") || model.Contains("oculus") ||
                   model.Contains("pico") || model.Contains("yvr");
        }
    }

    public static bool IsAndroidPhone =>
        Application.platform == RuntimePlatform.Android && !IsVrHeadset;
}
