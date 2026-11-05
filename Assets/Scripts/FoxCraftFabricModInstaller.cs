// Experimental FoxCraft companion for QuestChatKeyboardOverlayBridge.
// Copies the bundled Fabric JAR into the FoxCraft Minecraft instance once.
// This cannot install into the original QuestCraft app (different Android package).
using System;
using System.Collections;
using System.IO;
using UnityEngine;
using UnityEngine.Networking;

public sealed class FoxCraftFabricModInstaller : MonoBehaviour
{
    private const string Instance = "1.21.1";
    private const string JarName = "FoxChatKeyboard-bridge-0.6.0.jar";
    private static FoxCraftFabricModInstaller instance;
    private bool completed;
    private bool installing;
    private float nextAttempt;

    [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
    private static void Bootstrap()
    {
        if (instance != null) return;
        var host = new GameObject("FoxCraft-FabricModInstaller");
        host.AddComponent<FoxCraftFabricModInstaller>();
    }

    private void Awake()
    {
        if (instance != null && instance != this)
        {
            Destroy(gameObject);
            return;
        }
        instance = this;
        DontDestroyOnLoad(gameObject);
    }

    private void Update()
    {
        if (completed || installing || Time.unscaledTime < nextAttempt) return;
        nextAttempt = Time.unscaledTime + 2f;
        string folder = FindInstance();
        if (!String.IsNullOrEmpty(folder))
        {
            installing = true;
            StartCoroutine(Install(folder));
        }
    }

    private static string FindInstance()
    {
        string[] roots = {
            Application.persistentDataPath,
            Path.Combine("/storage/emulated/0/Android/data", Application.identifier, "files")
        };
        foreach (string root in roots)
        {
            if (String.IsNullOrEmpty(root)) continue;
            string candidate = Path.Combine(root, "instances", Instance);
            if (Directory.Exists(candidate)) return candidate;
        }
        return null; // Pojlib can create the instance after the Unity scene starts.
    }

    private IEnumerator Install(string instanceFolder)
    {
        string mods = Path.Combine(instanceFolder, "mods");
        string dest = Path.Combine(mods, JarName);
        try
        {
            Directory.CreateDirectory(mods);
            if (File.Exists(dest))
            {
                // Do not silently replace a user-managed JAR.
                completed = true;
                Debug.Log("[FoxCraft] Existing Fabric bridge kept (not overwritten).");
            }
            else
            {
                foreach (string file in Directory.GetFiles(mods, "*.jar"))
                {
                    string name = Path.GetFileName(file).ToLowerInvariant();
                    if (name.Contains("questchatkeyboard") || name.Contains("foxchatkeyboard"))
                    {
                        completed = true;
                        Debug.LogWarning("[FoxCraft] Other keyboard bridge found; remove duplicates manually.");
                        break;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Cannot inspect mods folder: " + ex.GetType().Name);
        }

        if (completed)
        {
            installing = false;
            yield break;
        }

        // On Android, StreamingAssets lives inside base.apk and requires a URL read.
        string uri = Application.streamingAssetsPath.TrimEnd('/') + "/" + JarName;
        using (var request = UnityWebRequest.Get(uri))
        {
            yield return request.SendWebRequest();
            if (request.result != UnityWebRequest.Result.Success ||
                request.downloadHandler == null)
            {
                Debug.LogWarning("[FoxCraft] Bundled Fabric JAR inaccessible.");
                installing = false;
                yield break;
            }
            byte[] data = request.downloadHandler.data;
            if (data == null || data.Length < 1024 ||
                data[0] != (byte)'P' || data[1] != (byte)'K')
            {
                Debug.LogWarning("[FoxCraft] Bundled Fabric JAR is invalid.");
                completed = true;
                installing = false;
                yield break;
            }
            string temporary = dest + ".pending";
            try
            {
                if (!File.Exists(dest))
                {
                    File.WriteAllBytes(temporary, data);
                    if (!File.Exists(dest))
                        File.Move(temporary, dest);
                }
                if (File.Exists(temporary)) File.Delete(temporary);
                completed = File.Exists(dest);
                if (completed)
                    Debug.Log("[FoxCraft] Fabric JAR installed. Restart Minecraft if it is already running.");
            }
            catch (Exception ex)
            {
                try { if (File.Exists(temporary)) File.Delete(temporary); }
                catch (Exception) { }
                Debug.LogWarning("[FoxCraft] Fabric JAR install failed: " + ex.GetType().Name);
            }
        }
        installing = false;
    }

    private void OnDestroy()
    {
        if (instance == this) instance = null;
    }
}
