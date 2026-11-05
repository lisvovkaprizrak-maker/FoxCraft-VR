using System.Collections.Generic;
using FoxCraft.UI;
using TMPro;
using UnityEditor;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.XR.Interaction.Toolkit.UI;

namespace FoxCraft.Editor
{
    /// <summary>Non-mutating scene check. Does not run a headset or compile an APK.</summary>
    public static class FoxCraftKeyboardValidator
    {
        [MenuItem("Tools/FoxCraft/Validate Keyboard Scene")]
        public static void Validate()
        {
            List<string> warnings = new List<string>();
            List<string> success = new List<string>();

            KeyboardHandler[] handlers = Object.FindObjectsOfType<KeyboardHandler>(true);
            if (handlers.Length == 0)
                warnings.Add("No legacy KeyboardHandler exists in the currently loaded scene.");
            else
                success.Add("Found " + handlers.Length + " launcher keyboard handler(s).");

            EventSystem[] systems = Object.FindObjectsOfType<EventSystem>(true);
            if (systems.Length != 1)
                warnings.Add("Expected one EventSystem in the loaded scene; found " + systems.Length + ".");
            else
            {
                XRUIInputModule module = systems[0].GetComponent<XRUIInputModule>();
                if (module == null)
                    warnings.Add("EventSystem is missing XRUIInputModule for tracked VR controller UI.");
                else
                    success.Add("XRUIInputModule exists.");
            }

            Camera camera = Camera.main;
            if (camera == null)
                warnings.Add("No tagged MainCamera found. Assign a placement anchor to FoxCraftKeyboard.");
            else
                success.Add("Tagged MainCamera found: " + camera.name);

            FoxCraftKeyboard[] keyboards = Object.FindObjectsOfType<FoxCraftKeyboard>(true);
            if (keyboards.Length == 0)
                warnings.Add("No FoxCraftKeyboard component in the loaded scene. KeyboardHandler creates one at runtime if enabled; verify in Play Mode.");
            else
            {
                success.Add("Found " + keyboards.Length + " custom keyboard component(s).");
                foreach (FoxCraftKeyboard keyboard in keyboards)
                {
                    if (keyboard.GetComponentInChildren<TrackedDeviceGraphicRaycaster>(true) == null)
                        warnings.Add("Keyboard '" + keyboard.name + "' has no generated XR raycaster yet (expected before runtime Build()).");
                }
            }

            FoxCraftGameTextInputCoordinator[] coordinators =
                Object.FindObjectsOfType<FoxCraftGameTextInputCoordinator>(true);
            if (coordinators.Length == 0)
                warnings.Add("No game text coordinator in the scene. This is required for Minecraft input sessions.");
            else
                success.Add("Found game text coordinator (does not imply a Java game-focus hook).");

            if (warnings.Count == 0)
                Debug.Log("FoxCraft keyboard scene validation: no configuration warnings. This does not replace device testing.\n" +
                          string.Join("\n", success));
            else
                Debug.LogWarning("FoxCraft keyboard scene validation: " + warnings.Count + " item(s) to review.\n" +
                                 string.Join("\n", warnings) + "\n\nChecks passed:\n" + string.Join("\n", success));

            // The Java Minecraft UI focus hook and JNI bridge must be verified on-device.
        }
    }
}
