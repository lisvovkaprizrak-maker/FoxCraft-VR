using UnityEngine;

public class DesktopCam : MonoBehaviour
{
    public GameObject VrCam;
    public GameObject DesktopCamera;
    public Canvas[] Canvases;

    private void Start()
    {
        // QuestCraft normally uses a tracked VR camera on Android. Keep it on
        // Quest and Pico devices, but expose the existing desktop/flat camera
        // on regular Android phones (and in the desktop Unity editor).
        if (Application.platform == RuntimePlatform.Android && IsVrHeadset())
            return;

        if (DesktopCamera == null)
        {
            Debug.LogWarning("[FoxCraft] Flat launcher camera is not configured.");
            return;
        }

        if (VrCam != null)
            VrCam.SetActive(false);
        DesktopCamera.SetActive(true);

        Camera flatCamera = DesktopCamera.GetComponent<Camera>();
        if (flatCamera == null)
        {
            Debug.LogWarning("[FoxCraft] Flat launcher camera component is missing.");
            return;
        }

        if (Canvases == null)
            return;

        foreach (Canvas screenCanvas in Canvases)
        {
            if (screenCanvas == null)
                continue;
            screenCanvas.worldCamera = flatCamera;
        }
    }

    private static bool IsVrHeadset()
    {
        string model = (SystemInfo.deviceModel ?? "").ToLowerInvariant();
        return model.Contains("quest") ||
               model.Contains("pico") ||
               model.Contains("yvr") ||
               model.Contains("oculus");
    }
}
