using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using Newtonsoft.Json;
using TMPro;
using UnityEngine;
using UnityEngine.Networking;
using UnityEngine.UI;

public class LoadReleases : MonoBehaviour
{
    // Launcher updates must come from FoxCraft, not upstream QuestCraft.
    private const string ReleasesUrl =
        "https://api.github.com/repos/lisvovkaprizrak-maker/FoxCraft-VR/releases?per_page=20";

    public TextMeshProUGUI tags;
    public ScrollRect changelog;
    public VersionChecker versionChecker;

    private bool cooldown;

    public class ReleaseInfo
    {
        public string tag_name;
        public string name;
        public string published_at;
        public string body;
        public bool draft;
        public bool prerelease;
    }

    private void Start() => LoadChangeLog();

    [ContextMenu("Generate Releases")]
    public void LoadChangeLog()
    {
        if (!cooldown)
            StartCoroutine(FetchReleases());
    }

    private IEnumerator FetchReleases()
    {
        cooldown = true;
        using (UnityWebRequest request = UnityWebRequest.Get(ReleasesUrl))
        {
            request.timeout = 15;
            request.SetRequestHeader("User-Agent", "FoxCraft-VR/" + Application.version);
            yield return request.SendWebRequest();

            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogWarning("[FoxCraft] Unable to fetch launcher releases: " + request.error);
                SetChangelog("Не удалось загрузить новости FoxCraft. Проверьте соединение и попробуйте снова.");
            }
            else
            {
                List<ReleaseInfo> releases = null;
                try
                {
                    releases = JsonConvert.DeserializeObject<List<ReleaseInfo>>(
                        request.downloadHandler.text);
                }
                catch (JsonException ex)
                {
                    Debug.LogWarning("[FoxCraft] Invalid release response: " + ex.Message);
                }

                if (releases == null || releases.Count == 0)
                {
                    SetChangelog("Релизы FoxCraft пока не опубликованы.");
                }
                else
                {
                    StringBuilder builder = new StringBuilder();
                    string latestStableVersion = null;

                    foreach (ReleaseInfo release in releases)
                    {
                        if (release == null || release.draft)
                            continue;

                        if (!release.prerelease && latestStableVersion == null &&
                            !string.IsNullOrWhiteSpace(release.tag_name))
                            latestStableVersion = release.tag_name;

                        string title = string.IsNullOrWhiteSpace(release.name)
                            ? release.tag_name : release.name;
                        string date = "";
                        if (DateTime.TryParse(release.published_at, out DateTime published))
                            date = published.ToString("yyyy-MM-dd");

                        builder.Append("<size=75%>").Append(date).Append("</size>\n");
                        builder.Append("<size=120%><u>").Append(title ?? "FoxCraft")
                            .Append("</u></size>\n");
                        builder.Append(release.body ?? "").Append("\n\n");
                    }

                    SetChangelog(builder.Length == 0
                        ? "Релизы FoxCraft пока не опубликованы."
                        : builder.ToString());

                    // Do not claim an update based on a draft, a prerelease or an empty feed.
                    if (versionChecker != null && latestStableVersion != null)
                    {
                        versionChecker.currentPublicVersion = latestStableVersion;
                        versionChecker.CheckVersion();
                    }
                }
            }
        }

        yield return new WaitForSecondsRealtime(10);
        cooldown = false;
    }

    private void SetChangelog(string text)
    {
        if (tags != null)
            tags.text = text;
    }

    public void ScrollTop() => StartCoroutine(ScrollToTop());

    private IEnumerator ScrollToTop()
    {
        yield return new WaitForEndOfFrame();
        if (changelog != null)
            changelog.verticalNormalizedPosition = 1f;
    }
}
