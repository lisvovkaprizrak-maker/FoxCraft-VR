using FoxCraft.UI;
using TMPro;
using UnityEngine;
using UnityEngine.EventSystems;

/// <summary>
/// Compatibility adapter for the existing QuestCraft launcher scene.
/// The old serialized keyboard buttons keep working as a fallback; by default,
/// this handler now opens the FoxCraft world-space keyboard for known input fields.
/// </summary>
public class KeyboardHandler : MonoBehaviour
{
    [SerializeField] private TMP_InputField searchBox;
    [SerializeField] private TMP_InputField ramSetter;
    [SerializeField] private TMP_InputField instanceEditor;
    [SerializeField] private GameObject keyboard;
    public AudioSource keyPop;

    [Header("FoxCraft keyboard")]
    [SerializeField] private bool preferFoxCraftKeyboard = true;
    [SerializeField] private FoxCraftKeyboard foxCraftKeyboard;

    private TMP_InputField activeField;
    private TMP_InputField dismissedField;
    private bool oldShift;

    private void Awake()
    {
        if (!preferFoxCraftKeyboard) return;

        if (foxCraftKeyboard == null)
            foxCraftKeyboard = GetComponent<FoxCraftKeyboard>();
        if (foxCraftKeyboard == null)
            foxCraftKeyboard = gameObject.AddComponent<FoxCraftKeyboard>();

        // Hide the old prefab, NOT its containing controller GameObject.
        if (keyboard != null && keyboard != gameObject)
            keyboard.SetActive(false);
    }

    private void Update()
    {
        if (preferFoxCraftKeyboard && foxCraftKeyboard != null)
        {
            if (keyboard != null && keyboard != gameObject && keyboard.activeSelf)
                keyboard.SetActive(false);

            if (foxCraftKeyboard.IsGameTextMode)
                return; // Do not let launcher fields steal a game editing session.

            if (activeField != null &&
                (!foxCraftKeyboard.IsVisible || !activeField.gameObject.activeInHierarchy))
            {
                foxCraftKeyboard.Hide();
                dismissedField = activeField;
                activeField = null;
            }

            TMP_InputField selectedField = GetFocusedField();
            GameObject selectedObject = EventSystem.current != null
                ? EventSystem.current.currentSelectedGameObject : null;
            bool selectedKeyboardKey = foxCraftKeyboard.ContainsUIObject(selectedObject);

            // Clicking a key changes EventSystem selection away from the input
            // field. Keep editing while the selected button belongs to FoxCraft.
            if (selectedField == null && selectedKeyboardKey)
                return;

            // Clicking outside both the keyboard and the input fields closes it.
            if (selectedField == null && selectedObject != null && !selectedKeyboardKey)
            {
                if (activeField != null)
                {
                    foxCraftKeyboard.Hide();
                    dismissedField = activeField;
                    activeField = null;
                }
                return;
            }

            if (selectedField == null)
            {
                dismissedField = null;
                return;
            }

            if (selectedField != activeField && selectedField != dismissedField)
            {
                activeField = selectedField;
                foxCraftKeyboard.ShowFor(selectedField);
            }
            return;
        }

        // Legacy fallback: use actual focus rather than IsActive(), which tests
        // whether an input component is enabled, not whether the player selected it.
        if (keyboard != null && keyboard != gameObject)
            keyboard.SetActive(GetFocusedField() != null);
    }

    private TMP_InputField GetFocusedField()
    {
        TMP_InputField focused = null;
        if (EventSystem.current != null &&
            EventSystem.current.currentSelectedGameObject != null)
            focused = EventSystem.current.currentSelectedGameObject.GetComponent<TMP_InputField>();

        if (IsKnownField(focused)) return focused;
        // A different selected GameObject takes priority over stale TMP.isFocused
        // state (which may remain true briefly after a button click).
        if (EventSystem.current != null &&
            EventSystem.current.currentSelectedGameObject != null)
            return null;
        if (searchBox != null && searchBox.isFocused) return searchBox;
        if (ramSetter != null && ramSetter.isFocused) return ramSetter;
        if (instanceEditor != null && instanceEditor.isFocused) return instanceEditor;
        return null;
    }

    private bool IsKnownField(TMP_InputField field)
    {
        return field != null && field.gameObject.activeInHierarchy &&
               (field == searchBox || field == ramSetter || field == instanceEditor);
    }

    private TMP_InputField CurrentField()
    {
        return activeField != null && activeField.gameObject.activeInHierarchy
            ? activeField : GetFocusedField();
    }

    private void Pop()
    {
        if (keyPop != null) keyPop.Play();
    }

    // These names are referenced by existing prefab button UnityEvents.
    public void KeyPress(string key)
    {
        if (string.IsNullOrEmpty(key)) return;
        if (preferFoxCraftKeyboard && foxCraftKeyboard != null && foxCraftKeyboard.IsVisible)
        {
            foxCraftKeyboard.Insert(key);
        }
        else
        {
            TMP_InputField target = CurrentField();
            if (target == null) return;
            target.text += oldShift ? key.ToUpperInvariant() : key.ToLowerInvariant();
        }
        Pop();
    }

    public void BackspacePress()
    {
        if (preferFoxCraftKeyboard && foxCraftKeyboard != null && foxCraftKeyboard.IsVisible)
        {
            foxCraftKeyboard.Backspace();
        }
        else
        {
            TMP_InputField target = CurrentField();
            if (target == null || string.IsNullOrEmpty(target.text)) return;
            target.text = target.text.Remove(target.text.Length - 1, 1);
        }
        Pop();
    }

    public void ShiftPress()
    {
        if (preferFoxCraftKeyboard && foxCraftKeyboard != null && foxCraftKeyboard.IsVisible)
            foxCraftKeyboard.ToggleShift();
        else
            oldShift = !oldShift;
        Pop();
    }

    public void SpacePress()
    {
        if (preferFoxCraftKeyboard && foxCraftKeyboard != null && foxCraftKeyboard.IsVisible)
        {
            foxCraftKeyboard.Insert(" ");
        }
        else
        {
            TMP_InputField target = CurrentField();
            if (target == null) return;
            target.text += " ";
        }
        Pop();
    }
}
