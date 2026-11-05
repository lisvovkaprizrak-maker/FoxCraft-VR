using System;
using System.Text;
using UnityEngine;

namespace FoxCraft.UI
{
    /// <summary>
    /// Receives explicit focus signals from a Minecraft-side integration via UnitySendMessage.
    /// It does not detect game focus itself and cannot make the game keyboard functional
    /// without a corresponding Java/Minecraft-side hook.
    /// </summary>
    [DisallowMultipleComponent]
    public sealed class FoxCraftGameFocusReceiver : MonoBehaviour
    {
        [Header("Explicit opt-in")]
        [SerializeField] private FoxCraftGameTextInputCoordinator coordinator;
        [SerializeField] private bool acceptGameFocusMessages;

        [Tooltip("Reject unreasonably large text payloads; Minecraft UI fields are normally short.")]
        [SerializeField, Min(1)] private int maxCharacters = 4096;

        private void Awake()
        {
            if (coordinator == null)
                coordinator = GetComponent<FoxCraftGameTextInputCoordinator>();
            if (coordinator == null)
            {
                Debug.LogError("FoxCraft focus receiver requires a game text coordinator.", this);
                enabled = false;
            }
        }

        /// <summary>
        /// UnitySendMessage(GameObject.name, "OpenTextField", base64 UTF-8 payload).
        /// Use RFC4648 standard Base64 (no line breaks) and an empty string for empty text.
        /// </summary>
        public void OpenTextField(string base64Utf8)
        {
            if (!acceptGameFocusMessages || !isActiveAndEnabled || coordinator == null)
                return;

            try
            {
                string text = Encoding.UTF8.GetString(Convert.FromBase64String(base64Utf8 ?? ""));
                if (text.Length > maxCharacters)
                {
                    Debug.LogWarning("FoxCraft: rejected oversized game text focus payload.", this);
                    return;
                }
                coordinator.OpenFromGame(text);
            }
            catch (FormatException)
            {
                Debug.LogWarning("FoxCraft: rejected invalid Base64 game focus payload.", this);
            }
        }

        /// <summary>UnitySendMessage(GameObject.name, "CloseTextField", "").</summary>
        public void CloseTextField(string ignored)
        {
            if (!acceptGameFocusMessages || !isActiveAndEnabled || coordinator == null)
                return;
            coordinator.CloseFromGame();
        }

        private void OnDisable()
        {
            // Never leave a text session active if its focus receiver is removed.
            if (coordinator != null && coordinator.IsSessionOpen)
                coordinator.CloseFromGame();
        }
    }
}
