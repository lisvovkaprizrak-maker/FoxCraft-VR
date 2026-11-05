using System;
using TMPro;
using UnityEngine;
using UnityEngine.Events;
using UnityEngine.UI;
using UnityEngine.XR.Interaction.Toolkit.UI;

namespace FoxCraft.UI
{
    /// <summary>
    /// World-space VR keyboard for Unity launcher fields.
    /// Does not inject text into the Minecraft Java runtime: connect OnTextChanged/OnSubmitted
    /// to the game's existing input bridge separately.
    /// </summary>
    public sealed class FoxCraftKeyboard : MonoBehaviour
    {
        [Serializable] public sealed class TextEvent : UnityEvent<string> { }

        [Header("Optional configuration")]
        [SerializeField] private TMP_FontAsset font;
        [SerializeField] private Transform placementAnchor;
        [SerializeField] private Vector3 localOffset = new Vector3(0f, -0.18f, 0.55f);
        [SerializeField] private float worldScale = 0.0008f;
        [Tooltip("Leave disabled for a stationary floating keyboard. Enable to follow head movement.")]
        [SerializeField] private bool followHeadMovement;
        [SerializeField] private bool showOnStart;

        [Header("Events (use these for your Minecraft input adapter)")]
        public TextEvent OnTextChanged = new TextEvent();
        public TextEvent OnSubmitted = new TextEvent();
        public UnityEvent OnCancelled = new UnityEvent();

        // Discrete edit operations are required for GLFW input. Text snapshots alone
        // cannot accurately describe edits at the cursor or duplicated characters.
        public TextEvent OnCharacterInserted = new TextEvent();
        public UnityEvent OnBackspacePressed = new UnityEvent();
        public UnityEvent OnCursorLeft = new UnityEvent();
        public UnityEvent OnCursorRight = new UnityEvent();

        private enum Language { English, Russian }
        private Language language = Language.English;
        private bool shift;
        private bool symbols;
        private TMP_InputField target;
        private Canvas canvas;
        private RectTransform keyboardPanel;
        private TextMeshProUGUI previewText;
        private string originalText = "";
        private string buffer = "";
        private int cursor;
        private bool built;
        private bool gameTextMode;

        private static readonly string[][] English =
        {
            new[] { "q", "w", "e", "r", "t", "y", "u", "i", "o", "p" },
            new[] { "a", "s", "d", "f", "g", "h", "j", "k", "l" },
            new[] { "z", "x", "c", "v", "b", "n", "m" }
        };

        private static readonly string[][] Russian =
        {
            new[] { "й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х" },
            new[] { "ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э" },
            new[] { "я", "ч", "с", "м", "и", "т", "ь", "б", "ю" }
        };

        private static readonly string[][] Symbols =
        {
            new[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" },
            new[] { "@", "#", "$", "%", "&", "-", "+", "(", ")", "/" },
            new[] { "*", "\"", "'", ":", ";", "!", "?", ".", ",", "_" }
        };

        private static readonly Color Background = new Color(0.055f, 0.071f, 0.12f, 0.97f);
        private static readonly Color KeyColor = new Color(0.13f, 0.17f, 0.24f, 1f);
        private static readonly Color UtilityColor = new Color(0.20f, 0.25f, 0.32f, 1f);
        private static readonly Color AccentColor = new Color(1f, 0.48f, 0.17f, 1f);
        private static readonly Color HoverColor = new Color(1f, 0.63f, 0.32f, 1f);
        private static Sprite roundedSprite;

        private void Awake()
        {
            Build();
            canvas.gameObject.SetActive(showOnStart);
        }

        /// <summary>Open the keyboard for a TextMeshPro input field in the Unity launcher.</summary>
        public void ShowFor(TMP_InputField input)
        {
            if (input == null || gameTextMode) return;
            if (!built) Build();
            target = input;
            gameTextMode = false;
            buffer = input != null ? (input.text ?? "") : "";
            originalText = buffer;
            cursor = Mathf.Clamp(input != null ? input.caretPosition : buffer.Length, 0, buffer.Length);
            Refresh();
            Position();
            canvas.gameObject.SetActive(true);
        }

        /// <summary>Open without a Unity input field; consume OnTextChanged/OnSubmitted externally.</summary>
        public void ShowWithText(string initialText)
        {
            if (!built) Build();
            target = null;
            gameTextMode = true;
            buffer = initialText ?? "";
            originalText = buffer;
            cursor = buffer.Length;
            Refresh();
            Position();
            canvas.gameObject.SetActive(true);
        }

        public void Hide()
        {
            if (canvas != null) canvas.gameObject.SetActive(false);
            gameTextMode = false;
            target = null;
        }

        public string CurrentText { get { return buffer; } }
        public bool IsVisible { get { return canvas != null && canvas.gameObject.activeInHierarchy; } }
        public bool IsGameTextMode { get { return gameTextMode && IsVisible; } }

        /// <summary>Whether an EventSystem selection belongs to the generated keyboard.</summary>
        public bool ContainsUIObject(GameObject selectedObject)
        {
            return selectedObject != null && canvas != null &&
                   selectedObject.transform.IsChildOf(canvas.transform);
        }

        private void Position()
        {
            Camera eventCamera = Camera.main;
            if (placementAnchor != null)
            {
                Camera anchorCamera = placementAnchor.GetComponent<Camera>();
                if (anchorCamera != null) eventCamera = anchorCamera;
            }

            if (canvas != null && eventCamera != null)
                canvas.worldCamera = eventCamera;

            Transform anchor = placementAnchor != null ? placementAnchor :
                (eventCamera != null ? eventCamera.transform : null);
            if (anchor == null)
            {
                Debug.LogWarning("FoxCraft keyboard: no placement anchor or MainCamera. Assign one to show the panel in VR.", this);
                return;
            }

            // Fixed in world space by default: looking around should not pull a
            // large UI panel along with the headset. 'followHeadMovement' is opt-in.
            canvas.transform.SetParent(followHeadMovement ? anchor : transform, false);
            canvas.transform.position = anchor.TransformPoint(localOffset);
            Vector3 towardUser = anchor.position - canvas.transform.position;
            if (towardUser.sqrMagnitude > 0.00001f)
                canvas.transform.rotation = Quaternion.LookRotation(towardUser, anchor.up);
            else
                canvas.transform.rotation = anchor.rotation * Quaternion.Euler(0f, 180f, 0f);
            canvas.transform.localScale = Vector3.one * Mathf.Max(0.0001f, worldScale);
        }

        private void Build()
        {
            if (built) return;
            GameObject root = new GameObject("FoxCraft Keyboard Canvas", typeof(RectTransform),
                typeof(Canvas), typeof(TrackedDeviceGraphicRaycaster));
            root.transform.SetParent(transform, false);
            canvas = root.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.WorldSpace;
            canvas.worldCamera = Camera.main;
            RectTransform rect = root.GetComponent<RectTransform>();
            rect.sizeDelta = new Vector2(1120f, 480f);
            root.transform.localScale = Vector3.one * Mathf.Max(0.0001f, worldScale);

            keyboardPanel = CreatePanel(root.transform, "Keyboard", Background);
            keyboardPanel.anchorMin = Vector2.zero;
            keyboardPanel.anchorMax = Vector2.one;
            keyboardPanel.offsetMin = Vector2.zero;
            keyboardPanel.offsetMax = Vector2.zero;
            VerticalLayoutGroup vertical = keyboardPanel.gameObject.AddComponent<VerticalLayoutGroup>();
            vertical.padding = new RectOffset(22, 22, 22, 22);
            vertical.spacing = 9;
            vertical.childControlWidth = true;
            vertical.childControlHeight = true;
            vertical.childForceExpandHeight = false;

            CreatePreviewRow();

            built = true;
            Refresh();
        }

        private void Refresh()
        {
            if (!built) return;
            for (int i = keyboardPanel.childCount - 1; i >= 1; --i)
            {
                GameObject oldRow = keyboardPanel.GetChild(i).gameObject;
                oldRow.SetActive(false);
                Destroy(oldRow);
            }

            CreateRow("Header", new[] {
                new KeySpec("FOXCRAFT", null, 2f, UtilityColor),
                new KeySpec(language == Language.Russian ? "РУС" : "ENG", ToggleLanguage, 1f, AccentColor),
                new KeySpec(symbols ? "АБВ" : "123", ToggleSymbols, 1f, UtilityColor),
                new KeySpec("✕", Cancel, 0.7f, UtilityColor)
            });

            string[][] mapping = symbols ? Symbols : (language == Language.Russian ? Russian : English);
            foreach (string[] row in mapping)
            {
                KeySpec[] keys = new KeySpec[row.Length];
                for (int i = 0; i < row.Length; ++i)
                {
                    string key = row[i];
                    string character = shift ? key.ToUpperInvariant() : key;
                    keys[i] = new KeySpec(character, () => Insert(character), 1f, KeyColor);
                }
                CreateRow("Letters", keys);
            }

            if (previewText != null) previewText.text = string.IsNullOrEmpty(buffer) ? "FoxCraft  •  Введите текст / Type here" : buffer;

            CreateRow("Actions", new[] {
                new KeySpec(shift ? "SHIFT ●" : "SHIFT", ToggleShift, 1.3f, UtilityColor),
                new KeySpec("←", MoveLeft, 0.9f, UtilityColor),
                new KeySpec(language == Language.Russian ? "ПРОБЕЛ" : "SPACE", () => Insert(" "), 4.2f, KeyColor),
                new KeySpec("→", MoveRight, 0.9f, UtilityColor),
                new KeySpec("⌫", Backspace, 1.1f, UtilityColor),
                new KeySpec(language == Language.Russian ? "ВВОД" : "ENTER", Submit, 1.5f, AccentColor)
            });
        }


        private void CreatePreviewRow()
        {
            GameObject row = new GameObject("Text preview", typeof(RectTransform), typeof(Image), typeof(LayoutElement));
            row.transform.SetParent(keyboardPanel, false);
            Image image = row.GetComponent<Image>();
            image.color = new Color(0.09f, 0.10f, 0.17f, 1f);
            image.sprite = RoundedSprite();
            image.type = Image.Type.Sliced;
            row.GetComponent<LayoutElement>().preferredHeight = 62f;
            row.GetComponent<LayoutElement>().minHeight = 62f;

            GameObject textObject = new GameObject("Text", typeof(RectTransform), typeof(TextMeshProUGUI));
            textObject.transform.SetParent(row.transform, false);
            RectTransform rect = textObject.GetComponent<RectTransform>();
            rect.anchorMin = Vector2.zero;
            rect.anchorMax = Vector2.one;
            rect.offsetMin = new Vector2(18f, 5f);
            rect.offsetMax = new Vector2(-18f, -5f);
            previewText = textObject.GetComponent<TextMeshProUGUI>();
            if (font != null) previewText.font = font;
            previewText.fontSize = 29f;
            previewText.alignment = TextAlignmentOptions.MidlineLeft;
            previewText.overflowMode = TextOverflowModes.Ellipsis;
            previewText.color = Color.white;
            previewText.raycastTarget = false;
        }

        private static Sprite RoundedSprite()
        {
            if (roundedSprite != null) return roundedSprite;
            const int size = 32;
            const float radius = 9f;
            Texture2D texture = new Texture2D(size, size, TextureFormat.RGBA32, false);
            texture.filterMode = FilterMode.Bilinear;
            texture.wrapMode = TextureWrapMode.Clamp;
            Color32[] pixels = new Color32[size * size];
            for (int y = 0; y < size; ++y)
                for (int x = 0; x < size; ++x)
                {
                    float dx = Mathf.Max(0f, Mathf.Abs(x + 0.5f - size / 2f) - (size / 2f - radius));
                    float dy = Mathf.Max(0f, Mathf.Abs(y + 0.5f - size / 2f) - (size / 2f - radius));
                    float alpha = Mathf.Clamp01(radius - Mathf.Sqrt(dx * dx + dy * dy) + 0.5f);
                    pixels[y * size + x] = new Color(1f, 1f, 1f, alpha);
                }
            texture.SetPixels32(pixels);
            texture.Apply();
            roundedSprite = Sprite.Create(texture, new Rect(0, 0, size, size), new Vector2(0.5f, 0.5f), 100f, 0, SpriteMeshType.FullRect, new Vector4(10, 10, 10, 10));
            return roundedSprite;
        }

        private struct KeySpec
        {
            public string Label;
            public Action Action;
            public float Weight;
            public Color Background;
            public KeySpec(string label, Action action, float weight, Color background)
            { Label = label; Action = action; Weight = weight; Background = background; }
        }

        private void CreateRow(string name, KeySpec[] keys)
        {
            GameObject row = new GameObject(name, typeof(RectTransform));
            row.transform.SetParent(keyboardPanel, false);
            HorizontalLayoutGroup layout = row.AddComponent<HorizontalLayoutGroup>();
            layout.spacing = 7f;
            layout.childControlHeight = true;
            layout.childControlWidth = true;
            layout.childForceExpandWidth = true;
            layout.childForceExpandHeight = true;
            LayoutElement rowHeight = row.AddComponent<LayoutElement>();
            rowHeight.flexibleHeight = 1f;

            foreach (KeySpec key in keys)
            {
                GameObject buttonObject = new GameObject("Key " + key.Label, typeof(RectTransform), typeof(Image), typeof(Button), typeof(LayoutElement));
                buttonObject.transform.SetParent(row.transform, false);
                Image keyImage = buttonObject.GetComponent<Image>();
                // ColorTint multiplies Image.color. Keep the base white so the
                // normal/hover/pressed palette has the intended appearance.
                keyImage.color = Color.white;
                keyImage.sprite = RoundedSprite();
                keyImage.type = Image.Type.Sliced;
                buttonObject.GetComponent<LayoutElement>().flexibleWidth = key.Weight;
                Button button = buttonObject.GetComponent<Button>();
                ColorBlock colors = button.colors;
                colors.normalColor = key.Background;
                colors.highlightedColor = Color.Lerp(key.Background, HoverColor, 0.65f);
                colors.pressedColor = AccentColor;
                colors.selectedColor = Color.Lerp(key.Background, HoverColor, 0.65f);
                colors.disabledColor = key.Background;
                colors.fadeDuration = 0.08f;
                button.transition = Selectable.Transition.ColorTint;
                button.colors = colors;
                if (key.Action != null)
                {
                    Action callback = key.Action;
                    button.onClick.AddListener(() => callback());
                }
                else button.interactable = false;

                GameObject labelObject = new GameObject("Label", typeof(RectTransform), typeof(TextMeshProUGUI));
                labelObject.transform.SetParent(buttonObject.transform, false);
                RectTransform labelRect = labelObject.GetComponent<RectTransform>();
                labelRect.anchorMin = Vector2.zero;
                labelRect.anchorMax = Vector2.one;
                labelRect.offsetMin = Vector2.zero;
                labelRect.offsetMax = Vector2.zero;
                TextMeshProUGUI label = labelObject.GetComponent<TextMeshProUGUI>();
                label.text = key.Label;
                if (font != null) label.font = font;
                label.fontSize = 25f;
                label.fontSizeMin = 15f;
                label.enableAutoSizing = true;
                label.alignment = TextAlignmentOptions.Center;
                label.color = Color.white;
                label.raycastTarget = false;
            }
        }

        private static RectTransform CreatePanel(Transform parent, string name, Color color)
        {
            GameObject panel = new GameObject(name, typeof(RectTransform), typeof(Image));
            panel.transform.SetParent(parent, false);
            Image image = panel.GetComponent<Image>();
            image.color = color;
            image.sprite = RoundedSprite();
            image.type = Image.Type.Sliced;
            return panel.GetComponent<RectTransform>();
        }

        private void Commit()
        {
            if (target != null)
            {
                target.text = buffer;
                target.caretPosition = cursor;
            }
            if (previewText != null) previewText.text = string.IsNullOrEmpty(buffer) ? "FoxCraft  •  Введите текст / Type here" : buffer;
            OnTextChanged.Invoke(buffer);
        }

        public void Insert(string value)
        {
            if (!IsVisible || string.IsNullOrEmpty(value)) return;
            // The present Pojlib GLFW callback accepts only UTF-16 char units.
            // Reject unsupported supplementary characters before updating the
            // local preview, rather than showing text the game never received.
            if (gameTextMode)
            {
                foreach (char unit in value)
                {
                    if (char.IsSurrogate(unit))
                    {
                        Debug.LogWarning("FoxCraft: supplementary Unicode is not supported by the active game input bridge.", this);
                        return;
                    }
                }
            }
            buffer = buffer.Insert(cursor, value);
            cursor += value.Length;
            OnCharacterInserted.Invoke(value);
            Commit();
            if (shift)
            {
                shift = false;
                Refresh();
            }
        }

        public void Backspace()
        {
            if (!IsVisible || cursor == 0) return;
            // Keep surrogate pairs (emoji and supplementary code points) intact.
            int length = cursor >= 2 && char.IsLowSurrogate(buffer[cursor - 1]) &&
                char.IsHighSurrogate(buffer[cursor - 2]) ? 2 : 1;
            buffer = buffer.Remove(cursor - length, length);
            cursor -= length;
            OnBackspacePressed.Invoke();
            Commit();
        }

        public void MoveLeft()
        {
            if (!IsVisible || cursor == 0) return;
            cursor -= cursor >= 2 && char.IsLowSurrogate(buffer[cursor - 1]) &&
                char.IsHighSurrogate(buffer[cursor - 2]) ? 2 : 1;
            OnCursorLeft.Invoke();
            Commit();
        }

        public void MoveRight()
        {
            if (!IsVisible || cursor >= buffer.Length) return;
            cursor += cursor + 1 < buffer.Length && char.IsHighSurrogate(buffer[cursor]) &&
                char.IsLowSurrogate(buffer[cursor + 1]) ? 2 : 1;
            OnCursorRight.Invoke();
            Commit();
        }

        public void Submit()
        {
            if (!IsVisible) return;
            Commit();
            OnSubmitted.Invoke(buffer);
            Hide();
        }

        public void Cancel()
        {
            if (!IsVisible) return;
            if (target != null)
            {
                target.text = originalText;
                target.caretPosition = Mathf.Clamp(cursor, 0, originalText.Length);
            }
            buffer = originalText;
            // The game input handler owns its own edit state: cancellation sends
            // Escape, not a synthetic text-replacement snapshot.
            if (!gameTextMode) OnTextChanged.Invoke(buffer);
            OnCancelled.Invoke();
            Hide();
        }

        public void ToggleLanguage()
        {
            language = language == Language.English ? Language.Russian : Language.English;
            Refresh();
        }

        public void ToggleSymbols()
        {
            symbols = !symbols;
            Refresh();
        }

        public void ToggleShift()
        {
            shift = !shift;
            Refresh();
        }
    }
}
