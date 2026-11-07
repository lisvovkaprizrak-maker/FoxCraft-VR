using System;
using System.Collections;
using System.Collections.Generic;
using Newtonsoft.Json;
using TMPro;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.Networking;

// Adds the mode selector at runtime, without modifying the existing QuestCraft
// scene/prefab bindings. The installed Minecraft version picker is reused.
public sealed class FoxCraftLaunchModeMenu : MonoBehaviour
{
    private static readonly Color Orange = new Color(1f, 0.41f, 0.06f, 1f);
    private static readonly Color Dark = new Color(0.13f, 0.14f, 0.17f, 0.96f);
    private static readonly Color White = new Color(1f, 0.96f, 0.90f, 1f);

    private UIHandler ui;
    private ConfigHandler config;
    private WindowHandler window;
    private GameObject panel;
    private GameObject modeToggle;
    private TextMeshProUGUI modeToggleLabel;
    private bool expanded;
    private Image vrBackground;
    private Image phoneBackground;
    private TextMeshProUGUI vrLabel;
    private TextMeshProUGUI phoneLabel;
    private TextMeshProUGUI detail;
    private bool? displayedPhone;
    private string displayedVersion;
    private readonly List<string> phoneVersions = new List<string>();
    private GameObject phoneVersionRow;
    private TextMeshProUGUI phoneVersionLabel;
    private bool versionsRequested;
    private bool refreshedVersionsNeedSaving;

    private sealed class MinecraftManifest
    {
        public List<MinecraftRelease> versions;
    }

    private sealed class MinecraftRelease
    {
        public string id;
        public string type;
    }

    public void Initialize(UIHandler owner)
    {
        ui = owner;
        if (!versionsRequested)
        {
            versionsRequested = true;
            StartCoroutine(LoadPhoneVersions());
        }
    }

    private void Update()
    {
        if (ui == null)
            return;

        if (panel == null)
        {
            BuildMenu();
            if (panel == null)
                return;
        }

        if (config == null)
            config = FindObjectOfType<ConfigHandler>();
        if (config != null && config.IsLoaded)
        {
            if (phoneVersions.Count == 0)
                phoneVersions.AddRange(config.GetCachedPhoneVersions());
            // The Mojang request can finish before JNIStorage.LoadConfig.
            // Persist fetched releases once settings become ready.
            if (refreshedVersionsNeedSaving && phoneVersions.Count > 0)
            {
                config.SetCachedPhoneVersions(phoneVersions);
                refreshedVersionsNeedSaving = false;
            }
        }
        if (window == null)
            window = FindObjectOfType<WindowHandler>();

        bool visible = ui.playButton != null && ui.playButton.gameObject.activeInHierarchy &&
            (window == null || window.mainPanel == null || window.mainPanel.activeInHierarchy);
        if (modeToggle != null && modeToggle.activeSelf != visible)
            modeToggle.SetActive(visible);
        bool showPanel = visible && expanded;
        if (panel.activeSelf != showPanel)
            panel.SetActive(showPanel);
        if (!visible)
            return;

        bool phone = config != null && config.IsPhoneModeSelected;
        string gameVersion = phone && config != null
            ? config.SelectedPhoneVersion : SelectedVersion();
        if (displayedPhone == phone && displayedVersion == gameVersion)
            return;

        displayedPhone = phone;
        displayedVersion = gameVersion;
        if (phoneVersionRow != null)
            phoneVersionRow.SetActive(phone);
        if (phoneVersionLabel != null)
            phoneVersionLabel.text = gameVersion;
        vrBackground.color = phone ? Dark : Orange;
        phoneBackground.color = phone ? Orange : Dark;
        if (modeToggleLabel != null)
            modeToggleLabel.text = phone ? "ТЕЛ. ▼" : "VR ▼";
        vrLabel.color = White;
        phoneLabel.color = White;
        detail.text = phone
            ? "Телефон  |  " + gameVersion + "  |  2D-движок готовится"
            : "VR  |  " + gameVersion + "  |  Quest / Pico";
    }

    private string SelectedVersion()
    {
        if (ui.dropdownMain == null || ui.dropdownMain.options == null)
            return "Minecraft";
        int index = ui.dropdownMain.value;
        return index >= 0 && index < ui.dropdownMain.options.Count
            ? ui.dropdownMain.options[index].text : "Minecraft";
    }

    private void ChooseMode(string mode)
    {
        if (config == null)
            return;
        config.SetLaunchMode(mode);
        // Keep the panel open for phone version selection; choosing VR can
        // close it immediately because the original QuestCraft list remains.
        expanded = mode == ConfigHandler.PhoneMode;
        displayedPhone = null;
    }

    private void BuildMenu()
    {
        if (ui.playButton == null)
            return;

        Canvas canvas = ui.playButton.GetComponentInParent<Canvas>();
        if (canvas == null)
            return;

        // Real QuestCraft scene: MainCanvas is 1200x600; its bottom bar
        // contains Play centered at x=600, and Mods/Options towards x=920/1141.
        // A compact toggle at x=1055 avoids existing buttons. Expand into a
        // temporary chooser over the profile grid rather than covering Play.
        modeToggle = CreateElement("FoxCraft mode toggle", canvas.transform,
            new Vector2(90f, 60f), Vector2.zero);
        RectTransform toggleRect = modeToggle.GetComponent<RectTransform>();
        toggleRect.anchorMin = new Vector2(1f, 0f);
        toggleRect.anchorMax = new Vector2(1f, 0f);
        toggleRect.anchoredPosition = new Vector2(-145f, 55f);
        modeToggle.GetComponent<Image>().color = Orange;
        Button toggleButton = modeToggle.AddComponent<Button>();
        toggleButton.onClick.AddListener(() => { expanded = !expanded; });
        modeToggleLabel = CreateText("VR ▼", modeToggle.transform,
            new Vector2(88f, 58f), Vector2.zero, 18, White);

        panel = CreateElement("FoxCraft • VR / Phone", canvas.transform,
            new Vector2(570f, 230f), Vector2.zero);
        RectTransform root = panel.GetComponent<RectTransform>();
        root.anchorMin = new Vector2(0.5f, 0.5f);
        root.anchorMax = new Vector2(0.5f, 0.5f);
        root.anchoredPosition = Vector2.zero;
        panel.GetComponent<Image>().color = new Color(0.045f, 0.05f, 0.07f, 0.98f);
        panel.GetComponent<Image>().raycastTarget = true;

        CreateText("FOXCRAFT  •  РЕЖИМ ИГРЫ", panel.transform,
            new Vector2(480f, 32f), new Vector2(35f, 92f), 23, White);
        Texture2D foxTexture = Resources.Load<Texture2D>("FoxCraftIcon");
        if (foxTexture != null)
        {
            GameObject icon = CreateElement("FoxCraft fox icon", panel.transform,
                new Vector2(42f, 42f), new Vector2(-238f, 91f));
            Image image = icon.GetComponent<Image>();
            image.sprite = Sprite.Create(foxTexture,
                new Rect(0f, 0f, foxTexture.width, foxTexture.height),
                new Vector2(0.5f, 0.5f));
            image.preserveAspect = true;
            image.raycastTarget = false;
        }

        CreateModeButton("VR", new Vector2(-137f, 35f),
            ConfigHandler.VrMode, out vrBackground, out vrLabel);
        CreateModeButton("ТЕЛЕФОН", new Vector2(137f, 35f),
            ConfigHandler.PhoneMode, out phoneBackground, out phoneLabel);
        detail = CreateText("Версия Minecraft", panel.transform, new Vector2(535f, 32f),
            new Vector2(0f, -22f), 18, White);

        phoneVersionRow = CreateElement("Phone Minecraft version", panel.transform,
            new Vector2(525f, 53f), new Vector2(0f, -80f));
        phoneVersionRow.GetComponent<Image>().color = Dark;
        phoneVersionRow.GetComponent<Image>().raycastTarget = false;
        phoneVersionLabel = CreateText("1.21.5", phoneVersionRow.transform,
            new Vector2(260f, 48f), Vector2.zero, 23, White);
        // Quick steps make old Java releases accessible without hundreds of taps.
        CreateVersionArrow("«", -228f, -10);
        CreateVersionArrow("◀", -172f, -1);
        CreateVersionArrow("▶", 172f, 1);
        CreateVersionArrow("»", 228f, 10);
        phoneVersionRow.SetActive(false);

        // Users can dismiss the chooser without changing Minecraft profiles.
        GameObject close = CreateElement("Close FoxCraft mode menu",
            panel.transform, new Vector2(42f, 42f), new Vector2(252f, 93f));
        close.GetComponent<Image>().color = Dark;
        Button closeButton = close.AddComponent<Button>();
        closeButton.onClick.AddListener(() => expanded = false);
        CreateText("×", close.transform, new Vector2(38f, 38f),
            Vector2.zero, 27, White);
        panel.SetActive(false);
    }

    private void CreateModeButton(string title, Vector2 position, string mode,
        out Image background, out TextMeshProUGUI label)
    {
        GameObject buttonObject = CreateElement("FoxCraft Mode " + mode,
            panel.transform, new Vector2(254f, 57f), position);
        background = buttonObject.GetComponent<Image>();
        background.color = Dark;
        Button button = buttonObject.AddComponent<Button>();
        button.onClick.AddListener(() => ChooseMode(mode));
        label = CreateText(title, buttonObject.transform, new Vector2(250f, 54f),
            Vector2.zero, 25, White);
    }

    private void CreateVersionArrow(string glyph, float x, int step)
    {
        GameObject arrow = CreateElement("Change phone version", phoneVersionRow.transform,
            new Vector2(50f, 48f), new Vector2(x, 0f));
        arrow.GetComponent<Image>().color = Orange;
        Button button = arrow.AddComponent<Button>();
        button.onClick.AddListener(() => StepPhoneVersion(step));
        CreateText(glyph, arrow.transform, new Vector2(46f, 46f),
            Vector2.zero, 22, White);
    }

    private void StepPhoneVersion(int step)
    {
        if (config == null || phoneVersions.Count == 0)
            return;

        int index = phoneVersions.IndexOf(config.SelectedPhoneVersion);
        int count = phoneVersions.Count;
        int destination = index < 0
            ? (step < 0 ? count - 1 : 0)
            : ((index + step) % count + count) % count;
        config.SetPhoneVersion(phoneVersions[destination]);
        displayedPhone = null;
    }

    private IEnumerator LoadPhoneVersions()
    {
        // Show actual release IDs, not the small VR-only QuestCraft profile list.
        const string manifest =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
        using (UnityWebRequest request = UnityWebRequest.Get(manifest))
        {
            request.timeout = 20;
            yield return request.SendWebRequest();
            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogWarning("[FoxCraft] Phone version list unavailable: " + request.error);
                yield break;
            }

            try
            {
                MinecraftManifest list = JsonConvert.DeserializeObject<MinecraftManifest>(
                    request.downloadHandler.text);
                if (list != null && list.versions != null)
                {
                    // Replace stale cached results instead of appending them
                    // ahead of the newest official versions.
                    List<string> latest = new List<string>();
                    foreach (MinecraftRelease entry in list.versions)
                    {
                        if (latest.Count >= ConfigHandler.MaxCachedPhoneVersions)
                            break;
                        if (entry != null && entry.type == "release" &&
                            !string.IsNullOrWhiteSpace(entry.id) && !latest.Contains(entry.id))
                            latest.Add(entry.id);
                    }
                    if (latest.Count > 0)
                    {
                        phoneVersions.Clear();
                        phoneVersions.AddRange(latest);
                        refreshedVersionsNeedSaving = true;
                    }
                }
            }
            catch (Exception ex)
            {
                Debug.LogWarning("[FoxCraft] Invalid Minecraft version list: " + ex.Message);
            }
        }

        if (config == null)
            config = FindObjectOfType<ConfigHandler>();
        if (refreshedVersionsNeedSaving && config != null && config.IsLoaded)
        {
            config.SetCachedPhoneVersions(phoneVersions);
            refreshedVersionsNeedSaving = false;
        }
        displayedPhone = null;
    }

    private static GameObject CreateElement(string name, Transform parent,
        Vector2 size, Vector2 position)
    {
        GameObject element = new GameObject(name, typeof(RectTransform), typeof(Image));
        element.transform.SetParent(parent, false);
        RectTransform rectangle = element.GetComponent<RectTransform>();
        rectangle.anchorMin = new Vector2(0.5f, 0.5f);
        rectangle.anchorMax = new Vector2(0.5f, 0.5f);
        rectangle.sizeDelta = size;
        rectangle.anchoredPosition = position;
        return element;
    }

    private static TextMeshProUGUI CreateText(string content, Transform parent,
        Vector2 size, Vector2 position, float fontSize, Color color)
    {
        GameObject textObject = new GameObject("Label", typeof(RectTransform),
            typeof(TextMeshProUGUI));
        textObject.transform.SetParent(parent, false);
        RectTransform rect = textObject.GetComponent<RectTransform>();
        rect.anchorMin = new Vector2(0.5f, 0.5f);
        rect.anchorMax = new Vector2(0.5f, 0.5f);
        rect.sizeDelta = size;
        rect.anchoredPosition = position;
        TextMeshProUGUI label = textObject.GetComponent<TextMeshProUGUI>();
        label.text = content;
        label.fontSize = fontSize;
        label.color = color;
        label.raycastTarget = false;
        label.alignment = TextAlignmentOptions.Center;
        return label;
    }
}
