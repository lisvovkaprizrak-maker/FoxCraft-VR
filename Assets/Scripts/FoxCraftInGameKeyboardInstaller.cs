using System;
using System.Collections;
using System.IO;
using System.Security.Cryptography;
using UnityEngine;
using UnityEngine.Networking;

/// <summary>
/// Installs an optional, pre-validated Fabric 1.21.5 keyboard JAR into the
/// selected Pojlib instance before Minecraft launches. Never overwrites mods.
/// </summary>
public static class FoxCraftInGameKeyboardInstaller
{
    private const string ModJarName = "foxcraft-keyboard-0.1.0-alpha.jar";

    public static IEnumerator EnsureInstalled(AndroidJavaObject instance, Action<bool> complete)
    {
        if (Application.platform != RuntimePlatform.Android || instance == null ||
            !TryFindTargetFolder(instance, out string targetFolder))
        {
            complete?.Invoke(false);
            yield break;
        }

        if (ExistingKeyboardFound(targetFolder, out bool sameVersion) && !sameVersion)
        {
            // An incompatible installed version (or inaccessible mods folder)
            // must never be replaced silently.
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
                Debug.LogWarning("[FoxCraft] In-game keyboard JAR is not bundled in this APK. " +
                    "Stage the Fabric build into StreamingAssets before packaging.");
                complete?.Invoke(false);
                yield break;
            }

            byte[] jar = request.downloadHandler.data;
            if (jar == null || jar.Length < 1024 || jar[0] != (byte)'P' ||
                jar[1] != (byte)'K' || jar[2] != 3 || jar[3] != 4)
            {
                Debug.LogWarning("[FoxCraft] Staged keyboard JAR has an invalid ZIP signature.");
                complete?.Invoke(false);
                yield break;
            }

            bool installed = InstallAtomically(targetFolder, jar);
            complete?.Invoke(installed);
        }
    }

    private static bool TryFindTargetFolder(AndroidJavaObject instance, out string folder)
    {
        folder = null;
        try
        {
            // Both fields come from the bundled Pojlib Java API.
            string gameDirectory = instance.Get<string>("gameDir");
            string homeDirectory;
            using (AndroidJavaClass constants = new AndroidJavaClass("pojlib.util.Constants"))
                homeDirectory = constants.GetStatic<string>("USER_HOME");
            if (String.IsNullOrEmpty(gameDirectory) || String.IsNullOrEmpty(homeDirectory))
                throw new IOException("Pojlib game directory is not initialized");

            string home = Path.GetFullPath(homeDirectory);
            string instanceRoot = Path.GetFullPath(Path.Combine(home, "instances")) +
                Path.DirectorySeparatorChar;
            string resolvedGame = Path.GetFullPath(gameDirectory);

            if (!resolvedGame.StartsWith(instanceRoot, StringComparison.Ordinal) ||
                !Directory.Exists(resolvedGame))
                throw new IOException("Game directory is outside Pojlib instances");

            folder = Path.Combine(resolvedGame, "mods");
            return true;
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Cannot locate this Pojlib instance: " +
                ex.GetType().Name);
            return false;
        }
    }

    private static bool ExistingKeyboardFound(string folder, out bool sameVersion)
    {
        sameVersion = false;
        try
        {
            if (!Directory.Exists(folder)) return false;
            foreach (string file in Directory.GetFiles(folder, "*.jar"))
            {
                string name = Path.GetFileName(file);
                if (!name.StartsWith("foxcraft-keyboard-", StringComparison.OrdinalIgnoreCase))
                    continue;
                sameVersion = name.Equals(ModJarName, StringComparison.OrdinalIgnoreCase);
                if (sameVersion)
                    Debug.Log("[FoxCraft] Existing in-game keyboard JAR retained.");
                else
                    Debug.LogWarning("[FoxCraft] Different keyboard JAR detected. " +
                        "No files were overwritten.");
                return true;
            }
            return false;
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Cannot inspect mods: " + ex.GetType().Name);
            return true; // Fail closed. Leave the mods directory unchanged.
        }
    }

    private static bool InstallAtomically(string folder, byte[] bytes)
    {
        string destination = Path.Combine(folder, ModJarName);
        string temporary = destination + ".pending";
        try
        {
            Directory.CreateDirectory(folder);
            if (File.Exists(destination))
            {
                // A matching filename does not prove it contains the same JAR.
                // Never replace a user-installed file of unknown origin.
                using (SHA256 sha = SHA256.Create())
                {
                    byte[] expected = sha.ComputeHash(bytes);
                    using (FileStream existing = File.OpenRead(destination))
                    {
                        byte[] actual = sha.ComputeHash(existing);
                        if (actual.Length != expected.Length)
                            return false;
                        for (int i = 0; i < expected.Length; i++)
                        {
                            if (expected[i] == actual[i]) continue;
                            Debug.LogWarning("[FoxCraft] Existing keyboard JAR has different contents; " +
                                "leaving it unchanged.");
                            return false;
                        }
                    }
                }
                Debug.Log("[FoxCraft] Matching keyboard JAR is already installed.");
                return true;
            }

            File.WriteAllBytes(temporary, bytes);
            if (!File.Exists(destination))
                File.Move(temporary, destination);
            bool installed = File.Exists(destination);
            if (installed)
                Debug.Log("[FoxCraft] Fabric 1.21.5 keyboard ready for Minecraft launch.");
            return installed;
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Cannot install keyboard JAR: " + ex.GetType().Name);
            return false;
        }
        finally
        {
            try { if (File.Exists(temporary)) File.Delete(temporary); }
            catch (Exception) { }
        }
    }
}
