using System;
using System.Collections;
using System.IO;
using UnityEngine;
using UnityEngine.Networking;

/// <summary>
/// Optional installer of the separately built FoxCraft Fabric 1.21.5 mod.
/// Called on the Unity main thread before Pojlib launches the game.
/// Never overwrites existing mod JARs, user worlds, or external QuestCraft data.
/// </summary>
public static class FoxCraftInGameKeyboardInstaller
{
    private const string ModJarName = "foxcraft-keyboard-0.1.0-alpha.jar";

    public static IEnumerator EnsureInstalled(AndroidJavaObject instance, Action<bool> complete)
    {
        if (Application.platform != RuntimePlatform.Android || instance == null)
        {
            complete?.Invoke(false);
            yield break;
        }

        string gameDirectory;
        string homeDirectory;
        try
        {
            // Pojlib's MinecraftInstances.Instance has a public gameDir field.
            gameDirectory = instance.Get<string>("gameDir");
            using (AndroidJavaClass constants = new AndroidJavaClass("pojlib.util.Constants"))
                homeDirectory = constants.GetStatic<string>("USER_HOME");
            if (String.IsNullOrEmpty(gameDirectory) || String.IsNullOrEmpty(homeDirectory))
                throw new IOException("Pojlib game directory has not been initialized");
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Cannot determine Pojlib instance directory: " +
                ex.GetType().Name);
            complete?.Invoke(false);
            yield break;
        }

        string targetFolder;
        try
        {
            string home = Path.GetFullPath(homeDirectory);
            string instanceRoot = Path.GetFullPath(Path.Combine(home, "instances")) +
                Path.DirectorySeparatorChar;
            string resolvedGame = Path.GetFullPath(gameDirectory);
            if (!resolvedGame.StartsWith(instanceRoot, StringComparison.Ordinal) ||
                !Directory.Exists(resolvedGame))
                throw new IOException("Instance path is outside the Pojlib instances directory");
            targetFolder = Path.Combine(resolvedGame, "mods");

            if (Directory.Exists(targetFolder))
            {
                foreach (string existing in Directory.GetFiles(targetFolder, "*.jar"))
                {
                    string fileName = Path.GetFileName(existing);
                    if (fileName.StartsWith("foxcraft-keyboard-", StringComparison.OrdinalIgnoreCase))
                    {
                        if (fileName.Equals(ModJarName, StringComparison.OrdinalIgnoreCase))
                        {
                            Debug.Log("[FoxCraft] Existing in-game keyboard JAR retained.");
                            complete?.Invoke(true);
                        }
                        else
                        {
                            Debug.LogWarning("[FoxCraft] Another FoxCraft keyboard version exists. " +
                                "No mod JARs were replaced.");
                            complete?.Invoke(false);
                        }
                        yield break;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Cannot inspect instance mods: " + ex.GetType().Name);
            complete?.Invoke(false);
            yield break;
        }

        string assetUrl = Application.streamingAssetsPath.TrimEnd('/') + "/" + ModJarName;
        using (UnityWebRequest request = UnityWebRequest.Get(assetUrl))
        {
            yield return request.SendWebRequest();
            if (request.result != UnityWebRequest.Result.Success ||
                request.downloadHandler == null)
            {
                Debug.LogWarning("[FoxCraft] In-game keyboard JAR not bundled in this APK. " +
                    "Stage the Fabric build in Assets/StreamingAssets before building.");
                complete?.Invoke(false);
                yield break;
            }

            byte[] jar = request.downloadHandler.data;
            if (jar == null || jar.Length < 1024 || jar[0] != (byte)'P' ||
                jar[1] != (byte)'K' || jar[2] != 3 || jar[3] != 4)
            {
                Debug.LogWarning("[FoxCraft] Staged keyboard is not a valid JAR ZIP.");
                complete?.Invoke(false);
                yield break;
            }

            string temporary = Path.Combine(targetFolder, ModJarName + ".pending");
            string destination = Path.Combine(targetFolder, ModJarName);
            bool installed = false;
            try
            {
                Directory.CreateDirectory(targetFolder);
                if (File.Exists(destination))
                    installed = true; // Never replace the user's existing JAR.
                else
                {
                    File.WriteAllBytes(temporary, jar);
                    if (!File.Exists(destination))
                        File.Move(temporary, destination);
                    installed = File.Exists(destination);
                }
                if (installed)
                    Debug.Log("[FoxCraft] In-game keyboard mod is ready for Minecraft 1.21.5.");
            }
            catch (Exception ex)
            {
                Debug.LogWarning("[FoxCraft] Mod installation failed: " + ex.GetType().Name);
            }
            finally
            {
                try { if (File.Exists(temporary)) File.Delete(temporary); }
                catch (Exception) { }
            }

            complete?.Invoke(installed);
        }
    }
}
