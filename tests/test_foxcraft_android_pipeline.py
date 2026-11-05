import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class AndroidApkWorkflowTests(unittest.TestCase):
    def test_manual_build_and_pr_smoke_are_isolated(self):
        workflow = (ROOT / ".github/workflows/foxcraft-android-apk.yml").read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("pull_request:", workflow)
        self.assertGreaterEqual(
            workflow.count("if: github.event_name == 'workflow_dispatch'"), 4,
            "License gate and expensive jobs must only run on manual dispatch",
        )
        self.assertIn("secrets.UNITY_LICENSE", workflow)
        self.assertIn("buildMethod: FoxCraftAndroidBuild.BuildTestApk", workflow)
        self.assertIn("unityVersion: 2022.3.62f3", workflow)
        self.assertIn("targetPlatform: Android", workflow)

    def test_both_ci_artifacts_staged_before_unity_build(self):
        workflow = (ROOT / ".github/workflows/foxcraft-android-apk.yml").read_text(encoding="utf-8")
        self.assertIn("tools/foxcraft_stage_fabric.py --source", workflow)
        self.assertIn("tools/foxcraft_stage_pojlib.py --source", workflow)
        self.assertIn("FoxCraft-VR-Quest3-UNTESTED-APK", workflow)
        self.assertIn("if-no-files-found: error", workflow)
        self.assertLess(
            workflow.index("Validate and stage both Android dependencies"),
            workflow.index("Build separate FoxCraft Android test APK with Unity"),
        )

    def test_apk_build_keeps_original_questcraft_separate(self):
        source = (ROOT / "Assets/Editor/FoxCraftAndroidBuild.cs").read_text(encoding="utf-8")
        self.assertIn('TestPackageId = "com.foxcraftvr.quest"', source)
        self.assertIn("PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, originalId)", source)
        self.assertIn("finally", source)
        self.assertIn("BuildResult.Succeeded", source)


if __name__ == "__main__":
    unittest.main()
