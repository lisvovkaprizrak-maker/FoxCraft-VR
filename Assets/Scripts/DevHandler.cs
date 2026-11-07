using Newtonsoft.Json;
using TMPro;
using UnityEngine;
using UnityEngine.Serialization;
using UnityEngine.UI;
using System.IO;

public class DevHandler : MonoBehaviour
{
    [SerializeField, FormerlySerializedAs("DevToggle")]
    public Toggle _devToggle;
    [SerializeField, FormerlySerializedAs("ADBToggle")]
    private Toggle _adbToggle;
    [SerializeField, FormerlySerializedAs("RAMSetterToggle")]
    public Toggle _ramSetterToggle;
    [SerializeField, FormerlySerializedAs("RAMSetterField")]
    public TMP_InputField _ramSetterField;
    public Button fixFilePerms;

    public ConfigHandler configHandler;
    public string configPath;

    private void Start()
    {
        InitializeButtonListeners();
        configPath = Application.persistentDataPath + "/launcher.conf";
    }
    
    private void OnDestroy()
    {
        DisposeButtonListeners();
    }

    private void InitializeButtonListeners()
    {
        _devToggle.onValueChanged.AddListener(OnDevModsToggleValueChanged);
        _ramSetterToggle.onValueChanged.AddListener(OnRamSetterToggleValueChanged);
    }
    
    private void DisposeButtonListeners()
    {
        _devToggle.onValueChanged.RemoveListener(OnDevModsToggleValueChanged);
        _ramSetterToggle.onValueChanged.RemoveListener(OnRamSetterToggleValueChanged);
    }

    private void OnDevModsToggleValueChanged(bool isOn)
    {
        if (configHandler == null || !configHandler.IsLoaded)
            return;
        configHandler.config.setDevMods = isOn;
        configHandler.SaveConfig();

        if (JNIStorage.apiClass != null)
            JNIStorage.apiClass.SetStatic("developerMods", isOn);
        if (JNIStorage.instance != null)
            JNIStorage.instance.UpdateInstances();
    }    
    
    private void OnRamSetterToggleValueChanged(bool isOn)
    {
        if (configHandler == null || !configHandler.IsLoaded)
            return;
        configHandler.config.setCustomRAM = isOn;
        configHandler.SaveConfig();

        if (JNIStorage.apiClass != null)
            JNIStorage.apiClass.SetStatic("customRAMValue", isOn);
        _ramSetterField.gameObject.SetActive(isOn);
    }    
}
