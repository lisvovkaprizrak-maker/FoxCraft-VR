using System;
using System.Collections;
using System.Threading;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Newtonsoft.Json;
using TMPro;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.Networking;
using UnityEngine.Serialization;
using UnityEngine.UI;

public class InstanceManager : MonoBehaviour
{
    [SerializeField] private GameObject instanceArray;
    [SerializeField] private GameObject instancePrefab;
    [SerializeField] private TextMeshProUGUI instanceVersion;
    [SerializeField] private TextMeshProUGUI instanceTitle;
    [SerializeField] private RawImage instanceImage;
    [SerializeField] private GameObject instanceRemoveMenu;
    public TMP_InputField instanceName;
    public Toggle defaultModsToggle;
    public TMP_Dropdown versionDropdown;
    public TMP_Dropdown loaderDropdown;
    public WindowHandler windowHandler;
    public Texture2D errorTexture;
    private int instanceInfoRequestId;
    // Shared across all panels. A timed-out Pojlib worker may still be
    // modifying the Minecraft directory, so a second worker must not start.
    private static ProfileWorkState activeProfileWork;
    private const float ProfileCreateTimeoutSeconds = 600f;
    private const float ProfileDeleteTimeoutSeconds = 90f;
    public static bool IsUpdatingProfiles => activeProfileWork != null;
    public static bool IsCreatingProfile =>
        activeProfileWork != null && !activeProfileWork.deleting;

    private sealed class ProfileWorkState
    {
        public volatile bool completed;
        public bool deleting;
        public bool success;
        public string error;
    }

    [FormerlySerializedAs("ModCounter")] public TextMeshProUGUI modCounter;
    [SerializeField] private GameObject modPrefab;
    [SerializeField] private GameObject modArray;
    [SerializeField] private APIHandler apiHandler;
    
    public void Start()
    {
        List<string> modLoaders = new List<string>
        {
            "Fabric",
            "Forge",
            "Quilt",
            "NeoForge"
        };
        
        loaderDropdown.AddOptions(modLoaders);
    }
    
    public void UpdateMenu()
    {
        string selectedModloader = loaderDropdown.options[loaderDropdown.value].text;
        defaultModsToggle.interactable = selectedModloader is "Fabric" or "Quilt";
        defaultModsToggle.isOn = selectedModloader is "Fabric" or "Quilt";
    }

    private void ShowProfileMessage(string message)
    {
        if (JNIStorage.instance != null && JNIStorage.instance.uiHandler != null)
            JNIStorage.instance.uiHandler.SetAndShowError(message);
        else
            Debug.LogWarning("[FoxCraft] " + message);
    }

    private bool CanMutateProfile()
    {
        if (Application.platform != RuntimePlatform.Android ||
            JNIStorage.instance == null || JNIStorage.apiClass == null ||
            JNIStorage.activity == null || JNIStorage.instancesObj == null)
        {
            ShowProfileMessage("Pojlib ещё не готов. Повторите после загрузки лаунчера.");
            return false;
        }
        if (IsUpdatingProfiles || InstanceButton.IsInstallingInstance ||
            ProgressBarManager.started)
        {
            ShowProfileMessage("Другая операция Minecraft ещё выполняется. " +
                "Дождитесь завершения перед изменением сборок.");
            return false;
        }
        return true;
    }

    private static bool IsSafeProfileName(string name)
    {
        if (string.IsNullOrWhiteSpace(name) || name.Length > 64 ||
            name == "." || name.Contains("..") || name.EndsWith("."))
            return false;
        foreach (char c in name)
        {
            if (char.IsControl(c) || c == '/' || c == '\\' ||
                c == ':' || c == '*' || c == '?' || c == '"' ||
                c == '<' || c == '>' || c == '|')
                return false;
        }
        return true;
    }

    public void CreateCustomInstance()
    {
        if (!CanMutateProfile())
            return;
        if (instanceName == null || loaderDropdown == null ||
            versionDropdown == null ||
            loaderDropdown.options == null || versionDropdown.options == null ||
            loaderDropdown.value < 0 || loaderDropdown.value >= loaderDropdown.options.Count ||
            versionDropdown.value < 0 || versionDropdown.value >= versionDropdown.options.Count)
        {
            ShowProfileMessage("Выберите версию Minecraft и загрузчик модов.");
            return;
        }

        string name = (instanceName.text ?? "").Trim();
        if (!IsSafeProfileName(name))
        {
            ShowProfileMessage("Недопустимое имя сборки. Используйте 1–64 символа " +
                "без специальных символов пути.");
            return;
        }

        try
        {
            AndroidJavaObject[] installed =
                JNIStorage.instancesObj.Call<AndroidJavaObject[]>("toArray");
            if (installed != null)
            {
                foreach (AndroidJavaObject entry in installed)
                {
                    if (entry != null && string.Equals(
                        entry.Get<string>("instanceName"), name,
                        StringComparison.OrdinalIgnoreCase))
                    {
                        ShowProfileMessage("Сборка с таким именем уже существует.");
                        return;
                    }
                }
            }
        }
        catch (Exception exception)
        {
            Debug.LogWarning("[FoxCraft] Cannot list profiles: " + exception.GetType().Name);
            ShowProfileMessage("Не удалось проверить установленные сборки.");
            return;
        }

        string version = versionDropdown.options[versionDropdown.value].text;
        string loader = loaderDropdown.options[loaderDropdown.value].text;
        if (string.IsNullOrWhiteSpace(version) || string.IsNullOrWhiteSpace(loader))
        {
            ShowProfileMessage("Укажите корректную версию Minecraft и загрузчик.");
            return;
        }
        bool installDefaults = defaultModsToggle != null && defaultModsToggle.isOn;
        StartCoroutine(RunProfileOperation(name, false, null, version, loader, installDefaults));
    }

    private IEnumerator RunProfileOperation(string name, bool deleting,
        AndroidJavaObject raw, string version, string loader, bool defaultMods)
    {
        ProfileWorkState state = new ProfileWorkState { deleting = deleting };
        activeProfileWork = state;

        // Snapshot JNI references on Unity's thread and attach the worker.
        // Downloading or deleting a profile must not block VR rendering.
        AndroidJavaClass api = JNIStorage.apiClass;
        AndroidJavaObject activity = JNIStorage.activity;
        AndroidJavaObject instances = JNIStorage.instancesObj;
        Thread worker = new Thread(() =>
        {
            bool attached = false;
            try
            {
                attached = AndroidJNI.AttachCurrentThread() >= 0;
                if (!attached)
                    throw new InvalidOperationException("Cannot attach profile worker to JVM");
                if (deleting)
                    state.success = api.CallStatic<bool>("deleteInstance", instances, raw);
                else
                {
                    using (AndroidJavaObject created =
                        api.CallStatic<AndroidJavaObject>("createNewInstance",
                            activity, instances, name, defaultMods, version, loader, null))
                    {
                        state.success = created != null;
                    }
                }
                if (!state.success)
                    state.error = deleting
                        ? "Pojlib не подтвердил удаление сборки."
                        : "Pojlib не смог создать новую сборку.";
            }
            catch (Exception exception)
            {
                state.error = "Операция Minecraft завершилась ошибкой: " +
                    exception.GetType().Name;
            }
            finally
            {
                if (attached)
                    AndroidJNI.DetachCurrentThread();
                state.completed = true;
            }
        });
        worker.IsBackground = true;
        worker.Start();

        float timeout = deleting ? ProfileDeleteTimeoutSeconds : ProfileCreateTimeoutSeconds;
        float deadline = Time.realtimeSinceStartup + timeout;
        while (!state.completed && Time.realtimeSinceStartup < deadline)
            yield return null;

        if (!state.completed)
        {
            // The Java worker cannot safely be aborted while changing files.
            // Keep blocking duplicate actions until it actually terminates.
            ShowProfileMessage("Операция Minecraft выполняется слишком долго. " +
                "Повторный запуск заблокирован, чтобы не повредить сборку.");
            while (!state.completed)
                yield return null;
        }

        if (state.error != null || !state.success)
        {
            activeProfileWork = null;
            ShowProfileMessage(state.error ?? "Pojlib не смог завершить операцию.");
            yield break;
        }

        try
        {
            ++instanceInfoRequestId; // Discard stale Modrinth results.
            if (!deleting && JNIStorage.instance != null &&
                JNIStorage.instance.configHandler != null &&
                JNIStorage.instance.configHandler.IsLoaded)
            {
                ConfigHandler preferences = JNIStorage.instance.configHandler;
                preferences.config.lastSelectedInstanceName = name;
                preferences.SaveConfig();
            }
            if (JNIStorage.instance != null)
                JNIStorage.instance.UpdateInstances();

            if (deleting)
            {
                if (instanceRemoveMenu != null)
                    instanceRemoveMenu.SetActive(false);
                if (windowHandler != null)
                    windowHandler.InstanceInfoUnsetter();
            }
            else if (windowHandler != null)
                windowHandler.InstanceCreatorUnsetter();
        }
        catch (Exception exception)
        {
            ShowProfileMessage("Сборка изменена, но список не обновился: " +
                exception.GetType().Name);
            activeProfileWork = null;
            yield break;
        }

        activeProfileWork = null;
        ShowProfileMessage(deleting
            ? "Сборка «" + name + "» удалена."
            : "Сборка «" + name + "» установлена и доступна в списке.");
    }

    public void CreateInstanceArray()
    {
        ResetArray();

        foreach (var instanceObj in JNIStorage.instancesObj.Call<AndroidJavaObject[]>("toArray"))
        {
            async Task SetInstanceData()
            {
                PojlibInstance instance = PojlibInstance.Parse(instanceObj);
                GameObject instanceGameObject = Instantiate(instancePrefab, new Vector3(-10, -10, -10), Quaternion.identity);
                instanceGameObject.transform.GetChild(1).GetComponent<TextMeshProUGUI>().text = instance.instanceName; instanceGameObject.transform.GetChild(2).GetComponent<TextMeshProUGUI>().text = instance.versionName + " - Fabric";
                instanceGameObject.transform.SetParent(instanceArray.transform, false);
                instanceGameObject.name = instance.instanceName;

                instanceGameObject.GetComponent<Button>().onClick.AddListener(delegate
                {
                    EventSystem.current.SetSelectedGameObject(instanceGameObject);
                    GameObject instanceObject = GameObject.Find(EventSystem.current.currentSelectedGameObject.transform.name);
                    CreateInstanceInfoPage(instanceObject.name);
                });

                if (instance.instanceImageURL != null)
                {
                    apiHandler.DownloadImage(instance.instanceImageURL, instanceGameObject.GetComponentInChildren<RawImage>());
                }
            }
            SetInstanceData();
        }

        if (instanceArray.transform.childCount == 0)
        {
            GameObject instanceGameObject = Instantiate(instancePrefab, new Vector3(-10, -10, -10), Quaternion.identity);
            instanceGameObject.GetComponentInChildren<RawImage>().texture = errorTexture;
            instanceGameObject.GetComponentInChildren<RawImage>().color = Color.yellow;
            instanceGameObject.transform.GetChild(1).GetComponent<TextMeshProUGUI>().text = "No instances could be found!";
            instanceGameObject.transform.GetChild(2).GetComponent<TextMeshProUGUI>().text = "Make sure you have an instance downloaded.";
            instanceGameObject.transform.SetParent(instanceArray.transform, false);
            instanceGameObject.name = "ERROR";
        }
    }

    async void CreateInstanceInfoPage(string slug)
    {
        // A slow Modrinth response must not render the previous profile's mods
        // after the user has selected another Minecraft instance.
        int requestId = ++instanceInfoRequestId;
        PojlibInstance instance = JNIStorage.GetInstance(slug);
        if (instance == null)
        {
            if (JNIStorage.instance != null && JNIStorage.instance.uiHandler != null)
                JNIStorage.instance.uiHandler.SetAndShowError(
                    "Сборка Minecraft больше не найдена. Обновите список профилей.");
            return;
        }
        windowHandler.InstanceInfoSetter();

        instanceVersion.text = instance.versionName + " - Fabric";
        instanceTitle.text = instance.instanceName;

        if (instance.instanceImageURL != null)
            apiHandler.DownloadImage(instance.instanceImageURL, instanceImage);

        for (int i = modArray.transform.childCount - 1; i >= 0; i--)
            Destroy(modArray.transform.GetChild(i).gameObject);

        PojlibMod[] modList = instance.GetMods() ?? Array.Empty<PojlibMod>();

        void CountMods(int count)
        {
            Color counterColor;
            if (count < 50)
                counterColor = Color.Lerp(new Color(3, 152, 252), new Color(255, 108, 10), (float)count / 50);
            else if (count == 69)
                counterColor = Color.green;
            else
                counterColor = Color.Lerp(new Color(255, 108, 10), new Color(255, 0, 0), (count - 50) / 50.0f);
            modCounter.text = $"Currently Installed mods: <color={ColorToHex(counterColor)}>{count}";
        }

        CountMods(modList.Length);

        List<MetaParser> fetchedMods = new();

        async Task GetModInfoArray()
        {
            // An empty instance is normal. Never call Modrinth with an
            // invalid ?ids=] query or wait indefinitely for its response.
            if (modList.Length == 0)
                return;

            List<string> slugs = new List<string>();
            foreach (PojlibMod mod in modList)
            {
                if (mod != null && !string.IsNullOrWhiteSpace(mod.slug) &&
                    !slugs.Contains(mod.slug))
                    slugs.Add(mod.slug);
            }
            if (slugs.Count == 0)
                return;

            string request = "https://api.modrinth.com/v2/projects?ids=" +
                UnityWebRequest.EscapeURL(JsonConvert.SerializeObject(slugs));
            using (UnityWebRequest www = UnityWebRequest.Get(request))
            {
                www.timeout = 15;
                www.SetRequestHeader("User-Agent", "FoxCraft-VR/" + Application.version);
                www.SendWebRequest();
                while (!www.isDone)
                    await Task.Delay(16);

                if (www.result != UnityWebRequest.Result.Success)
                {
                    Debug.LogWarning("[FoxCraft] Modrinth metadata unavailable: " + www.error);
                }
                else
                {
                    try
                    {
                        MetaParser[] response =
                            JsonConvert.DeserializeObject<MetaParser[]>(www.downloadHandler.text);
                        if (response != null)
                            fetchedMods.AddRange(response.Where(mod => mod != null &&
                                !string.IsNullOrWhiteSpace(mod.slug)));
                    }
                    catch (JsonException exception)
                    {
                        Debug.LogWarning("[FoxCraft] Invalid Modrinth metadata: " +
                            exception.GetType().Name);
                    }
                }
            }

            // Network failure / deleted Modrinth project must not hide installed
            // mods. Always render an offline record for each missing project.
            HashSet<string> fetchedSlugs = new HashSet<string>(
                fetchedMods.Select(mod => mod.slug), StringComparer.OrdinalIgnoreCase);
            foreach (PojlibMod mod in modList)
            {
                if (mod == null || string.IsNullOrWhiteSpace(mod.slug))
                    continue;
                if (fetchedSlugs.Add(mod.slug))
                {
                    fetchedMods.Add(new MetaParser
                    {
                        slug = mod.slug,
                        title = mod.slug,
                        description = "",
                        icon_url = "OFFLINE"
                    });
                }
            }
        }

        await GetModInfoArray();
        if (requestId != instanceInfoRequestId)
            return;

        //sort it
        fetchedMods = fetchedMods.OrderBy(mod => mod.title).ToList();

        foreach (MetaParser mod in fetchedMods)
        {
            GameObject modObject = Instantiate(modPrefab, new Vector3(-10, -10, -10), Quaternion.identity);
            modObject.name = mod.slug;
            modObject.transform.SetParent(modArray.transform, false);
            modObject.GetComponent<RectTransform>().sizeDelta =
                new(675, modObject.GetComponent<RectTransform>().sizeDelta.y);
            modObject.transform.GetChild(3).GetComponent<Button>().onClick.AddListener(delegate
            {
                bool deleted = JNIStorage.apiClass.CallStatic<bool>("removeExtraProject", JNIStorage.instancesObj, instance.raw, mod.slug);
                if (deleted)
                {
                    Destroy(modObject.gameObject);
                    CountMods(modArray.transform.childCount - 1);
                }
                else
                {
                    JNIStorage.instance.uiHandler.SetAndShowError("Failed to delete this mod, is it a core mod?");
                }
            });

            modObject.transform.GetChild(1).GetComponent<TextMeshProUGUI>().text = mod.title;
            modObject.transform.GetChild(2).GetComponent<TextMeshProUGUI>().text = mod.description;
            if (mod.icon_url != "OFFLINE")
            {
                apiHandler.DownloadImage(mod.icon_url, modObject.GetComponentInChildren<RawImage>());
            }
        }
    }

    string ColorToHex(Color color)
    {
        int r = (int)color.r;
        int g = (int)color.g;
        int b = (int)color.b;
        return "#" + r.ToString("X2") + g.ToString("X2") + b.ToString("X2");
    }

    public void RemoveInstancePrompt()
    {
        instanceRemoveMenu.GetComponentInChildren<TextMeshProUGUI>().text = "Pressing continue will delete this instance forever! Are you sure you want to do this?";
        instanceRemoveMenu.SetActive(true);
    }

    public void RemoveInstance()
    {
        if (!CanMutateProfile())
            return;
        string name = instanceTitle != null ? instanceTitle.text : null;
        PojlibInstance selected = JNIStorage.GetInstance(name);
        if (selected == null || selected.raw == null)
        {
            ShowProfileMessage("Сборка для удаления не найдена. Обновите список.");
            return;
        }
        StartCoroutine(RunProfileOperation(name, true, selected.raw, null, null, false));
    }

    private void ResetArray()
    {
        for (int i = instanceArray.transform.childCount - 1; i >= 0; i--)
        {
            Destroy(instanceArray.transform.GetChild(i).gameObject);
        }
    }
}