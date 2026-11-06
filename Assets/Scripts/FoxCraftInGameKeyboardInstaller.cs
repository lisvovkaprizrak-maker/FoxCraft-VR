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

    /// <summary>
    /// A Minecraft version number alone does not imply the Fabric loader.
    /// Pojlib exposes the actual Java mainClass on each instance; gate the
    /// installation so a 1.21.5 Forge/vanilla profile remains untouched.
    /// </summary>
    public static bool UsesFabricLoader(AndroidJavaObject instance)
    {
        if (instance == null) return false;
        try
        {
            string mainClass = instance.Get<string>("mainClass");
            return !String.IsNullOrEmpty(mainClass) &&
                mainClass.StartsWith("net.fabricmc.loader.", StringComparison.Ordinal) &&
                mainClass.EndsWith(".KnotClient", StringComparison.Ordinal);
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Cannot verify Fabric loader: " + ex.GetType().Name);
            return false;
        }
    }

    private static bool IsCorrectMinecraftVersion(AndroidJavaObject instance)
    {
        try
        {
            return String.Equals(instance.Get<string>("versionName"), "1.21.5",
                StringComparison.Ordinal);
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Cannot verify Minecraft profile version: " +
                ex.GetType().Name);
            return false;
        }
    }

    public static IEnumerator EnsureInstalled(AndroidJavaObject instance, Action<bool> complete)
    {
        // Validate again at the installation boundary: a caller must not be
        // able to inject this 1.21.5 Fabric mod into a different game profile.
        if (Application.platform != RuntimePlatform.Android || instance == null ||
            !IsCorrectMinecraftVersion(instance) || !UsesFabricLoader(instance) ||
            !TryFindTargetFolder(instance, out string targetFolder))
        {
            complete?.Invoke(false);
            yield break;
        }

        // The Android host exposes SpeechRecognizer to the isolated Pojlib JVM
        // through this instance's local config directory.
        FoxCraftVoiceBridge.RegisterGameDirectory(Path.GetDirectoryName(targetFolder));

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
            request.timeout = 30;
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
            bool foundExpectedVersion = false;
            foreach (string file in Directory.GetFiles(folder, "*.jar"))
            {
                string name = Path.GetFileName(file);
                if (!name.StartsWith("foxcraft-keyboard-", StringComparison.OrdinalIgnoreCase))
                    continue;

                if (!name.Equals(ModJarName, StringComparison.OrdinalIgnoreCase))
                {
                    // Scan ALL keyboard mods. Returning on the first matching
                    // filename could miss a second, incompatible installed mod.
                    Debug.LogWarning("[FoxCraft] Different keyboard JAR also detected: " +
                        name + ". No mod files were modified.");
                    return true;
                }

                foundExpectedVersion = true;
            }

            sameVersion = foundExpectedVersion;
            if (foundExpectedVersion)
                Debug.Log("[FoxCraft] Existing keyboard version detected. Contents will be verified.");
            return foundExpectedVersion;
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
        // Unique create-only staging path: concurrent launches must never
        // overwrite/delete another process's pending mod file.
        string temporary = destination + ".pending-" + Guid.NewGuid().ToString("N");
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

            using (FileStream staging = new FileStream(
                temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                staging.Write(bytes, 0, bytes.Length);
                staging.Flush();
            }

            // A ZIP header alone cannot guarantee that Android storage wrote
            // the complete content. Confirm the staged file before publishing.
            using (SHA256 sha = SHA256.Create())
            using (FileStream staged = File.OpenRead(temporary))
            {
                byte[] expected = sha.ComputeHash(bytes);
                byte[] actual = sha.ComputeHash(staged);
                if (actual.Length != expected.Length)
                    throw new IOException("Staged FoxCraft mod checksum mismatch");
                for (int i = 0; i < expected.Length; i++)
                    if (actual[i] != expected[i])
                        throw new IOException("Staged FoxCraft mod checksum mismatch");
            }

            // File.Move within the same mods directory is atomic on supported
            // Android filesystems. No existing game mod should be overwritten.
            if (File.Exists(destination))
                return false;
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
