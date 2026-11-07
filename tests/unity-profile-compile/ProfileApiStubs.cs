// Minimal test-only Unity and Pojlib substitutes. Never include this in a Unity build.
using System;
using System.Collections;
using System.Collections.Generic;
using System.Threading;
namespace UnityEngine
{
    public enum RuntimePlatform { Android, WindowsEditor }
    public static class Application
    {
        public static RuntimePlatform platform = RuntimePlatform.Android;
        public static string version = "0.1.0";
    }
    public static class Time
    {
        private static readonly System.Diagnostics.Stopwatch Clock =
            System.Diagnostics.Stopwatch.StartNew();
        public static float realtimeSinceStartup => (float)Clock.Elapsed.TotalSeconds;
    }
    [AttributeUsage(AttributeTargets.Field)]
    public sealed class SerializeField : Attribute { }
    public class Coroutine { }
    public class MonoBehaviour
    {
        public static readonly List<IEnumerator> Pending = new List<IEnumerator>();
        protected Coroutine StartCoroutine(IEnumerator routine)
        {
            // Unity starts the coroutine synchronously through its first yield.
            if (routine.MoveNext()) Pending.Add(routine);
            return new Coroutine();
        }
        protected static T Instantiate<T>(T prefab, Vector3 pos, Quaternion rot)
            where T : class => prefab;
        protected static void Destroy(object item) { }
    }
    public static class Debug
    {
        public static void Log(object message) { }
        public static void LogWarning(object message) { }
        public static void LogError(object message) { }
    }
    public class GameObject
    {
        public string name;
        public GameObject gameObject => this;
        public Transform transform = new Transform();
        public bool activeSelf;
        public void SetActive(bool value) { activeSelf = value; }
        public static GameObject Find(string name) => new GameObject { name = name };
        public T GetComponent<T>() where T : new() => new T();
        public T GetComponentInChildren<T>() where T : new() => new T();
    }
    public class Transform
    {
        public int childCount;
        public GameObject gameObject => new GameObject();
        public string name;
        public Transform GetChild(int index) => new Transform();
        public T GetComponent<T>() where T : new() => new T();
        public void SetParent(Transform parent, bool worldPositionStays) { }
    }
    public class RectTransform : Transform { public Vector2 sizeDelta; }
    public struct Vector2
    {
        public float x, y;
        public Vector2(float x, float y) { this.x = x; this.y = y; }
    }
    public struct Vector3
    {
        public Vector3(float x, float y, float z) { }
    }
    public struct Quaternion { public static Quaternion identity => new Quaternion(); }
    public struct Color
    {
        public float r, g, b;
        public Color(float red, float green, float blue)
        { r = red; g = green; b = blue; }
        public static Color yellow => new Color(1, 1, 0);
        public static Color green => new Color(0, 1, 0);
        public static Color Lerp(Color from, Color to, float t) => from;
    }
    public sealed class Texture2D { }
    public class AndroidJavaObject : IDisposable
    {
        public string profileName;
        public static AndroidJavaObject[] Profiles = Array.Empty<AndroidJavaObject>();
        public T Get<T>(string field)
        {
            if (field == "instanceName" || field == "versionName" ||
                field == "versionType")
                return (T)(object)profileName;
            return default(T);
        }
        public T Call<T>(string name, params object[] args)
        {
            if (name == "toArray") return (T)(object)Profiles;
            return default(T);
        }
        public void Dispose() { }
    }
    public class AndroidJavaClass : AndroidJavaObject
    {
        public static int CreateCalls, DeleteCalls, WorkerDelayMs;
        public static bool CreateSucceeds = true, DeleteSucceeds = true;
        public T CallStatic<T>(string method, params object[] args)
        {
            if (method == "createNewInstance")
            {
                Interlocked.Increment(ref CreateCalls);
                if (WorkerDelayMs > 0) Thread.Sleep(WorkerDelayMs);
                return CreateSucceeds ? (T)(object)new AndroidJavaObject() : default(T);
            }
            if (method == "deleteInstance")
            {
                Interlocked.Increment(ref DeleteCalls);
                if (WorkerDelayMs > 0) Thread.Sleep(WorkerDelayMs);
                return (T)(object)DeleteSucceeds;
            }
            return default(T);
        }
    }
    public static class AndroidJNI
    {
        public static int Attached, Detached;
        public static int AttachCurrentThread()
        {
            Interlocked.Increment(ref Attached);
            return 0;
        }
        public static void DetachCurrentThread() { Interlocked.Increment(ref Detached); }
    }
}
namespace UnityEngine.Serialization
{
    [AttributeUsage(AttributeTargets.Field)]
    public sealed class FormerlySerializedAsAttribute : Attribute
    {
        public FormerlySerializedAsAttribute(string value) { }
    }
}
namespace TMPro
{
    public class TMP_InputField { public string text; }
    public class TMP_Dropdown
    {
        public class OptionData { public string text; }
        public List<OptionData> options = new List<OptionData>();
        public int value;
        public void AddOptions(List<string> values)
        {
            foreach (string value in values) options.Add(new OptionData { text = value });
        }
    }
    public class TextMeshProUGUI { public string text; }
}
namespace UnityEngine.UI
{
    public sealed class ClickEvent { public void AddListener(Action click) { } }
    public class Button { public ClickEvent onClick = new ClickEvent(); }
    public class Toggle { public bool isOn, interactable; }
    public class RawImage { public object texture; public UnityEngine.Color color; }
}
namespace UnityEngine.EventSystems
{
    public class EventSystem
    {
        public static EventSystem current = new EventSystem();
        public UnityEngine.GameObject currentSelectedGameObject;
        public void SetSelectedGameObject(UnityEngine.GameObject selected)
        { currentSelectedGameObject = selected; }
    }
}
namespace UnityEngine.Networking
{
    public sealed class DownloadHandler { public string text; }
    public sealed class UnityWebRequest : IDisposable
    {
        public enum Result { Success, ConnectionError }
        public Result result = Result.ConnectionError;
        public string error = "offline";
        public int timeout;
        public bool isDone = true;
        public DownloadHandler downloadHandler = new DownloadHandler();
        public static UnityWebRequest Get(string url) => new UnityWebRequest();
        public static string EscapeURL(string value) => Uri.EscapeDataString(value);
        public object SendWebRequest() => null;
        public void SetRequestHeader(string header, string value) { }
        public void Dispose() { }
    }
}
namespace Newtonsoft.Json
{
    public sealed class JsonException : Exception { }
    public static class JsonConvert
    {
        public static T DeserializeObject<T>(string json) => default(T);
        public static string SerializeObject(object value) => "[]";
    }
}
public sealed class APIHandler
{
    public void DownloadImage(string url, UnityEngine.UI.RawImage image) { }
}
public sealed class WindowHandler
{
    public int Created, Deleted;
    public void InstanceCreatorUnsetter() { Created++; }
    public void InstanceInfoUnsetter() { Deleted++; }
    public void InstanceInfoSetter() { }
}
public sealed class UIHandler
{
    public string lastMessage;
    public void SetAndShowError(string text) { lastMessage = text; }
}
public sealed class ConfigHandler
{
    public sealed class Config { public string lastSelectedInstanceName; }
    public Config config = new Config();
    public bool IsLoaded => config != null;
    public int saveCount;
    public void SaveConfig() { saveCount++; }
}
public static class ProgressBarManager { public static bool started; }
public static class InstanceButton { public static bool IsInstallingInstance; }
public sealed class JNIStorage
{
    public static JNIStorage instance;
    public static UnityEngine.AndroidJavaClass apiClass;
    public static UnityEngine.AndroidJavaObject activity, instancesObj;
    public UIHandler uiHandler;
    public ConfigHandler configHandler;
    public PojlibInstance selected;
    public int refreshCount;
    public void UpdateInstances() { refreshCount++; }
    public static PojlibInstance GetInstance(string name) =>
        instance?.selected?.instanceName == name ? instance.selected : null;
}
public sealed class PojlibInstance
{
    public string instanceName, versionName, versionType, instanceImageURL;
    public UnityEngine.AndroidJavaObject raw;
    public static PojlibInstance Parse(UnityEngine.AndroidJavaObject raw) =>
        new PojlibInstance { raw = raw, instanceName = raw.Get<string>("instanceName"),
            versionName = "1.21.5" };
    public PojlibMod[] GetMods() => Array.Empty<PojlibMod>();
}
public sealed class PojlibMod { public string slug; }
public sealed class MetaParser
{
    public string slug, title, description, icon_url;
}
