// Compile-only stand-ins. NEVER import this into the real Unity project.
using System;
using System.Collections;
using System.Collections.Generic;

namespace UnityEngine
{
    public enum RuntimePlatform { Android, WindowsEditor }
    public static class Application
    {
        public static string persistentDataPath { get; set; } = ".";
        public static RuntimePlatform platform { get; set; } = RuntimePlatform.Android;
    }
    public class MonoBehaviour
    {
        public GameObject gameObject = new GameObject();
        protected object StartCoroutine(IEnumerator routine) => null;
        protected static T FindObjectOfType<T>() where T : new() => new T();
    }
    public class GameObject
    {
        public Transform transform = new RectTransform();
        public bool activeSelf;
        public bool activeInHierarchy;
        public GameObject() { }
        public GameObject(string name, params Type[] types) { }
        public void SetActive(bool value) { activeSelf = value; activeInHierarchy = value; }
        public T GetComponent<T>() where T : new() => new T();
        public T GetComponentInParent<T>() where T : new() => new T();
        public T AddComponent<T>() where T : new() => new T();
    }
    public class Transform
    {
        public void SetParent(Transform parent, bool worldPositionStays) { }
    }
    public class RectTransform : Transform
    {
        public Vector2 anchorMin, anchorMax, anchoredPosition, sizeDelta;
    }
    public struct Vector2
    {
        public float x, y;
        public Vector2(float x, float y) { this.x = x; this.y = y; }
        public static Vector2 zero => new Vector2(0f, 0f);
    }
    public struct Color
    {
        public Color(float r, float g, float b, float a = 1f) { }
    }
    public struct Rect
    {
        public Rect(float x, float y, float w, float h) { }
    }
    public class Texture2D { public int width = 96, height = 96; }
    public class Sprite
    {
        public static Sprite Create(Texture2D texture, Rect rect, Vector2 pivot) =>
            new Sprite();
    }
    public static class Resources
    {
        public static T Load<T>(string name) where T : class => default(T);
    }
    public class Canvas { public Transform transform = new Transform(); }
    public class AudioSource { public int playCount; public void Play() { playCount++; } }

    public static class Debug
    {
        public static void Log(object message) { }
        public static void LogWarning(object message) { }
    }
    public static class Mathf
    {
        public static int Clamp(int value, int low, int high) =>
            Math.Max(low, Math.Min(value, high));
        public static float Clamp01(float value) =>
            Math.Max(0f, Math.Min(1f, value));
    }
    public static class SystemInfo { public static string deviceModel { get; set; } }
    [AttributeUsage(AttributeTargets.Field)]
    public sealed class SerializeField : Attribute { }
    public class AndroidJavaObject : IDisposable
    {
        public static bool VrTrackingFeature;
        public static bool OculusVrFeature;
        public string uuid;
        public string username;
        public bool isDemoMode;
        public void Dispose() { }
        public T Get<T>(string field)
        {
            object value = field == "uuid" ? uuid :
                field == "username" ? username :
                field == "isDemoMode" ? (object)isDemoMode : null;
            return value == null ? default(T) : (T)value;
        }
        public T Call<T>(string name, params object[] parameters)
        {
            if (name == "getPackageManager")
                return (T)(object)new AndroidJavaObject();
            if (name == "hasSystemFeature" && parameters.Length > 0)
            {
                string feature = parameters[0] as string;
                bool enabled = feature == "android.hardware.vr.headtracking"
                    ? VrTrackingFeature : feature == "com.oculus.feature.VR" && OculusVrFeature;
                return (T)(object)enabled;
            }
            return default(T);
        }
    }
    public class AndroidJavaClass : IDisposable
    {
        public static int LoginCallCount;
        public static bool DownloadsCompleted;
        public static bool FailDownloadProbe;
        public static float DownloadPercentage;
        public AndroidJavaClass() { }
        public AndroidJavaClass(string className) { }
        public void Dispose() { }
        public void SetStatic(string name, object value) { }
        public void CallStatic(string name, params object[] parameters)
        {
            if (name == "login") LoginCallCount++;
        }
        public T CallStatic<T>(string name, params object[] parameters)
        {
            if (name == "isDownloadsCompleted")
            {
                if (FailDownloadProbe)
                    throw new InvalidOperationException("Simulated Pojlib download probe failure");
                return (T)(object)DownloadsCompleted;
            }
            if (name == "getDownloadPercentage")
                return (T)(object)DownloadPercentage;
            return default(T);
        }
        public T GetStatic<T>(string name)
        {
            if (name == "currentActivity")
                return (T)(object)JNIStorage.activity;
            return default(T);
        }
    }
}
namespace UnityEngine.Networking
{
    public class DownloadHandler { public string text; }
    public class UnityWebRequest : IDisposable
    {
        public enum Result { Success, ConnectionError }
        public Result result;
        public string error;
        public int timeout;
        public DownloadHandler downloadHandler = new DownloadHandler();
        public static UnityWebRequest Get(string uri) => new UnityWebRequest();
        public object SendWebRequest() => null;
        public void Dispose() { }
    }
}
namespace UnityEngine.Serialization
{
    [AttributeUsage(AttributeTargets.Field)]
    public sealed class FormerlySerializedAsAttribute : Attribute
    {
        public FormerlySerializedAsAttribute(string name) { }
    }
}
namespace TMPro
{
    public class TMP_Dropdown
    {
        public class OptionData { public string text; }
        public List<OptionData> options = new List<OptionData>();
        public int value;
        public bool interactable;
        public void ClearOptions() => options.Clear();
        public void AddOptions(List<string> values)
        {
            foreach (string value in values) options.Add(new OptionData { text = value });
        }
        public void SetValueWithoutNotify(int selected) => value = selected;
    }
    public class TMP_InputField { public string text; public bool enabled = true; public bool IsActive() => enabled; public UnityEngine.GameObject gameObject = new UnityEngine.GameObject(); }
    public enum TextAlignmentOptions { Center }
    public class TextMeshProUGUI
    {
        public string text;
        public float fontSize;
        public UnityEngine.Color color;
        public bool raycastTarget;
        public TextAlignmentOptions alignment;
        public UnityEngine.GameObject gameObject = new UnityEngine.GameObject();
    }
}
namespace UnityEngine.UI
{
    public sealed class BoolEvent
    {
        public void AddListener(Action<bool> callback) { }
        public void RemoveListener(Action<bool> callback) { }
    }
    public class Toggle
    {
        public bool isOn;
        public BoolEvent onValueChanged = new BoolEvent();
    }
    public sealed class ClickEvent
    {
        public void AddListener(Action callback) { }
    }
    public class Button
    {
        public UnityEngine.GameObject gameObject = new UnityEngine.GameObject();
        public bool interactable;
        public ClickEvent onClick = new ClickEvent();
        public T GetComponentInParent<T>() where T : new() => new T();
    }
    public class Slider
    {
        public UnityEngine.GameObject gameObject = new UnityEngine.GameObject();
        public float value;
    }
    public class Image
    {
        public UnityEngine.Color color;
        public UnityEngine.Sprite sprite;
        public bool preserveAspect, raycastTarget;
    }
}
namespace Newtonsoft.Json
{
    public enum Formatting { Indented }
    public sealed class JsonException : Exception
    {
        public JsonException(string message) : base(message) { }
    }
    public static class JsonConvert
    {
        private static System.Text.Json.JsonSerializerOptions Options(bool indent = false) =>
            new System.Text.Json.JsonSerializerOptions { IncludeFields = true, WriteIndented = indent };
        public static T DeserializeObject<T>(string json)
        {
            try { return System.Text.Json.JsonSerializer.Deserialize<T>(json, Options()); }
            catch (System.Text.Json.JsonException e) { throw new JsonException(e.Message); }
        }
        public static string SerializeObject(object value, Formatting format) =>
            System.Text.Json.JsonSerializer.Serialize(value, Options(format == Formatting.Indented));
    }
}
public class WindowHandler
{
    public void LegalSetter() { }
    public void LogoutWindowUnsetter() { }
    public void LoadAv(string name) { }
    public UnityEngine.GameObject mainPanel = new UnityEngine.GameObject();
}
public class UIHandler
{
    public static int selectedInstance;
    public void UpdateDropdowns(bool initial, List<string> entries) { }
    public UnityEngine.UI.Button playButton = new UnityEngine.UI.Button();
    public TMPro.TMP_Dropdown dropdownMain = new TMPro.TMP_Dropdown();
}
public class SkinHandler { }
public class JNIStorage
{
    public static JNIStorage instance;
    public static UnityEngine.AndroidJavaClass apiClass;
    public static UnityEngine.AndroidJavaObject activity;
    public static UnityEngine.AndroidJavaObject accountObj;
    public ConfigHandler configHandler;
    public bool InitializationFailed { get; set; }
    public UIHandler uiHandler;
    public void UpdateInstances() { }
    public TMPro.TMP_Dropdown instancesDropdown;
}
