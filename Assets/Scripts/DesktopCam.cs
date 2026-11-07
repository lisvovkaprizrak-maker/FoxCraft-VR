using System.Collections;
using UnityEngine;

public class DesktopCam : MonoBehaviour
{
    public GameObject VrCam;
    public GameObject DesktopCamera;
    public Canvas[] Canvases;

    private IEnumerator Start()
    {
        // On some headsets Android reports an internal model codename and
        // initializes the PackageManager a few frames after the scene.
        // Do not disable the Quest VR camera until classification completes.
        if (Application.platform == RuntimePlatform.Android)
        {
            float deadline = Time.realtimeSinceStartup + 5f;
            while (FoxCraftDevice.IsDetectionPending &&
                   Time.realtimeSinceStartup < deadline)
                yield return null;
            if (FoxCraftDevice.IsDetectionPending)
                FoxCraftDevice.CompleteDetectionWithPhoneFallback();
        }

        if (FoxCraftDevice.IsVrHeadset)
            yield break;

        if (DesktopCamera == null)
        {
            Debug.LogWarning("[FoxCraft] Flat launcher camera is not configured.");
            yield break;
        }

        if (VrCam != null)
            VrCam.SetActive(false);
        DesktopCamera.SetActive(true);

        Camera flatCamera = DesktopCamera.GetComponent<Camera>();
        if (flatCamera == null)
        {
            Debug.LogWarning("[FoxCraft] Flat launcher camera component is missing.");
            yield break;
        }

        if (Canvases == null)
            yield break;

        foreach (Canvas screenCanvas in Canvases)
        {
            if (screenCanvas == null)
                continue;
            screenCanvas.worldCamera = flatCamera;
        }
    }

}
