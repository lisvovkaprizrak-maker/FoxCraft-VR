using System;
using UnityEngine;

namespace FoxCraft.UI
{
    /// <summary>
    /// Experimental QuestCraft/Pojlib JNI output for an explicitly focused Minecraft GUI text field.
    /// The caller must detect real game focus and invoke coordinator.OpenFromGame().
    /// Never attach this bridge to ordinary launcher text fields.
    /// </summary>
    public sealed class FoxCraftPojlibGlfwInputBridge : MonoBehaviour
    {
        // GLFW key codes (from pojlib.input.LwjglGlfwKeycode).
        private const int KeyEscape = 256;
        private const int KeyEnter = 257;
        private const int KeyBackspace = 259;
        private const int KeyRight = 262;
        private const int KeyLeft = 263;

        [SerializeField] private FoxCraftGameTextInputCoordinator coordinator;
        [Tooltip("Enable only after a real Minecraft focused-text callback is wired and tested.")]
        [SerializeField] private bool enableNativeInput;

        private AndroidJavaClass callbackBridge;
        private bool reportedUnsupportedText;
        private bool reportedUnavailable;

        private void Awake()
        {
            if (coordinator == null)
                coordinator = GetComponent<FoxCraftGameTextInputCoordinator>();
            if (coordinator == null)
            {
                Debug.LogError("FoxCraft GLFW bridge requires a game text input coordinator.", this);
                enabled = false;
            }
        }

        private void OnEnable()
        {
            if (coordinator == null) return;
            coordinator.OnGameCharacterInserted.AddListener(SendCharacters);
            coordinator.OnGameBackspacePressed.AddListener(SendBackspace);
            coordinator.OnGameCursorLeft.AddListener(SendLeft);
            coordinator.OnGameCursorRight.AddListener(SendRight);
            coordinator.OnGameTextSubmitted.AddListener(SendSubmit);
            coordinator.OnGameTextCancelled.AddListener(SendCancel);
        }

        private void OnDisable()
        {
            if (coordinator != null)
            {
                coordinator.OnGameCharacterInserted.RemoveListener(SendCharacters);
                coordinator.OnGameBackspacePressed.RemoveListener(SendBackspace);
                coordinator.OnGameCursorLeft.RemoveListener(SendLeft);
                coordinator.OnGameCursorRight.RemoveListener(SendRight);
                coordinator.OnGameTextSubmitted.RemoveListener(SendSubmit);
                coordinator.OnGameTextCancelled.RemoveListener(SendCancel);
            }
            if (callbackBridge != null)
            {
                callbackBridge.Dispose();
                callbackBridge = null;
            }
        }

        private bool EnsureReady()
        {
            if (!enableNativeInput || Application.platform != RuntimePlatform.Android ||
                coordinator == null || !coordinator.isActiveAndEnabled)
                return false;
            if (callbackBridge != null) return true;

            try
            {
                // Existing Pojlib-release.aar includes the org.lwjgl.glfw namespace.
                // Exact Unity JNI signatures still require a real device smoke test.
                callbackBridge = new AndroidJavaClass("org.lwjgl.glfw.CallbackBridge");
                return true;
            }
            catch (Exception ex)
            {
                DisableAfterFailure(ex);
                return false;
            }
        }

        private void SendCharacters(string value)
        {
            if (string.IsNullOrEmpty(value) || !EnsureReady()) return;

            // Current JNI API accepts Java char (UTF-16 code unit), not Unicode int.
            // Do not split supplementary characters into broken surrogate events.
            foreach (char ch in value)
                if (char.IsSurrogate(ch))
                {
                    if (!reportedUnsupportedText)
                    {
                        Debug.LogWarning("FoxCraft: supplementary Unicode characters are not supported by the current Pojlib char-input bridge.", this);
                        reportedUnsupportedText = true;
                    }
                    return;
                }

            try
            {
                foreach (char ch in value)
                    callbackBridge.CallStatic("sendChar", ch, 0);
            }
            catch (Exception ex)
            {
                DisableAfterFailure(ex);
            }
        }

        private void SendBackspace() { SendKey(KeyBackspace); }
        private void SendLeft() { SendKey(KeyLeft); }
        private void SendRight() { SendKey(KeyRight); }
        private void SendSubmit(string text) { SendKey(KeyEnter); }
        private void SendCancel() { SendKey(KeyEscape); }

        private void SendKey(int key)
        {
            if (!EnsureReady()) return;
            try
            {
                callbackBridge.CallStatic("sendKeyPress", key);
            }
            catch (Exception ex)
            {
                DisableAfterFailure(ex);
            }
        }

        private void DisableAfterFailure(Exception ex)
        {
            if (!reportedUnavailable)
            {
                Debug.LogError("FoxCraft: Pojlib GLFW input bridge unavailable. Native injection disabled: " + ex, this);
                reportedUnavailable = true;
            }
            enableNativeInput = false;
            if (callbackBridge != null)
            {
                callbackBridge.Dispose();
                callbackBridge = null;
            }
        }
    }
}
