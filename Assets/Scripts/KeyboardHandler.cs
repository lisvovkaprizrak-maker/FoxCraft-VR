using TMPro;
using UnityEngine;

public class KeyboardHandler : MonoBehaviour
{
    [SerializeField] private TMP_InputField searchBox;
    [SerializeField] private TMP_InputField ramSetter;
    [SerializeField] private TMP_InputField instanceEditor;
    [SerializeField] private GameObject keyboard;
    public AudioSource keyPop;
    private static bool isShift;

    // Preserve QuestCraft's original field-selection priority. A field may be
    // absent or disabled while the user changes tabs.
    private TMP_InputField ActiveField()
    {
        if (searchBox != null && searchBox.IsActive()) return searchBox;
        if (ramSetter != null && ramSetter.IsActive()) return ramSetter;
        if (instanceEditor != null && instanceEditor.IsActive()) return instanceEditor;
        return null;
    }

    private void Update()
    {
        if (keyboard == null)
            return;

        // The VR key grid is never shown over a phone's Android IME.
        if (FoxCraftDevice.IsAndroidPhone)
        {
            if (keyboard.activeSelf)
                keyboard.SetActive(false);
            return;
        }

        bool shouldShow = ActiveField() != null;
        if (keyboard.activeSelf != shouldShow)
            keyboard.SetActive(shouldShow);
    }

    private void PlayKeySound()
    {
        if (keyPop != null)
            keyPop.Play();
    }

    public void KeyPress(string key)
    {
        TMP_InputField field = ActiveField();
        if (field == null || string.IsNullOrEmpty(key))
            return;

        field.text += isShift ? key.ToUpperInvariant() : key.ToLowerInvariant();
        PlayKeySound();
    }

    public void BackspacePress()
    {
        TMP_InputField field = ActiveField();
        if (field == null || string.IsNullOrEmpty(field.text))
            return;

        field.text = field.text.Substring(0, field.text.Length - 1);
        PlayKeySound();
    }

    public void ShiftPress()
    {
        isShift = !isShift;
        PlayKeySound();
    }

    public void SpacePress()
    {
        TMP_InputField field = ActiveField();
        if (field == null)
            return;

        field.text += " ";
        PlayKeySound();
    }
}
