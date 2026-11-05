#if UNITY_EDITOR
using System;
using FoxCraft.UI;
using UnityEditor;
using UnityEngine;

/// <summary>
/// Opt-in Play Mode smoke tests for keyboard editing and event lifecycles.
/// Runs entirely on the Unity side; does not claim to test JNI or the headset.
/// </summary>
public static class FoxCraftKeyboardSmokeChecks
{
    [MenuItem("FoxCraft/Run Keyboard Smoke Checks", true)]
    private static bool CanRun() { return Application.isPlaying; }

    [MenuItem("FoxCraft/Run Keyboard Smoke Checks")]
    private static void Run()
    {
        GameObject root = new GameObject("FoxCraft Smoke Check (temporary)");
        try
        {
            FoxCraftKeyboard keyboard = root.AddComponent<FoxCraftKeyboard>();
            string emittedCharacter = null;
            string submitted = null;
            int backspaces = 0;
            int cancels = 0;
            keyboard.OnCharacterInserted.AddListener(s => emittedCharacter = s);
            keyboard.OnBackspacePressed.AddListener(() => backspaces++);
            keyboard.OnSubmitted.AddListener(s => submitted = s);
            keyboard.OnCancelled.AddListener(() => cancels++);

            keyboard.ShowWithText("a");
            Assert(keyboard.IsGameTextMode && keyboard.IsVisible, "Game keyboard should be visible.");
            keyboard.Insert("б");
            Assert(keyboard.CurrentText == "aб" && emittedCharacter == "б", "Cyrillic insert.");
            keyboard.MoveLeft();
            keyboard.Insert("X");
            Assert(keyboard.CurrentText == "aXб", "Cursor insert.");
            keyboard.Backspace();
            Assert(keyboard.CurrentText == "aб" && backspaces == 1, "Backspace.");
            keyboard.Submit();
            Assert(submitted == "aб" && !keyboard.IsVisible, "Submit closes keyboard.");

            keyboard.ShowWithText("start");
            keyboard.Insert("!");
            keyboard.Cancel();
            Assert(cancels == 1 && keyboard.CurrentText == "start", "Cancel restores buffer.");

            FoxCraftGameTextInputCoordinator coordinator =
                root.AddComponent<FoxCraftGameTextInputCoordinator>();
            string gameCharacter = null;
            string gameSubmit = null;
            coordinator.OnGameCharacterInserted.AddListener(s => gameCharacter = s);
            coordinator.OnGameTextSubmitted.AddListener(s => gameSubmit = s);
            coordinator.OpenFromGame("");
            keyboard.Insert("Привет");
            Assert(gameCharacter == "Привет", "Coordinator forwards character edits.");
            keyboard.Submit();
            Assert(gameSubmit == "Привет" && !coordinator.IsSessionOpen,
                "Coordinator submits and closes session.");

            Debug.Log("FoxCraft: keyboard smoke checks passed (Unity only; JNI and XR hardware not tested).");
        }
        catch (Exception error)
        {
            Debug.LogError("FoxCraft: keyboard smoke checks FAILED: " + error);
        }
        finally
        {
            UnityEngine.Object.Destroy(root);
        }
    }

    private static void Assert(bool condition, string message)
    {
        if (!condition) throw new InvalidOperationException(message);
    }
}
#endif
