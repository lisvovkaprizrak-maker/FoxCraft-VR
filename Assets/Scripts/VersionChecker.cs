using System;
using TMPro;
using UnityEngine;

public class VersionChecker : MonoBehaviour
{
    public string currentPublicVersion;
    public TextMeshProUGUI updateText;
    public TextMeshProUGUI version;

    private string currentVersion;
    private bool updateShown;

    [ContextMenu("Check Version")]
    public void Start()
    {
        currentVersion = Application.version;
        if (version != null)
            version.text = currentVersion;
    }

    public void CheckVersion()
    {
        if (string.IsNullOrWhiteSpace(currentVersion))
            currentVersion = Application.version;

        if (!IsUpdateAvailable(currentVersion, currentPublicVersion))
        {
            Debug.Log("[FoxCraft] Launcher is up to date or no comparable release was found.");
            return;
        }

        if (updateShown)
            return;

        Debug.Log("[FoxCraft] Launcher update available: " + currentPublicVersion);
        if (updateText != null)
        {
            updateText.text += " <color=#D70A53>| Доступно обновление FoxCraft!</color>";
            updateShown = true;
        }
    }

    // Accept both v1.2.3 and 1.2.3, including Unity's 0.1.0-alpha version.
    // A stable release supersedes a prerelease with the same numeric version.
    public static bool IsUpdateAvailable(string installed, string published)
    {
        if (!TryParseVersion(installed, out Version current, out string currentPrerelease) ||
            !TryParseVersion(published, out Version latest, out string latestPrerelease))
            return false;

        int comparison = latest.CompareTo(current);
        if (comparison != 0)
            return comparison > 0;

        return currentPrerelease.Length != 0 && latestPrerelease.Length == 0;
    }

    private static bool TryParseVersion(string value, out Version result, out string prerelease)
    {
        result = null;
        prerelease = "";
        if (string.IsNullOrWhiteSpace(value))
            return false;

        value = value.Trim();
        if (value.StartsWith("v", StringComparison.OrdinalIgnoreCase))
            value = value.Substring(1);

        int buildSeparator = value.IndexOf('+');
        if (buildSeparator >= 0)
            value = value.Substring(0, buildSeparator);

        int prereleaseSeparator = value.IndexOf('-');
        if (prereleaseSeparator >= 0)
        {
            prerelease = value.Substring(prereleaseSeparator + 1);
            value = value.Substring(0, prereleaseSeparator);
        }

        return Version.TryParse(value, out result);
    }
}
