using TMPro;
using UnityEngine;
using UnityEngine.UI;

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
    private Image vrBackground;
    private Image phoneBackground;
    private TextMeshProUGUI vrLabel;
    private TextMeshProUGUI phoneLabel;
    private TextMeshProUGUI detail;
    private bool? displayedPhone;
    private string displayedVersion;

    public void Initialize(UIHandler owner)
    {
        ui = owner;
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
        if (window == null)
            window = FindObjectOfType<WindowHandler>();

        bool visible = ui.playButton != null && ui.playButton.gameObject.activeInHierarchy &&
            (window == null || window.mainPanel == null || window.mainPanel.activeInHierarchy);
        if (panel.activeSelf != visible)
            panel.SetActive(visible);
        if (!visible)
            return;

        bool phone = config != null && config.IsPhoneModeSelected;
        string gameVersion = SelectedVersion();
        if (displayedPhone == phone && displayedVersion == gameVersion)
            return;

        displayedPhone = phone;
        displayedVersion = gameVersion;
        vrBackground.color = phone ? Dark : Orange;
        phoneBackground.color = phone ? Orange : Dark;
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
        displayedPhone = null;
    }

    private void BuildMenu()
    {
        if (ui.playButton == null)
            return;

        Canvas canvas = ui.playButton.GetComponentInParent<Canvas>();
        if (canvas == null)
            return;

        panel = CreateElement("FoxCraft • VR / Phone", canvas.transform,
            new Vector2(570f, 145f), new Vector2(0f, 0f));
        RectTransform root = panel.GetComponent<RectTransform>();
        root.anchorMin = new Vector2(0.5f, 0f);
        root.anchorMax = new Vector2(0.5f, 0f);
        root.anchoredPosition = new Vector2(0f, 125f);
        panel.GetComponent<Image>().color = new Color(0.045f, 0.05f, 0.07f, 0.95f);

        CreateText("РЕЖИМ ИГРЫ", panel.transform, new Vector2(530f, 30f),
            new Vector2(0f, 55f), 23, White);

        CreateModeButton("VR", new Vector2(-137f, 4f),
            ConfigHandler.VrMode, out vrBackground, out vrLabel);
        CreateModeButton("ТЕЛЕФОН", new Vector2(137f, 4f),
            ConfigHandler.PhoneMode, out phoneBackground, out phoneLabel);
        detail = CreateText("Версия Minecraft", panel.transform, new Vector2(535f, 32f),
            new Vector2(0f, -52f), 18, White);
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
