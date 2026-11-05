using TMPro;
using UnityEngine;
using UnityEngine.EventSystems;

namespace FoxCraft.UI
{
    /// <summary>
    /// Attach to a TMP_InputField in the Unity launcher to open FoxCraftKeyboard when selected.
    /// Game-side Minecraft input requires FoxCraftGameTextInputCoordinator and a Java bridge.
    /// </summary>
    [RequireComponent(typeof(TMP_InputField))]
    public sealed class FoxCraftKeyboardFieldBinding : MonoBehaviour, ISelectHandler
    {
        [SerializeField] private FoxCraftKeyboard keyboard;
        [SerializeField] private bool hideNativeMobileKeyboard = true;

        private TMP_InputField field;

        private void Awake()
        {
            field = GetComponent<TMP_InputField>();
            if (keyboard == null) keyboard = FindObjectOfType<FoxCraftKeyboard>();
            if (keyboard == null)
                Debug.LogWarning("No FoxCraftKeyboard in scene. Assign a keyboard in the Inspector.", this);
            if (hideNativeMobileKeyboard)
                field.shouldHideMobileInput = true;
        }

        public void OnSelect(BaseEventData eventData)
        {
            if (!isActiveAndEnabled || keyboard == null) return;
            keyboard.ShowFor(field);
        }

        /// <summary>Can also be bound to a UI button's OnClick event.</summary>
        public void Open()
        {
            if (keyboard == null || field == null) return;
            keyboard.ShowFor(field);
        }
    }
}
