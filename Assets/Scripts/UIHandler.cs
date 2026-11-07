using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using TMPro;
using UnityEngine;
using UnityEngine.Networking;
using UnityEngine.UI;

public class UIHandler : MonoBehaviour
{
    public TextMeshProUGUI minuteHourText;
    public TMP_Dropdown dropdownMain;
    public TMP_Dropdown dropdownModSearch;
    public TMP_Dropdown dropdownModInfo;
    public TMP_Dropdown dropdownInstanceCreator;
    public GameObject legalContent;
    public GameObject errorMenu;
    public GameObject logPanel;
    public Button legalContinue;
    public Toggle modToggle;
    public Toggle modpacksToggle;
    public Toggle resourcePacksToggle;
    public Button modsButton;
    public Button instancesButton;
    public Button playButton;
    public Button killButton;
    public Button logoutButton;
    public Button needHelpButton;
    public static int selectedInstance;
    public string pfpUrl;
    static string profileName;
    public ModManager modManager;
    public LoginHandler loginHandler;

    void Start()
    {
        // Add listeners for toggle buttons
        modToggle.onValueChanged.AddListener((value) => OnToggleClicked(value, modToggle));
        modpacksToggle.onValueChanged.AddListener((value) => OnToggleClicked(value, modpacksToggle));
        resourcePacksToggle.onValueChanged.AddListener((value) => OnToggleClicked(value, resourcePacksToggle));
        
        dropdownMain.onValueChanged.AddListener(delegate
        {
            OnSelectedInstanceChanged(dropdownMain);
        });
        dropdownInstanceCreator.onValueChanged.AddListener(delegate
        {
            OnSelectedInstanceChanged(dropdownInstanceCreator);
        });
        dropdownModInfo.onValueChanged.AddListener(delegate
        {
            OnSelectedInstanceChanged(dropdownModInfo);
        });
        dropdownModSearch.onValueChanged.AddListener(delegate
        {
            OnSelectedInstanceChanged(dropdownModSearch);
        });
        
        dropdownMain.interactable = false;
        modsButton.interactable = false;
        instancesButton.interactable = false;
        playButton.interactable = false;

        // Keep QuestCraft's existing Minecraft version selector; add only the
        // independent FoxCraft VR / Phone choice without scene edits.
        FoxCraftLaunchModeMenu modes = gameObject.AddComponent<FoxCraftLaunchModeMenu>();
        modes.Initialize(this);
        // Always register the deferred Android touch adapter. Unknown
        // headset model codenames cannot be classified until Android exposes
        // the PackageManager a few frames after Unity Start. The adapter
        // itself exits on Quest/Pico without touching their VR pointer modules.
        if (Application.platform == RuntimePlatform.Android)
            gameObject.AddComponent<FoxCraftPhoneTouchInput>();
    }
    
    void Update()
    {
        string time = DateTime.Now.ToString("hh:mm tt");
        minuteHourText.text = time;
        UILoginCheck();
    }

    public void UILoginCheck()
    {
        // A saved profile name is not proof of sign-in. Pojlib can still be
        // downloading dependencies while login completes. Both conditions
        // must be true before enabling Play or profile/mod operations.
        bool authenticated = loginHandler != null &&
            loginHandler.HasAuthenticatedAccount;
        bool launcherBusy = ProgressBarManager.started ||
            InstanceButton.IsInstallingInstance || InstanceManager.IsUpdatingProfiles;
        bool playReady = FoxCraftLaunchGate.CanPlay(authenticated,
            ProgressBarManager.DownloadsReady, launcherBusy);
        bool canManage = FoxCraftLaunchGate.CanManageInstances(authenticated,
            loginHandler != null && loginHandler.isDemoMode,
            ProgressBarManager.DownloadsReady, launcherBusy);

        dropdownMain.interactable = canManage;
        modsButton.interactable = canManage;
        instancesButton.interactable = canManage;
        playButton.interactable = playReady;
    }
    
    void OnToggleClicked(bool value, Toggle clickedToggle)
    {
        if (modManager.isSearching)
        {
            clickedToggle.isOn = false;
            return;
        }
        
        if (value)
        {
            // Enable the toggles
            Toggle[] allToggles = { modToggle, modpacksToggle, resourcePacksToggle };
            foreach (Toggle toggle in allToggles)
                toggle.interactable = true;

            clickedToggle.isOn = false;
            // Disable the clicked toggle
            clickedToggle.interactable = false;
            modManager.SearchMods();
        }
    }

    private void OnSelectedInstanceChanged(TMP_Dropdown origin)
    {
        // These four QuestCraft dropdowns represent one selected profile.
        // Persist changes when the user selects them, not only when Play is
        // pressed. Programmatic synchronization uses SetValueWithoutNotify
        // and therefore cannot feed back or overwrite the saved selection.
        if (origin == null || origin.options == null ||
            origin.value < 0 || origin.value >= origin.options.Count ||
            origin.options[origin.value] == null)
            return;

        selectedInstance = origin.value;
        UpdateDropdowns(false, null);
        if (JNIStorage.instance != null &&
            JNIStorage.instance.configHandler != null &&
            JNIStorage.instance.configHandler.IsLoaded)
            JNIStorage.instance.configHandler.SetLastSelectedInstance(
                selectedInstance);
    }

    public void UpdateDropdowns(bool init, List<string> list)
    {
        if (init)
        {
            dropdownMain.AddOptions(list);
            dropdownInstanceCreator.AddOptions(list);
            dropdownModInfo.AddOptions(list);
            dropdownModSearch.AddOptions(list);
        }
        else
        {
            dropdownMain.SetValueWithoutNotify(selectedInstance);
            dropdownInstanceCreator.SetValueWithoutNotify(selectedInstance);
            dropdownModInfo.SetValueWithoutNotify(selectedInstance);
            dropdownModSearch.SetValueWithoutNotify(selectedInstance);
        }
    }

    public void ClearDropdowns()
    {
        dropdownMain.ClearOptions();
        dropdownInstanceCreator.ClearOptions();
        dropdownModInfo.ClearOptions();
        dropdownModSearch.ClearOptions();
    }

    public void SetAndShowError(String errorMessage)
    {
        errorMenu.GetComponentInChildren<TextMeshProUGUI>().text = errorMessage;
        errorMenu.SetActive(true);
    }

    public void UpdateLegalButton()
    {
        if (legalContent.transform.localPosition.y > 6800f)
        {
            legalContinue.interactable = true;
        }
    }

    public void PlaySetter()
    {
        needHelpButton.interactable = false;
        logoutButton.interactable = false;
        dropdownMain.interactable = false;
        modsButton.interactable = false;
        instancesButton.interactable = false;
        playButton.gameObject.SetActive(false);
        killButton.gameObject.SetActive(true);
        logPanel.SetActive(true);
    }   
    
    public void KillSetter()
    {
        needHelpButton.interactable = true;
        logoutButton.interactable = true;
        dropdownMain.interactable = true;
        modsButton.interactable = true;
        instancesButton.interactable = true;
        playButton.gameObject.SetActive(true);
        killButton.gameObject.SetActive(false);
        logPanel.SetActive(false);
        // Never temporarily unlock Play, Mods, or profile management after
        // a failed Java launch if the player has logged out or Pojlib is still
        // downloading. The same readiness gate governs normal UI updates.
        UILoginCheck();
    }
}
