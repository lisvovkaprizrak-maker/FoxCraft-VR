#if UNITY_EDITOR
using FoxCraft.UI;
using TMPro;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.SceneManagement;
using UnityEngine.XR.Interaction.Toolkit.UI;

/// <summary>
/// Installs a stable focus-message endpoint without changing QuestCraft's Android
/// activity or Java runtime. The Java sender and Minecraft focus detection must
/// be implemented and verified separately.
/// </summary>
public static class FoxCraftKeyboardSceneSetup
{
    public const string BridgeObjectName = "FoxCraftGameTextBridge";

    [MenuItem("FoxCraft/Install Keyboard In Open Scene")]
    public static void Install()
    {
        if (Application.isPlaying)
        {
            Debug.LogWarning("FoxCraft: stop Play Mode before installing the keyboard.");
            return;
        }

        Scene scene = SceneManager.GetActiveScene();
        if (!scene.IsValid() || !scene.isLoaded)
        {
            Debug.LogError("FoxCraft: open a scene before installing the keyboard.");
            return;
        }

        GameObject root = GameObject.Find(BridgeObjectName);
        if (root == null)
        {
            root = new GameObject(BridgeObjectName);
            Undo.RegisterCreatedObjectUndo(root, "Create FoxCraft text bridge");
            SceneManager.MoveGameObjectToScene(root, scene);
        }

        FoxCraftKeyboard keyboard = AddIfMissing<FoxCraftKeyboard>(root);
        EnsureCyrillicFont(keyboard);
        AddIfMissing<FoxCraftGameTextInputCoordinator>(root);
        AddIfMissing<FoxCraftPojlibGlfwInputBridge>(root);
        AddIfMissing<FoxCraftGameFocusReceiver>(root);

        int wired = 0;
        foreach (KeyboardHandler handler in Object.FindObjectsOfType<KeyboardHandler>(true))
        {
            if (handler.gameObject.scene != scene) continue;
            SerializedObject serialized = new SerializedObject(handler);
            SerializedProperty reference = serialized.FindProperty("foxCraftKeyboard");
            SerializedProperty prefer = serialized.FindProperty("preferFoxCraftKeyboard");
            if (reference != null && prefer != null)
            {
                reference.objectReferenceValue = keyboard;
                prefer.boolValue = true;
                serialized.ApplyModifiedProperties();
                EditorUtility.SetDirty(handler);
                wired++;
            }
        }

        EditorSceneManager.MarkSceneDirty(scene);
        Debug.Log("FoxCraft keyboard installed in " + scene.name + ". Wired " + wired +
                  " launcher handler(s). Game focus and native GLFW injection stay disabled. " +
                  "Save the scene, then run FoxCraft/Validate Keyboard Setup.");
        Validate();
        Selection.activeGameObject = root;
    }

    private static T AddIfMissing<T>(GameObject root) where T : Component
    {
        T component = root.GetComponent<T>();
        return component != null ? component : Undo.AddComponent<T>(root);
    }

    private static void EnsureCyrillicFont(FoxCraftKeyboard keyboard)
    {
        SerializedObject keyboardSettings = new SerializedObject(keyboard);
        SerializedProperty fontProperty = keyboardSettings.FindProperty("font");
        if (fontProperty == null || fontProperty.objectReferenceValue != null)
            return;

        string[] fontGuids = AssetDatabase.FindAssets("t:TMP_FontAsset");
        foreach (string guid in fontGuids)
        {
            string assetPath = AssetDatabase.GUIDToAssetPath(guid);
            TMP_FontAsset candidate = AssetDatabase.LoadAssetAtPath<TMP_FontAsset>(assetPath);
            if (candidate == null || !candidate.HasCharacter('Ж', true) ||
                !candidate.HasCharacter('я', true))
                continue;
            fontProperty.objectReferenceValue = candidate;
            keyboardSettings.ApplyModifiedProperties();
            EditorUtility.SetDirty(keyboard);
            Debug.Log("FoxCraft: using Cyrillic-compatible TMP font: " + assetPath, keyboard);
            return;
        }
        Debug.LogWarning("FoxCraft: no installed TMP font exposes Russian glyphs. " +
            "Assign a Cyrillic-enabled TMP font asset to FoxCraftKeyboard in the Inspector.", keyboard);
    }

    [MenuItem("FoxCraft/Validate Keyboard Setup")]
    public static void Validate()
    {
        GameObject root = GameObject.Find(BridgeObjectName);
        if (root == null)
        {
            Debug.LogWarning("FoxCraft: no " + BridgeObjectName + " object in the current scene. " +
                             "Use FoxCraft/Install Keyboard In Open Scene.");
            return;
        }

        if (root.GetComponent<FoxCraftKeyboard>() == null ||
            root.GetComponent<FoxCraftGameTextInputCoordinator>() == null ||
            root.GetComponent<FoxCraftPojlibGlfwInputBridge>() == null ||
            root.GetComponent<FoxCraftGameFocusReceiver>() == null)
            Debug.LogWarning("FoxCraft: missing keyboard or game bridge components.", root);
        else
            Debug.Log("FoxCraft: keyboard, coordinator, JNI adapter and focus receiver found.", root);

        if (Camera.main == null)
            Debug.LogWarning("FoxCraft: no MainCamera-tagged camera found. Assign a placement anchor.", root);

        SerializedObject keyboardSettings = new SerializedObject(root.GetComponent<FoxCraftKeyboard>());
        SerializedProperty fontProperty = keyboardSettings.FindProperty("font");
        if (fontProperty == null || fontProperty.objectReferenceValue == null)
            Debug.LogWarning("FoxCraft: no custom TMP font assigned; Russian characters may render as squares.", root);

        EventSystem eventSystem = Object.FindObjectOfType<EventSystem>();
        if (eventSystem == null)
            Debug.LogWarning("FoxCraft: EventSystem missing. Configure an XR UI Input Module.", root);
        else if (eventSystem.GetComponent<XRUIInputModule>() == null)
            Debug.LogWarning("FoxCraft: EventSystem has no XRUIInputModule; controller rays may not click keyboard keys.", eventSystem);

        if (Object.FindObjectOfType<KeyboardHandler>() == null)
            Debug.LogWarning("FoxCraft: no KeyboardHandler detected. Launcher fields may need manual bindings.", root);

        Debug.Log("FoxCraft: game input still requires a verified Minecraft focus hook, " +
                  "Cyrillic TMP font, XR Ray Interactor and device validation.");
    }
}
#endif
