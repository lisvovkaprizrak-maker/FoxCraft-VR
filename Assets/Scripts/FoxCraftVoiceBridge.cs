using System;
using System.IO;
using System.Text;
using UnityEngine;
using UnityEngine.Android;

/// <summary>
/// Unity/Android half of FoxCraft voice dictation. Pojlib's Minecraft JVM
/// cannot load Android's SpeechRecognizer classes directly. The Android host
/// therefore listens and returns a single result through the local instance
/// config directory. This bridge never sends Minecraft chat messages.
/// </summary>
public sealed class FoxCraftVoiceBridge : MonoBehaviour
{
    private const string AudioPermission = "android.permission.RECORD_AUDIO";
    private static FoxCraftVoiceBridge instance;
    private string folder;
    private float nextPoll;
    private float nextHeartbeat;
    private string currentId;
    private AndroidJavaObject recognizer;
    // Keep a managed reference while Android holds the Java callback. On
    // Quest GC runs independently of SpeechRecognizer's Java lifetime.
    private VoiceEvents recognitionEvents;
    private bool listening;

    public static void RegisterGameDirectory(string gameDirectory)
    {
        if (Application.platform != RuntimePlatform.Android ||
            String.IsNullOrEmpty(gameDirectory)) return;
        if (instance == null)
        {
            GameObject host = new GameObject("FoxCraft Android voice bridge");
            DontDestroyOnLoad(host);
            instance = host.AddComponent<FoxCraftVoiceBridge>();
        }
        instance.Configure(Path.Combine(gameDirectory, "config", "foxcraft-voice-bridge"));
    }

    private void Configure(string dir)
    {
        try
        {
            Directory.CreateDirectory(dir);
            folder = dir;
            nextHeartbeat = 0f;
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Voice bridge storage unavailable: " +
                ex.GetType().Name);
            folder = null;
        }
    }

    private void Update()
    {
        if (String.IsNullOrEmpty(folder)) return;
        float now = Time.realtimeSinceStartup;
        if (now >= nextHeartbeat)
        {
            nextHeartbeat = now + 3f;
            try { File.WriteAllText(Path.Combine(folder, "ready"), "unity-android"); }
            catch (IOException) { }
        }
        if (now < nextPoll || listening) return;
        nextPoll = now + 0.2f;
        try
        {
            string request = Path.Combine(folder, "request");
            if (!File.Exists(request)) return;
            string[] parts = File.ReadAllLines(request);
            File.Delete(request);
            if (parts.Length < 2 || parts[0].Length > 40) return;
            currentId = parts[0];
            StartRecognition(parts[1].Equals("ru", StringComparison.OrdinalIgnoreCase));
        }
        catch (Exception ex)
        {
            Debug.LogWarning("[FoxCraft] Voice request error: " + ex.GetType().Name);
            if (!String.IsNullOrEmpty(currentId))
                Complete("ERROR", "Не удалось запустить микрофон");
        }
    }

    private void StartRecognition(bool russian)
    {
        if (!Permission.HasUserAuthorizedPermission(AudioPermission))
        {
            Permission.RequestUserPermission(AudioPermission);
            Complete("ERROR", "Разреши доступ к микрофону и повтори");
            return;
        }
        try
        {
            using (AndroidJavaClass unity = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            using (AndroidJavaObject activity = unity.GetStatic<AndroidJavaObject>("currentActivity"))
            {
                if (activity == null)
                {
                    Complete("ERROR", "Android Activity недоступна");
                    return;
                }
                // Android SpeechRecognizer requires its main UI thread.
                string id = currentId;
                activity.Call("runOnUiThread", new AndroidJavaRunnable(() =>
                {
                    using (AndroidJavaClass cls = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                    using (AndroidJavaObject current = cls.GetStatic<AndroidJavaObject>("currentActivity"))
                        BeginOnUiThread(current, russian, id);
                }));
            }
        }
        catch (Exception ex)
        {
            Complete("ERROR", "Сбой запуска: " + ex.GetType().Name);
        }
    }

    private void BeginOnUiThread(AndroidJavaObject activity, bool russian, string id)
    {
        try
        {
            using (AndroidJavaClass speech =
                new AndroidJavaClass("android.speech.SpeechRecognizer"))
            {
                if (!speech.CallStatic<bool>("isRecognitionAvailable", activity))
                {
                    Complete("ERROR", "На Quest нет службы распознавания речи");
                    return;
                }
                recognizer = speech.CallStatic<AndroidJavaObject>(
                    "createSpeechRecognizer", activity);
                recognitionEvents = new VoiceEvents(this, id);
                recognizer.Call("setRecognitionListener", recognitionEvents);
                using (AndroidJavaObject intent = new AndroidJavaObject(
                    "android.content.Intent", "android.speech.action.RECOGNIZE_SPEECH"))
                {
                    intent.Call<AndroidJavaObject>("putExtra",
                        "android.speech.extra.LANGUAGE_MODEL", "free_form");
                    intent.Call<AndroidJavaObject>("putExtra",
                        "android.speech.extra.LANGUAGE", russian ? "ru-RU" : "en-US");
                    listening = true;
                    recognizer.Call("startListening", intent);
                }
            }
        }
        catch (Exception ex)
        {
            Complete("ERROR", "Ошибка Android SpeechRecognizer: " +
                ex.GetType().Name);
        }
    }

    private sealed class VoiceEvents : AndroidJavaProxy
    {
        private readonly FoxCraftVoiceBridge owner;
        private readonly string id;

        public VoiceEvents(FoxCraftVoiceBridge owner, string id)
            : base("android.speech.RecognitionListener")
        {
            this.owner = owner;
            this.id = id;
        }
        public void onReadyForSpeech(AndroidJavaObject bundle) { }
        public void onBeginningOfSpeech() { }
        public void onRmsChanged(float rms) { }
        public void onBufferReceived(sbyte[] data) { }
        public void onEndOfSpeech() { }
        public void onPartialResults(AndroidJavaObject bundle) { }
        public void onEvent(int type, AndroidJavaObject bundle) { }
        public void onError(int error)
        {
            owner.Complete("ERROR", "Ошибка распознавания, код " + error, id);
        }
        public void onResults(AndroidJavaObject bundle)
        {
            string words = "";
            try
            {
                using (AndroidJavaObject texts = bundle.Call<AndroidJavaObject>(
                    "getStringArrayList", "results_recognition"))
                {
                    if (texts != null && texts.Call<int>("size") > 0)
                        words = texts.Call<string>("get", 0);
                }
            }
            catch (Exception ex)
            {
                Debug.LogWarning("[FoxCraft] Android voice result: " + ex.GetType().Name);
            }
            owner.Complete(String.IsNullOrWhiteSpace(words) ? "ERROR" : "OK",
                String.IsNullOrWhiteSpace(words) ? "Речь не распознана" : words, id);
        }
    }

    private void Complete(string status, string message, string id = null)
    {
        if (String.IsNullOrEmpty(folder)) return;
        string requestId = id ?? currentId;
        if (String.IsNullOrEmpty(requestId)) return;
        try
        {
            string target = Path.Combine(folder, "result");
            string staging = target + ".tmp";
            string encoded = Convert.ToBase64String(Encoding.UTF8.GetBytes(
                (message ?? "").Replace('\r', ' ').Replace('\n', ' ')));
            File.WriteAllText(staging, requestId + "\n" + status + "\n" + encoded);
            if (File.Exists(target)) File.Delete(target);
            File.Move(staging, target);
        }
        catch (IOException ex)
        {
            Debug.LogWarning("[FoxCraft] Voice result not saved: " + ex.GetType().Name);
        }
        listening = false;
        currentId = null;
        recognitionEvents = null;
        AndroidJavaObject old = recognizer;
        recognizer = null;
        if (old != null)
        {
            try
            {
                using (AndroidJavaClass unity = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (AndroidJavaObject activity = unity.GetStatic<AndroidJavaObject>("currentActivity"))
                    activity.Call("runOnUiThread", new AndroidJavaRunnable(() =>
                    {
                        try { old.Call("destroy"); }
                        catch (Exception) { }
                        old.Dispose();
                    }));
            }
            catch (Exception) { old.Dispose(); }
        }
    }

    private void OnApplicationQuit()
    {
        if (String.IsNullOrEmpty(folder)) return;
        try { File.Delete(Path.Combine(folder, "ready")); }
        catch (IOException) { }
    }
}
