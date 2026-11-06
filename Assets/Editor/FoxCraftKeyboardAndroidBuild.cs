// Manual/gated Android test build entry point for Minecraft 1.21.5.
// The test APK uses a separate Android package ID, leaving QuestCraft installed.
#if UNITY_EDITOR
using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

public static class FoxCraftKeyboardAndroidBuild
{
    public const string TestPackageId = "com.foxcraftvr.quest";
    private const string FabricJar = "foxcraft-keyboard-0.1.0-alpha.jar";
    private const string OutputFile = "FoxCraft-1.21.5-UNTESTED.apk";

    [MenuItem("FoxCraft/Build 1.21.5 keyboard test APK")]
    public static void BuildTestApk()
    {
        if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.Android)
            throw new InvalidOperationException("Choose Android build target before building FoxCraft.");

        string root = Directory.GetCurrentDirectory();
        string staged = Path.Combine(root, "Assets/StreamingAssets", FabricJar);
        string pojlib = Path.Combine(root, "Assets/Plugins/Android/Pojlib-release.aar");

        // Never silently produce an APK missing its game runtime or keyboard.
        VerifyAsset(staged, "Fabric 1.21.5 FoxCraft keyboard JAR");
        VerifyAsset(pojlib, "Pojlib Android AAR");

        string[] scenes = EditorBuildSettings.scenes
            .Where(scene => scene.enabled).Select(scene => scene.path).ToArray();
        if (scenes.Length == 0)
            throw new InvalidOperationException("No enabled scenes found in Unity build settings.");

        string initialId = PlayerSettings.GetApplicationIdentifier(BuildTargetGroup.Android);
        if (initialId != "com.qcxr.qcxr" && initialId != TestPackageId)
            throw new InvalidOperationException("Unexpected source package ID: " + initialId);

        string initialName = PlayerSettings.productName;
        string initialCompany = PlayerSettings.companyName;
        string initialVersion = PlayerSettings.bundleVersion;
        int initialCode = PlayerSettings.Android.bundleVersionCode;
        AndroidArchitecture initialArchitectures = PlayerSettings.Android.targetArchitectures;
        bool initialExport = EditorUserBuildSettings.exportAsGoogleAndroidProject;

        string output = Path.Combine(root, "Builds", OutputFile);
        Directory.CreateDirectory(Path.GetDirectoryName(output));

        try
        {
            PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, TestPackageId);
            PlayerSettings.productName = "FoxCraft VR";
            PlayerSettings.companyName = "FoxCraft Project";
            PlayerSettings.bundleVersion = "0.1.0-alpha";
            PlayerSettings.Android.bundleVersionCode = 101;
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            EditorUserBuildSettings.exportAsGoogleAndroidProject = false;

            BuildReport report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
            {
                scenes = scenes,
                locationPathName = output,
                target = BuildTarget.Android,
                options = BuildOptions.None
            });

            if (report == null || report.summary.result != BuildResult.Succeeded)
                throw new InvalidOperationException("Unity Android build failed; inspect Editor.log.");

            Debug.Log("[FoxCraft] UNTESTED Quest APK generated: " + output);
            Debug.Log("[FoxCraft] Hardware, VR input, Minecraft GUI and Pojlib install still require verification.");
        }
        finally
        {
            PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, initialId);
            PlayerSettings.productName = initialName;
            PlayerSettings.companyName = initialCompany;
            PlayerSettings.bundleVersion = initialVersion;
            PlayerSettings.Android.bundleVersionCode = initialCode;
            PlayerSettings.Android.targetArchitectures = initialArchitectures;
            EditorUserBuildSettings.exportAsGoogleAndroidProject = initialExport;
        }
    }

    private static void VerifyAsset(string path, string description)
    {
        if (!File.Exists(path) || new FileInfo(path).Length < 1024)
            throw new FileNotFoundException(description + " is missing or invalid. " +
                "Stage the verified JAR and check out the Pojlib AAR before building.", path);
    }
}
#endif
