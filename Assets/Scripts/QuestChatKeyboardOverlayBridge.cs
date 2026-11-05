// FoxCraft VR experimental Unity half of the QCKB1 Fabric <-> Unity bridge.
// Requires a matching Fabric mod installed in the Minecraft instance.
// This file does NOT prove the Meta system keyboard is available on Quest 3.
using System;
using System.IO;
using System.Text;
using UnityEngine;

public sealed class QuestChatKeyboardOverlayBridge : MonoBehaviour
{
    private const string Magic = "QCKB1";
    private const string InstanceName = "1.21.1";
    private static readonly Encoding Utf8 = new UTF8Encoding(false);
    private static QuestChatKeyboardOverlayBridge instance;

    private string bridgeDirectory;
    private string lastRequest = "";
    private string session = "";
    private TouchScreenKeyboard keyboard;
    private float nextSearch;
    private float nextPoll;
    private bool focused = true;
    private bool paused;
    private bool sawFocusLoss;
    private float focusReturnedAt = -1;

    [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
    private static void Bootstrap()
    {
        if (instance != null) return;
        GameObject host = new GameObject("FoxCraft-KeyboardBridge");
        host.AddComponent<QuestChatKeyboardOverlayBridge>();
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
        if (bridgeDirectory == null)
        {
            if (Time.unscaledTime >= nextSearch)
            {
                nextSearch = Time.unscaledTime + 2f;
                FindBridgeDirectory();
            }
            return;
        }

        if (Time.unscaledTime >= nextPoll)
        {
            nextPoll = Time.unscaledTime + 0.2f;
            PollRequests();
        }

        if (keyboard == null || session.Length == 0) return;
        try
        {
            switch (keyboard.status)
            {
                case TouchScreenKeyboard.Status.Done:
                    Respond("SUBMIT", keyboard.text);
                    break;
                case TouchScreenKeyboard.Status.Canceled:
                    Respond("CANCEL", "");
                    break;
                case TouchScreenKeyboard.Status.LostFocus:
                    // Meta's overlay can own focus temporarily. This is not Cancel.
                    // When focus returns, retain the typed text without sending it.
                    if (sawFocusLoss && focused && !paused &&
                        focusReturnedAt >= 0f &&
                        Time.unscaledTime - focusReturnedAt > 2f)
                    {
                        string text = keyboard.text;
                        Respond(String.IsNullOrEmpty(text) ? "CANCEL" : "COMMIT", text);
                    }
                    break;
            }
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Keyboard status failed: " + ex.GetType().Name);
            Respond("ERROR", "");
        }
    }

    private void FindBridgeDirectory()
    {
        // IMPORTANT: Unity's persistentDataPath may not equal Fabric's gameDir.
        // Discover an existing Minecraft instance before creating a bridge folder.
        string[] roots = {
            Application.persistentDataPath,
            Path.Combine("/storage/emulated/0/Android/data", Application.identifier, "files")
        };
        foreach (string root in roots)
        {
            if (String.IsNullOrEmpty(root)) continue;
            try
            {
                string game = Path.Combine(root, "instances", InstanceName);
                if (!Directory.Exists(game)) continue;
                string config = Path.Combine(game, "config");
                string candidate = Path.Combine(config, "questchatkeyboard-bridge");
                Directory.CreateDirectory(candidate);
                bridgeDirectory = candidate;
                Debug.Log("[FoxCraft] QCKB1 bridge directory found");
                return;
            }
            catch (Exception ex)
            {
                Debug.LogWarning("[FoxCraft] Bridge path unavailable: " + ex.GetType().Name);
            }
        }
    }

    private void PollRequests()
    {
        string path = Path.Combine(bridgeDirectory, "request.txt");
        try
        {
            if (!File.Exists(path)) return;
            FileInfo file = new FileInfo(path);
            if (file.Length <= 0 || file.Length > 256) return;
            string raw = File.ReadAllText(path, Utf8);
            if (raw == lastRequest) return;
            lastRequest = raw;
            if ((DateTime.UtcNow - file.LastWriteTimeUtc).TotalSeconds > 45) return;
            string[] parts = raw.Replace("\r", "").Split(
                new[] {'\n'}, StringSplitOptions.RemoveEmptyEntries);
            Guid validId;
            if (parts.Length != 3 || parts[0] != Magic ||
                !Guid.TryParse(parts[2], out validId)) return;

            if (parts[1] == "OPEN")
            {
                CloseKeyboard();
                session = parts[2];
                sawFocusLoss = false;
                focusReturnedAt = -1f;
                try
                {
                    keyboard = TouchScreenKeyboard.Open(
                        "", TouchScreenKeyboardType.Default,
                        false, false, false, false);
                    if (keyboard == null) Respond("ERROR", "");
                    else Debug.Log("[FoxCraft] Unity accepted keyboard request (overlay NOT verified)");
                }
                catch (Exception ex)
                {
                    Debug.LogWarning("[FoxCraft] Open failed: " + ex.GetType().Name);
                    Respond("ERROR", "");
                }
            }
            else if (parts[1] == "CLOSE" && parts[2] == session)
            {
                CloseKeyboard();
                session = "";
            }
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Request read failed: " + ex.GetType().Name);
        }
    }

    private void Respond(string action, string text)
    {
        string id = session;
        CloseKeyboard();
        session = "";
        if (id.Length == 0 || bridgeDirectory == null) return;
        try
        {
            string safeText = (text ?? "").Replace('\n', ' ').Replace('\r', ' ');
            if (safeText.Length > 256) safeText = safeText.Substring(0, 256);
            string encoded = Convert.ToBase64String(Utf8.GetBytes(safeText));
            string payload = Magic + "\n" + action + "\n" + id + "\n" + encoded + "\n";
            string temp = Path.Combine(bridgeDirectory, "response.tmp");
            string target = Path.Combine(bridgeDirectory, "response.txt");
            File.WriteAllText(temp, payload, Utf8);
            if (File.Exists(target)) File.Delete(target);
            File.Move(temp, target);
            // Deliberately never log any chat text, UUID or Base64 payload.
            Debug.Log("[FoxCraft] QCKB1 response status: " + action);
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Response write failed: " + ex.GetType().Name);
        }
    }

    private void CloseKeyboard()
    {
        if (keyboard != null)
        {
            try { keyboard.active = false; }
            catch (Exception) { }
        }
        keyboard = null;
    }

    private void OnApplicationFocus(bool value)
    {
        if (!value && session.Length != 0) sawFocusLoss = true;
        if (value && !focused && sawFocusLoss) focusReturnedAt = Time.unscaledTime;
        focused = value;
    }

    private void OnApplicationPause(bool value)
    {
        if (value && session.Length != 0) sawFocusLoss = true;
        if (!value && paused && sawFocusLoss) focusReturnedAt = Time.unscaledTime;
        paused = value;
    }

    private void OnDestroy()
    {
        if (instance == this) instance = null;
        CloseKeyboard();
    }
}
