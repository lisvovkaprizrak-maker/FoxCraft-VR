using System.Collections;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem.UI;
using UnityEngine.UI;

// The QuestCraft launcher uses tracked VR pointers. Android phones need a
// normal touch pointer to tap the SAME account/version/mod/settings menu.
public sealed class FoxCraftPhoneTouchInput : MonoBehaviour
{
    private IEnumerator Start()
    {
        if (!FoxCraftDevice.IsAndroidPhone)
            yield break;

        // Wait for the original QuestCraft scene's EventSystem to initialize.
        yield return null;
        EventSystem events = EventSystem.current;
        if (events == null)
        {
            GameObject host = new GameObject("FoxCraft Android Touch Events");
            events = host.AddComponent<EventSystem>();
        }

        InputSystemUIInputModule touch = events.GetComponent<InputSystemUIInputModule>();
        if (touch == null)
        {
            touch = events.gameObject.AddComponent<InputSystemUIInputModule>();
            touch.AssignDefaultActions();
        }

        // A single EventSystem module must handle the phone's touchscreen.
        foreach (BaseInputModule module in events.GetComponents<BaseInputModule>())
        {
            if (module != null && module != touch)
                module.enabled = false;
        }
        touch.enabled = true;

        // QuestCraft's existing menu hierarchy remains intact. Add only the
        // 2D raycast capability missing from VR-only menu canvas variants.
        foreach (Canvas canvas in FindObjectsOfType<Canvas>(true))
        {
            if (canvas == null || canvas.GetComponent<GraphicRaycaster>() != null)
                continue;

            canvas.gameObject.AddComponent<GraphicRaycaster>();
        }

        Debug.Log("[FoxCraft] Android touchscreen enabled for the original QuestCraft menu.");
    }
}
