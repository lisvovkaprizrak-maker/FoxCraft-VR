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
        while (JNIStorage.instance == null || JNIStorage.apiClass == null ||
               JNIStorage.activity == null || JNIStorage.instance.configHandler == null ||
               !JNIStorage.instance.configHandler.IsLoaded)
            yield return null;

        configPath = ConfigHandler.configPath;
        config = JNIStorage.instance.configHandler.config;
        ParseAccounts();
        Login();
    }
	
    public void Login()
    {
	    if (Application.platform != RuntimePlatform.Android) return;
		isLoggedIn = false;
        if (JNIStorage.apiClass == null || JNIStorage.activity == null ||
            accountDropdown.options == null || accountDropdown.options.Count == 0)
            return;
        int safeIndex = Mathf.Clamp(accountDropdown.value, 0, accountDropdown.options.Count - 1);
        selectedAccountUsername = accountDropdown.options[safeIndex].text;
        if (!accountUUIDMap.TryGetValue(selectedAccountUsername, out selectedAccountUUID))
            return;
	    loginText.SetActive(true);
	    JNIStorage.apiClass.CallStatic("login", JNIStorage.activity, selectedAccountUsername == "Add Account" ? null : selectedAccountUUID);
	    CheckVerification();
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
    
    private async void CheckVerification() {
	    if (Application.platform != RuntimePlatform.Android) return;
	    while (!isLoggedIn)
	    {
		    await Task.Delay(1500);
		    
		    Debug.Log("Check Login State");
		    if (JNIStorage.accountObj != JNIStorage.apiClass.GetStatic<AndroidJavaObject>("currentAcc"))
		    {
			    JNIStorage.accountObj = JNIStorage.apiClass.GetStatic<AndroidJavaObject>("currentAcc");
			    if (JNIStorage.accountObj != null)
			    {
				    string configFile = File.ReadAllText(configPath);
				    string accName = JNIStorage.apiClass.GetStatic<string>("profileName");
				    string accUUID = JNIStorage.apiClass.GetStatic<string>("profileUUID");
				    Debug.Log("Logged Into " + accName + " | " + accUUID);
				    loginText.SetActive(false);
				    isLoggedIn = true;
                    config = JNIStorage.instance.configHandler.config;
				    bool accExists = config.accounts.Any(account => account.username == accName);
				    if (config.accounts.All(account => account.uuid != accUUID))
				    {
					    config.accounts.Add(new ConfigHandler.Accounts { username = accName, uuid = accUUID });
				    }

				    config.lastSelectedAccount = accountDropdown.value;
                    JNIStorage.instance.configHandler.SaveConfig();
				    isDemoMode = JNIStorage.apiClass.GetStatic<bool>("isDemoMode");
				    if (!accExists)
				    {
					    ParseAccounts();
				    }

				    selectedAccountUsername = accName;
				    selectedAccountUUID = accUUID;
				    if (accName != "Add Account")
				    {
					    handler.LoadAv(selectedAccountUsername);
				    }
				    else
				    {
					    handler.LoadAv(accName);
				    }
			    }
		    }
	    }
    }
    
    public void RemoveAccount()
    {
	    JNIStorage.accountObj = null;
	    JNIStorage.apiClass.CallStatic<bool>("removeAccount", JNIStorage.activity, selectedAccountUUID);
	    
        config = JNIStorage.instance.configHandler.config;
        config.accounts.RemoveAll(account => account.username == selectedAccountUsername);
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