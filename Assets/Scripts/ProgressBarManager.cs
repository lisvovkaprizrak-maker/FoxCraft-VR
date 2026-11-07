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

    private void Awake()
    {
        // The Java restartLauncher path can recreate Unity's launcher without
        // restarting Android's process. Static progress flags from the old
        // session must never unlock Play on the new account/login screen.
        started = false;
        DownloadsReady = false;
        loggedDownloadFailure = false;
    }

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
            if (InstanceManager.IsUpdatingProfiles)
            {
                DownloadsReady = false;
                float profileProgress = InstanceManager.IsCreatingProfile
                    ? JNIStorage.apiClass.CallStatic<float>("getDownloadPercentage")
                    : 0f;
                ShowProgress(InstanceManager.IsCreatingProfile
                    ? "Создание сборки Minecraft…"
                    : "Удаление сборки Minecraft…", profileProgress);
                if (playButton != null)
                    playButton.interactable = false;
                if (accountDropdown != null)
                    accountDropdown.interactable = false;
                return;
            }
            if (InstanceButton.IsInstallingInstance)
            {
                DownloadsReady = false;
                float installationProgress =
                    JNIStorage.apiClass.CallStatic<float>("getDownloadPercentage");
                ShowProgress("Установка сборки Minecraft…", installationProgress);
                if (playButton != null)
                    playButton.interactable = false;
                if (accountDropdown != null)
                    accountDropdown.interactable = false;
                return;
            }

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
            // Bundled Pojlib DownloadManager.getPercentComplete returns 0..100.
            // normalizedValue also handles Slider min/max customized in Unity.
            progressBar.normalizedValue = Mathf.Clamp01(value / 100f);
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
