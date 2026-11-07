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
    public class MonoBehaviour { }
    public class GameObject { public void SetActive(bool value) { } }
    public static class Debug
    {
        public static void Log(object message) { }
        public static void LogWarning(object message) { }
    }
    public static class Mathf
    {
        public static int Clamp(int value, int low, int high) =>
            Math.Max(low, Math.Min(value, high));
    }
    public static class SystemInfo { public static string deviceModel { get; set; } }
    [AttributeUsage(AttributeTargets.Field)]
    public sealed class SerializeField : Attribute { }
    public class AndroidJavaObject : IDisposable
    {
        public void Dispose() { }
    }
    public class AndroidJavaClass
    {
        public static int LoginCallCount;
        public void SetStatic(string name, object value) { }
        public void CallStatic(string name, params object[] parameters)
        {
            if (name == "login") LoginCallCount++;
        }
        public T CallStatic<T>(string name, params object[] parameters) => default(T);
        public T GetStatic<T>(string name) => default(T);
    }
}
namespace UnityEngine.Networking { }
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
        public void ClearOptions() => options.Clear();
        public void AddOptions(List<string> values)
        {
            foreach (string value in values) options.Add(new OptionData { text = value });
        }
        public void SetValueWithoutNotify(int selected) => value = selected;
    }
    public class TMP_InputField { public string text; public UnityEngine.GameObject gameObject = new UnityEngine.GameObject(); }
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
    public class Button { }
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
public static class FoxCraftDevice { public static bool IsVrHeadset => false; }
public class WindowHandler
{
    public void LegalSetter() { }
    public void LogoutWindowUnsetter() { }
    public void LoadAv(string name) { }
}
public class UIHandler
{
    public static int selectedInstance;
    public void UpdateDropdowns(bool initial, List<string> entries) { }
}
public class SkinHandler { }
public class JNIStorage
{
    public static JNIStorage instance;
    public static UnityEngine.AndroidJavaClass apiClass;
    public static UnityEngine.AndroidJavaObject activity;
    public static UnityEngine.AndroidJavaObject accountObj;
    public ConfigHandler configHandler;
    public UIHandler uiHandler;
    public void UpdateInstances() { }
    public TMPro.TMP_Dropdown instancesDropdown;
}
