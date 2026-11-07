using System;
using UnityEngine;

// The launcher and its Java Edition phone game backend have separate lifecycles.
// A phone runtime must provide a real, touch-capable Minecraft renderer before
// the launcher will report a successful phone launch.
//
// Android bridge contract (implemented by the future in-APK 2D module):
//   dev.foxcraft.mobile.MobileMinecraftBridge.isAvailable(Activity) : boolean
//   dev.foxcraft.mobile.MobileMinecraftBridge.launch(Activity, MinecraftAccount,
//       String minecraftVersion) : boolean
//
// Do NOT route phone launches to QuestCraft's Vivecraft/OpenXR launchInstance.
public static class FoxCraftPhoneRuntime
{
    private const string BridgeClass = "dev.foxcraft.mobile.MobileMinecraftBridge";

    public static bool TryLaunch(string version, out string error)
    {
        error = "2D-движок Minecraft ещё не включён в этот APK FoxCraft.";
        if (Application.platform != RuntimePlatform.Android ||
            JNIStorage.activity == null || JNIStorage.accountObj == null ||
            string.IsNullOrWhiteSpace(version))
        {
            error = "Для запуска Minecraft на телефоне требуется вход в аккаунт " +
                "и готовый 2D-движок.";
            return false;
        }

        try
        {
            using (AndroidJavaClass bridge = new AndroidJavaClass(BridgeClass))
            {
                if (!bridge.CallStatic<bool>("isAvailable", JNIStorage.activity))
                    return false;

                if (bridge.CallStatic<bool>("launch", JNIStorage.activity,
                    JNIStorage.accountObj, version))
                    return true;

                error = "2D-движок не смог запустить выбранную версию Minecraft.";
                return false;
            }
        }
        catch (Exception exception)
        {
            Debug.LogWarning("[FoxCraft] Phone runtime is unavailable: " +
                exception.GetType().Name);
            // Missing class is expected until a genuine 2D runtime is bundled.
            return false;
        }
    }
}
