// FoxCraft test build entry point. Run with Unity 2022.3.62f3 Android Build Support.
// -executeMethod FoxCraftAndroidBuild.BuildTestApk
// Deliberately refuses to build until Pojlib and the matching Fabric bridge are available.
#if UNITY_EDITOR
using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

public static class FoxCraftAndroidBuild
{
    public const string TestPackageId = "com.foxcraftvr.quest";
    public const string ProductName = "FoxCraft VR";

    [MenuItem("FoxCraft/Build separate Android test APK")]
    public static void BuildTestApk()
    {
        if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.Android)
            throw new InvalidOperationException("Switch the Unity build target to Android.");

        string root = Directory.GetCurrentDirectory();
        string pojlib = Path.Combine(root, "Assets/Plugins/Android/Pojlib-release.aar");
        string fabric = Path.Combine(root, "Assets/StreamingAssets/FoxChatKeyboard-bridge-0.6.0.jar");

        if (!File.Exists(pojlib) || new FileInfo(pojlib).Length < 1000)
            throw new FileNotFoundException(
                "Pojlib-release.aar is required; compile the QuestCraft 6.0.0 Pojlib branch first.", pojlib);
        if (!File.Exists(fabric) || new FileInfo(fabric).Length < 1000)
            throw new FileNotFoundException(
                "Matching QCKB1 Fabric JAR is required in StreamingAssets.", fabric);

        string[] scenes = EditorBuildSettings.scenes.Where(s => s.enabled)
            .Select(s => s.path).ToArray();
        if (scenes.Length == 0)
            throw new InvalidOperationException("No enabled Unity scenes.");

        string originalId = PlayerSettings.GetApplicationIdentifier(BuildTargetGroup.Android);
        if (originalId != "com.qcxr.qcxr" && originalId != TestPackageId)
            throw new InvalidOperationException("Unexpected source package ID: " + originalId);

        string originalName = PlayerSettings.productName;
        string originalCompany = PlayerSettings.companyName;
        string originalVersion = PlayerSettings.bundleVersion;
        int originalCode = PlayerSettings.Android.bundleVersionCode;
        bool originalExport = EditorUserBuildSettings.exportAsGoogleAndroidProject;

        string outputDir = Path.Combine(root, "Builds");
        Directory.CreateDirectory(outputDir);
        string apk = Path.Combine(outputDir, "FoxCraft-VR-test.apk");

        try
        {
            // Do not change the identifier of the installed QuestCraft.
            // Unity PlayerSettings are restored even when building fails.
            PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, TestPackageId);
            PlayerSettings.productName = ProductName;
            PlayerSettings.companyName = "FoxCraft Project";
            PlayerSettings.bundleVersion = "0.8.0-alpha";
            PlayerSettings.Android.bundleVersionCode = 80;

            // The source AndroidManifest.xml already declares the optional overlay.
            // Direct Android APK packaging avoids a separate Gradle export step.
            EditorUserBuildSettings.exportAsGoogleAndroidProject = false;
            BuildReport report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = scenes,
                locationPathName = apk,
                target = BuildTarget.Android,
                options = BuildOptions.None
            });

            if (report == null || report.summary.result != BuildResult.Succeeded)
                throw new InvalidOperationException("Unity Android build failed. Read Editor.log.");
            Debug.Log("[FoxCraft] Test APK built: " + apk);
            Debug.Log("[FoxCraft] Meta overlay must be tested on Quest 3; a successful build is not proof.");
        }
        finally
        {
            PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, originalId);
            PlayerSettings.productName = originalName;
            PlayerSettings.companyName = originalCompany;
            PlayerSettings.bundleVersion = originalVersion;
            PlayerSettings.Android.bundleVersionCode = originalCode;
            EditorUserBuildSettings.exportAsGoogleAndroidProject = originalExport;
        }
    }
}
#endif
