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
            keyboard.ShowFor(null);
            Assert(keyboard.IsGameTextMode && keyboard.CurrentText == "a",
                "Launcher input cannot steal an active game session.");
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

            // Java GLFW char callback cannot encode supplementary characters.
            keyboard.ShowWithText("abc");
            keyboard.Insert("🙂");
            Assert(keyboard.CurrentText == "abc", "Unsupported game emoji must not desync preview.");
            keyboard.Hide();
            keyboard.Insert("!");
            keyboard.Backspace();
            keyboard.Submit();
            Assert(keyboard.CurrentText == "abc", "Hidden keyboard must ignore edit operations.");

            FoxCraftGameTextInputCoordinator coordinator =
                root.AddComponent<FoxCraftGameTextInputCoordinator>();
            string gameCharacter = null;
            string gameSubmit = null;
            coordinator.OnGameCharacterInserted.AddListener(s => gameCharacter = s);
            coordinator.OnGameTextSubmitted.AddListener(s => gameSubmit = s);
            int gameCancels = 0;
            int gameChanges = 0;
            coordinator.OnGameTextCancelled.AddListener(() => gameCancels++);
            coordinator.OnGameTextChanged.AddListener(s => gameChanges++);
            coordinator.OpenFromGame("");
            keyboard.Insert("Привет");
            Assert(gameCharacter == "Привет", "Coordinator forwards character edits.");
            keyboard.Submit();
            Assert(gameSubmit == "Привет" && !coordinator.IsSessionOpen,
                "Coordinator submits and closes session.");
            Assert(gameChanges == 1, "Game edits must not be duplicated on submit.");

            coordinator.OpenFromGame("hello");
            keyboard.Insert("!");
            keyboard.Cancel();
            Assert(gameCancels == 1 && !coordinator.IsSessionOpen,
                "Game cancel fires once and closes focused session.");
            Assert(gameChanges == 2, "Cancel must not emit an extra game text snapshot.");

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
