// Intentionally minimal compile-time stand-ins for C# syntax/flow checks.
// NOT Unity's real APIs. This project must never be included in a Unity build.
using System;
using System.Collections;
using System.Collections.Generic;

namespace UnityEngine
{
    public enum RuntimePlatform { Android, WindowsEditor }
    public static class Application
    {
        public static RuntimePlatform platform { get; set; }
        public static string persistentDataPath { get; set; }
        public static string streamingAssetsPath { get; set; }
    }
    public class Texture2D { }
    public static class SystemInfo { public static string deviceModel { get; set; } }
    public sealed class Camera { }
    public sealed class Canvas
    {
        public Camera worldCamera { get; set; }
        public GameObject gameObject { get; set; }
        public T GetComponent<T>() where T : new() { return new T(); }
    }
    public class GameObject
    {
        public GameObject() { }
        public GameObject(string name) { }
        public T AddComponent<T>() where T : new() { return new T(); }
        public T GetComponent<T>() where T : new() { return new T(); }
        public T[] GetComponents<T>() where T : new() { return System.Array.Empty<T>(); }
        public void SetActive(bool active) { }
    }
    public static class Time { public static float realtimeSinceStartup { get; set; } }
    public class Coroutine { }
    public class MonoBehaviour
    {
        protected Coroutine StartCoroutine(IEnumerator coroutine) { return null; }
        protected static void DontDestroyOnLoad(object target) { }
        protected static T[] FindObjectsOfType<T>(bool includeInactive) { return System.Array.Empty<T>(); }
    }
    public class CanvasGroup
    {
        public GameObject gameObject { get; set; }
        public float alpha { get; set; }
    }
    public static class Debug
    {
        public static void Log(object message) { }
        public static void LogWarning(object message) { }
        public static void LogError(object message) { }
    }
    public class AndroidJavaObject : IDisposable
    {
        public AndroidJavaObject() { }
        public AndroidJavaObject(string className, params object[] args) { }
        public void Call(string method, params object[] args) { }
        public T Get<T>(string field) { return default(T); }
        public T Call<T>(string method, params object[] args) { return default(T); }
        public void Dispose() { }
    }
    public class AndroidJavaClass : AndroidJavaObject
    {
        public static int PrelaunchDelayMs;
        public static int PrelaunchCallCount;
        public AndroidJavaClass(string name) { }
        public T GetStatic<T>(string field) { return default(T); }
        public T CallStatic<T>(string method, params object[] args)
        {
            if (method == "prelaunch")
            {
                System.Threading.Interlocked.Increment(ref PrelaunchCallCount);
                if (PrelaunchDelayMs > 0)
                    System.Threading.Thread.Sleep(PrelaunchDelayMs);
            }
            return default(T);
        }
        public void CallStatic(string method, params object[] args) { }
    }
    public sealed class AndroidJavaRunnable
    {
        public AndroidJavaRunnable(Action action) { }
    }
    public class AndroidJavaProxy
    {
        protected AndroidJavaProxy(string javaInterface) { }
    }
    public static class AndroidJNI
    {
        public static int AttachCurrentThread() { return 0; }
        public static void DetachCurrentThread() { }
    }
}
namespace UnityEngine.EventSystems
{
    public class BaseInputModule
    {
        public bool enabled { get; set; }
    }
    public class EventSystem
    {
        public static EventSystem current { get; set; }
        public UnityEngine.GameObject gameObject { get; set; }
        public T GetComponent<T>() where T : new() { return new T(); }
        public T[] GetComponents<T>() where T : new() { return System.Array.Empty<T>(); }
    }
}
namespace UnityEngine.InputSystem.UI
{
    public sealed class InputSystemUIInputModule : UnityEngine.EventSystems.BaseInputModule
    {
        public void AssignDefaultActions() { }
    }
}
namespace UnityEngine.UI
{
    public sealed class GraphicRaycaster { }
}
namespace UnityEngine.Android
{
    public static class Permission
    {
        public static bool HasUserAuthorizedPermission(string name) { return true; }
        public static void RequestUserPermission(string name) { }
    }
}
namespace UnityEngine.XR { }
namespace UnityEngine.XR.Management
{
    public sealed class XRManagerSettings
    {
        public void DeinitializeLoader() { }
    }
    public sealed class XRGeneralSettings
    {
        public static XRGeneralSettings Instance { get; set; }
        public XRManagerSettings Manager { get; set; }
    }
}
namespace UnityEngine.Networking
{
    public class DownloadHandler { public byte[] data { get; set; } }
    public sealed class UnityWebRequest : IDisposable
    {
        public enum Result { Success, Failure }
        public Result result { get; set; }
        public int timeout { get; set; }
        public DownloadHandler downloadHandler { get; set; }
        public static UnityWebRequest Get(string url) { return new UnityWebRequest(); }
        public object SendWebRequest() { return null; }
        public void Dispose() { }
    }
}
namespace UnityEditor
{
    [AttributeUsage(AttributeTargets.Method)]
    public sealed class MenuItem : Attribute
    {
        public MenuItem(string itemName) { }
    }
    public enum BuildTarget { Android, StandaloneWindows }
    public enum BuildTargetGroup { Android }
    public enum AndroidArchitecture { ARM64, ARMv7 }
    public enum BuildOptions { None }
    public static class EditorUserBuildSettings
    {
        public static BuildTarget activeBuildTarget { get; set; }
        public static bool exportAsGoogleAndroidProject { get; set; }
    }
    public static class AssetDatabase
    {
        public static T LoadAssetAtPath<T>(string path) where T : class { return null; }
    }
    public static class PlayerSettings
    {
        public static UnityEngine.Texture2D[] GetIconsForTargetGroup(BuildTargetGroup group) { return Array.Empty<UnityEngine.Texture2D>(); }
        public static void SetIconsForTargetGroup(BuildTargetGroup group, UnityEngine.Texture2D[] icons) { }
        public static string GetApplicationIdentifier(BuildTargetGroup target) { return null; }
        public static void SetApplicationIdentifier(BuildTargetGroup target, string value) { }
        public static string productName { get; set; }
        public static string companyName { get; set; }
        public static string bundleVersion { get; set; }
        public static class Android
        {
            public static int bundleVersionCode { get; set; }
            public static AndroidArchitecture targetArchitectures { get; set; }
            public static bool useCustomKeystore { get; set; }
        }
    }
    public sealed class EditorBuildSettingsScene
    {
        public bool enabled { get; set; }
        public string path { get; set; }
    }
    public static class EditorBuildSettings
    {
        public static EditorBuildSettingsScene[] scenes { get; set; } = Array.Empty<EditorBuildSettingsScene>();
    }
    public sealed class BuildPlayerOptions
    {
        public string[] scenes;
        public string locationPathName;
        public BuildTarget target;
        public BuildOptions options;
    }
    public static class BuildPipeline
    {
        public static UnityEditor.Build.Reporting.BuildReport BuildPlayer(BuildPlayerOptions o)
        {
            return null;
        }
    }
}
namespace UnityEditor.Build.Reporting
{
    public enum BuildResult { Succeeded, Failed }
    public sealed class BuildSummary { public BuildResult result { get; set; } }
    public sealed class BuildReport { public BuildSummary summary { get; set; } }
}
namespace Newtonsoft.Json { }
public sealed class DropdownOption { public string text { get; set; } }
public sealed class Dropdown
{
    public int value { get; set; }
    public List<DropdownOption> options { get; set; } = new List<DropdownOption>();
}
public sealed class UIHandler
{
    public int playCalls;
    public int killCalls;
    public string lastError;
    public void PlaySetter() { playCalls++; }
    public void KillSetter() { killCalls++; }
    public void SetAndShowError(string message) { lastError = message; }
}
public sealed class ConfigHandler
{
    public bool IsPhoneModeSelected { get; set; }
    public string SelectedPhoneVersion { get; set; } = "1.21.5";
    public int lastSelectedInstance = -1;
    public void SetLastSelectedInstance(int index) { lastSelectedInstance = index; }
}
public sealed class PojlibInstance
{
    public UnityEngine.AndroidJavaObject raw;
    public string versionName;
}
public sealed class JNIStorage
{
    public static JNIStorage instance;
    public static UnityEngine.AndroidJavaClass apiClass;
    public static UnityEngine.AndroidJavaObject activity, instancesObj, accountObj;
    public Dropdown instancesDropdown;
    public UIHandler uiHandler;
    public static PojlibInstance ActiveInstance;
    public static PojlibInstance GetInstance(string name) { return ActiveInstance; }
    public void UpdateInstances() { }
}
public static class ProgressBarManager { public static bool started; }
public sealed class TweenDescriptor
{
    public TweenDescriptor setOnUpdate(Action<float> action) { return this; }
    public TweenDescriptor setOnComplete(Action action) { return this; }
}
public static class LeanTween
{
    public static TweenDescriptor value(UnityEngine.GameObject go, float from, float to, float time)
    {
        return new TweenDescriptor();
    }
}
