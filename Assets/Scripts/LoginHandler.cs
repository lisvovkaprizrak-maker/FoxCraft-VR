using System.Collections;
using System.Collections.Generic;
using System.Linq;
using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json;
using TMPro;
using UnityEngine;
using UnityEngine.Networking;
using UnityEngine.Serialization;

public class LoginHandler : MonoBehaviour
{
    bool isLoggedIn;
    private int loginRevision;
    public bool HasAuthenticatedAccount => isLoggedIn && JNIStorage.accountObj != null;
    
    [SerializeField] private TMP_Dropdown accountDropdown;
    public List<ConfigHandler.Accounts> accounts = new();
    private Dictionary<string, string> accountUUIDMap = new();
    private string configPath;
    private string configFile;
    public GameObject loginText;
    public WindowHandler handler;
    public UIHandler UIHandler;
    public bool isDemoMode;
    
    public string selectedAccountUsername = "Add Account";
    private string selectedAccountUUID;
    public SkinHandler skinHandler;
    private ConfigHandler.Config config;

    private IEnumerator Start()
    {
        if (Application.platform != RuntimePlatform.Android)
            yield break;

        // Unity does not promise Start() ordering across MonoBehaviours. Wait
        // for JNIStorage to create/load launcher.conf before touching accounts.
        yield return null;
        while (JNIStorage.instance == null ||
               (!JNIStorage.instance.InitializationFailed &&
                (JNIStorage.apiClass == null || JNIStorage.activity == null ||
                 JNIStorage.instance.configHandler == null ||
                 !JNIStorage.instance.configHandler.IsLoaded)))
            yield return null;

        if (JNIStorage.instance.InitializationFailed)
        {
            Debug.LogWarning("[FoxCraft] Microsoft login skipped: launcher startup failed.");
            yield break;
        }

        configPath = ConfigHandler.configPath;
        config = JNIStorage.instance.configHandler.config;
        ParseAccounts();
        Login();
    }
	
    public void Login()
    {
	    if (Application.platform != RuntimePlatform.Android) return;
		isLoggedIn = false;
        int revision = ++loginRevision;
        JNIStorage.accountObj = null;
        if (JNIStorage.apiClass == null || JNIStorage.activity == null ||
            accountDropdown == null || accountDropdown.options == null ||
            accountDropdown.options.Count == 0)
            return;
        int safeIndex = Mathf.Clamp(accountDropdown.value, 0, accountDropdown.options.Count - 1);
        selectedAccountUsername = accountDropdown.options[safeIndex].text;
        if (!accountUUIDMap.TryGetValue(selectedAccountUsername, out selectedAccountUUID))
            return;
        if (loginText != null)
            loginText.SetActive(true);
        string requestedUUID = selectedAccountUsername == "Add Account"
            ? null : selectedAccountUUID;
        JNIStorage.apiClass.CallStatic("login", JNIStorage.activity, requestedUUID);
        CheckVerification(revision, requestedUUID);
    }

    public void ParseAccounts()
    {
	    accounts.Clear();
	    accountUUIDMap.Clear();

        if (config == null)
            return;
        if (config.accounts == null)
            config.accounts = new List<ConfigHandler.Accounts>();

        foreach (var account in config.accounts.Where(account => account != null &&
                     !string.IsNullOrWhiteSpace(account.username) &&
                     !string.IsNullOrWhiteSpace(account.uuid)))
        {
            accounts.Add(account);
            accountUUIDMap[account.username] = account.uuid;
        }

	    Debug.Log("Accounts parsed.");
	    ConfigHandler.Accounts addAccount = new ConfigHandler.Accounts { username = "Add Account", uuid = "null" };

	    if (accounts.Count == 0)
	    {
		    Debug.Log("No accounts, using account sign in.");
		    accounts.Add(addAccount);
		    accountUUIDMap[addAccount.username] = addAccount.uuid;
		    accountDropdown.ClearOptions();
            accountDropdown.AddOptions(accounts.Select(account => account.username).ToList());
	    }
	    else
	    {
		    accounts.Add(addAccount);
		    accountUUIDMap[addAccount.username] = addAccount.uuid;
		    accountDropdown.ClearOptions();
            accountDropdown.AddOptions(accounts.Select(account => account.username).ToList());
        }

        // Never restore a stale slot beyond the current account list.
        int selected = Mathf.Clamp(config.lastSelectedAccount, 0, accountDropdown.options.Count - 1);
        accountDropdown.SetValueWithoutNotify(selected);
    }
    
    private async void CheckVerification(int revision, string requestedUUID)
    {
        if (Application.platform != RuntimePlatform.Android)
            return;

        // Every new Login() invalidates previous polling attempts. Comparing
        // AndroidJavaObject wrappers by reference does not identify Java
        // account changes; validate the actual UUID on the returned object.
        while (revision == loginRevision && !isLoggedIn)
        {
            await Task.Delay(1500);
            if (revision != loginRevision || JNIStorage.apiClass == null)
                return;

            try
            {
                AndroidJavaObject candidate =
                    JNIStorage.apiClass.GetStatic<AndroidJavaObject>("currentAcc");
                if (candidate == null)
                    continue;

                string uuid = candidate.Get<string>("uuid");
                string username = candidate.Get<string>("username");
                if (string.IsNullOrWhiteSpace(uuid) ||
                    string.IsNullOrWhiteSpace(username))
                    continue;

                // Pojlib can still expose the OLD session while a cached
                // account refresh is running. Never enable Play for it.
                if (requestedUUID != null &&
                    !string.Equals(requestedUUID, uuid, System.StringComparison.Ordinal))
                    continue;

                CompleteVerifiedLogin(candidate, username, uuid);
            }
            catch (System.Exception exception)
            {
                Debug.LogWarning("[FoxCraft] Microsoft verification check failed: " +
                    exception.GetType().Name);
            }
        }
    }

    private void CompleteVerifiedLogin(AndroidJavaObject candidate,
        string accountName, string accountUUID)
    {
        if (JNIStorage.instance == null ||
            JNIStorage.instance.configHandler == null ||
            !JNIStorage.instance.configHandler.IsLoaded)
            return;

        ConfigHandler settings = JNIStorage.instance.configHandler;
        config = settings.config;
        if (config.accounts == null)
            config.accounts = new List<ConfigHandler.Accounts>();

        // UUIDs are stable when a Microsoft display name changes. Record the
        // actual authenticated identity, not the earlier "Add Account" slot.
        int index = config.accounts.FindIndex(a =>
            a != null && a.uuid == accountUUID);
        if (index < 0)
        {
            config.accounts.Add(new ConfigHandler.Accounts
            {
                username = accountName,
                uuid = accountUUID
            });
            index = config.accounts.Count - 1;
        }
        else
        {
            config.accounts[index].username = accountName;
        }

        config.lastSelectedAccount = index;
        settings.SaveConfig();
        selectedAccountUsername = accountName;
        selectedAccountUUID = accountUUID;
        ParseAccounts();

        isDemoMode = candidate.Get<bool>("isDemoMode");
        JNIStorage.accountObj = candidate;
        isLoggedIn = true;
        if (loginText != null)
            loginText.SetActive(false);
        if (handler != null)
            handler.LoadAv(accountName);
        Debug.Log("[FoxCraft] Microsoft account verified and saved.");
    }

    private void OnDestroy()
    {
        // Prevent detached async pollers from mutating the next launcher.
        loginRevision++;
    }

    public void RemoveAccount()
    {
        loginRevision++;
        isLoggedIn = false;
        JNIStorage.accountObj = null;
        if (JNIStorage.apiClass == null || JNIStorage.activity == null ||
            string.IsNullOrWhiteSpace(selectedAccountUUID) ||
            selectedAccountUUID == "null")
            return;

        JNIStorage.apiClass.CallStatic<bool>("removeAccount",
            JNIStorage.activity, selectedAccountUUID);
        config = JNIStorage.instance.configHandler.config;
        config.accounts.RemoveAll(account =>
            account != null && account.uuid == selectedAccountUUID);
        config.lastSelectedAccount = 0;
        JNIStorage.instance.configHandler.SaveConfig();
	    
        handler.LogoutWindowUnsetter();
        ParseAccounts();
        if (accountDropdown.options != null && accountDropdown.options.Count > 0)
        {
            selectedAccountUsername = accountDropdown.options[accountDropdown.value].text;
            handler.LoadAv(selectedAccountUsername);
            Login();
        }
    }
}