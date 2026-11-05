using System;
using UnityEngine;
using UnityEngine.Events;

namespace FoxCraft.UI
{
    /// <summary>
    /// Unity-side lifecycle of a focused Minecraft text field.
    /// The Java/game bridge is intentionally external: subscribe to the four events
    /// and only call OpenFromGame/CloseFromGame from a known focused game field.
    /// This class does not claim to inject input into QuestCraft by itself.
    /// </summary>
    public sealed class FoxCraftGameTextInputCoordinator : MonoBehaviour
    {
        [Serializable] public sealed class StringEvent : UnityEvent<string> { }

        [Header("Keyboard")]
        [SerializeField] private FoxCraftKeyboard keyboard;

        [Header("Forward to a verified Java/game bridge")]
        public StringEvent OnGameTextChanged = new StringEvent();
        public StringEvent OnGameTextSubmitted = new StringEvent();
        public UnityEvent OnGameTextCancelled = new UnityEvent();
        public UnityEvent OnGameKeyboardOpened = new UnityEvent();
        public UnityEvent OnGameKeyboardClosed = new UnityEvent();

        private bool sessionOpen;
        private bool isClosing;
        private string initialText = "";
        private string lastText = "";

        public bool IsSessionOpen { get { return sessionOpen; } }
        public string CurrentText { get { return lastText; } }

        private void Awake()
        {
            if (keyboard == null) keyboard = GetComponent<FoxCraftKeyboard>();
            if (keyboard == null)
            {
                Debug.LogError("FoxCraft text input coordinator needs a FoxCraftKeyboard.", this);
                enabled = false;
            }
        }

        private void OnEnable()
        {
            if (keyboard == null) return;
            keyboard.OnTextChanged.AddListener(HandleTextChanged);
            keyboard.OnSubmitted.AddListener(HandleSubmit);
            keyboard.OnCancelled.AddListener(HandleCancel);
        }

        private void OnDisable()
        {
            if (keyboard != null)
            {
                keyboard.OnTextChanged.RemoveListener(HandleTextChanged);
                keyboard.OnSubmitted.RemoveListener(HandleSubmit);
                keyboard.OnCancelled.RemoveListener(HandleCancel);
                keyboard.Hide();
            }
            sessionOpen = false;
            isClosing = false;
        }

        /// <summary>
        /// Called only when Minecraft has an active editable field.
        /// Reopening replaces the previous unsubmitted session without submitting it.
        /// </summary>
        public void OpenFromGame(string currentValue)
        {
            if (!isActiveAndEnabled || keyboard == null) return;
            if (sessionOpen) CloseInternal();

            initialText = currentValue ?? "";
            lastText = initialText;
            sessionOpen = true;
            keyboard.ShowWithText(initialText);
            OnGameKeyboardOpened.Invoke();
        }

        /// <summary>Game no longer owns a text field: dismiss without sending text.</summary>
        public void CloseFromGame()
        {
            if (!sessionOpen) return;
            CloseInternal();
        }

        private void HandleTextChanged(string value)
        {
            if (!sessionOpen || isClosing) return;
            value = value ?? "";
            if (value == lastText) return; // Avoid duplicate snapshots on submit/cancel.
            lastText = value;
            OnGameTextChanged.Invoke(value);
        }

        private void HandleSubmit(string value)
        {
            if (!sessionOpen || isClosing) return;
            value = value ?? "";
            if (value != lastText)
            {
                lastText = value;
                OnGameTextChanged.Invoke(value);
            }

            // Drop ownership before notifying the bridge, since the game may open another field.
            CloseInternal();
            OnGameTextSubmitted.Invoke(value);
        }

        private void HandleCancel()
        {
            if (!sessionOpen || isClosing) return;

            // Keyboard.Cancel restores the original value. Explicitly signal cancellation
            // so a bridge can discard any provisional text edits.
            lastText = initialText;
            CloseInternal();
            OnGameTextCancelled.Invoke();
        }

        private void CloseInternal()
        {
            if (!sessionOpen || isClosing) return;
            isClosing = true;
            sessionOpen = false;
            keyboard.Hide();
            OnGameKeyboardClosed.Invoke();
            isClosing = false;
        }
    }
}
