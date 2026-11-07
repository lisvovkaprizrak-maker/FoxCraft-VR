using System;
using TMPro;
using UnityEngine;
using UnityEngine.UI;

public class ProgressBarManager : MonoBehaviour
{
    public Slider progressBar;
    public TextMeshProUGUI downloadText;
    public Button playButton;
    public TMP_Dropdown accountDropdown;

    public static bool started = false;
    public static bool DownloadsReady { get; private set; }
    private bool loggedDownloadFailure;

    void Update()
    {
        if (Application.platform != RuntimePlatform.Android || started)
            return;

        if (JNIStorage.apiClass == null)
        {
            DownloadsReady = false;
            ShowProgress("Инициализация FoxCraft…", 0f);
            if (playButton != null)
                playButton.interactable = false;
            if (accountDropdown != null)
                accountDropdown.interactable = false;
            return;
        }

        try
        {
            bool completed = JNIStorage.apiClass.CallStatic<bool>("isDownloadsCompleted");
            DownloadsReady = completed;
            if (completed)
            {
                HideProgress();
            }
            else
            {
                float percent = JNIStorage.apiClass.CallStatic<float>(
                    "getDownloadPercentage");
                ShowProgress("Загрузка файлов Minecraft…", percent);
            }

            if (accountDropdown != null)
                accountDropdown.interactable = completed;

            // Only UIHandler.UILoginCheck may ENABLE Play after authenticated
            // account verification. This component can only disable it while
            // the underlying Pojlib assets are still downloading.
            if (!completed && playButton != null)
                playButton.interactable = false;
            loggedDownloadFailure = false;
        }
        catch (Exception exception)
        {
            DownloadsReady = false;
            ShowProgress("Не удалось проверить загрузку Minecraft.", 0f);
            if (playButton != null)
                playButton.interactable = false;
            if (accountDropdown != null)
                accountDropdown.interactable = false;
            if (!loggedDownloadFailure)
            {
                loggedDownloadFailure = true;
                Debug.LogWarning("[FoxCraft] Pojlib download status unavailable: " +
                    exception.GetType().Name);
            }
        }
    }

    private void ShowProgress(string message, float value)
    {
        if (downloadText != null)
        {
            downloadText.gameObject.SetActive(true);
            downloadText.text = message;
        }
        if (progressBar != null)
        {
            progressBar.gameObject.SetActive(true);
            progressBar.value = Mathf.Clamp01(value);
        }
    }

    private void HideProgress()
    {
        if (downloadText != null)
            downloadText.gameObject.SetActive(false);
        if (progressBar != null)
            progressBar.gameObject.SetActive(false);
    }
}
