import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from foxcraft_stage_fabric import stage_jar, validate_jar


class StageFabricTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.jar = self.root / "input.jar"
        self.dest = self.root / "Assets/StreamingAssets/FoxChatKeyboard-bridge-0.6.1.jar"
        with zipfile.ZipFile(self.jar, "w") as z:
            z.writestr("fabric.mod.json", json.dumps({
                "id": "questchatkeyboardbridge",
                "version": "0.6.1",
                "depends": {"minecraft": "1.21.5"}}))
            z.writestr("com/questchatkeyboard/bridge/QuestChatKeyboardBridge.class", b"X" * 1800)

    def tearDown(self):
        self.tmp.cleanup()

    def test_stages_valid_artifact(self):
        self.assertEqual(stage_jar(self.jar, self.dest), "STAGED")
        self.assertEqual(self.jar.read_bytes(), self.dest.read_bytes())

    def test_repeated_stage_does_not_rewrite(self):
        stage_jar(self.jar, self.dest)
        self.assertEqual(stage_jar(self.jar, self.dest), "UNCHANGED")

    def test_conflicting_staged_artifact_blocked(self):
        stage_jar(self.jar, self.dest)
        old = self.dest.read_bytes()
        with zipfile.ZipFile(self.jar, "a") as z:
            z.writestr("extra.txt", "different")
        with self.assertRaisesRegex(ValueError, "explicit --replace"):
            stage_jar(self.jar, self.dest)
        self.assertEqual(self.dest.read_bytes(), old)

    def test_invalid_mod_id_rejected(self):
        other = self.root / "wrong.jar"
        with zipfile.ZipFile(other, "w") as z:
            z.writestr("fabric.mod.json", json.dumps({"id": "other", "version": "0.6.1",
                                                       "depends": {"minecraft": "1.21.5"}}))
            z.writestr("com/questchatkeyboard/bridge/QuestChatKeyboardBridge.class", b"X" * 1800)
        with self.assertRaisesRegex(ValueError, "Unexpected Fabric mod id"):
            validate_jar(other)

    def test_missing_class_rejected(self):
        other = self.root / "missing.jar"
        with zipfile.ZipFile(other, "w") as z:
            z.writestr("fabric.mod.json", json.dumps({"id": "questchatkeyboardbridge",
                         "version": "0.6.1", "depends": {"minecraft": "1.21.5"}}))
            z.writestr("unrelated.class", b"X" * 1800)
        with self.assertRaisesRegex(ValueError, "entrypoint"):
            validate_jar(other)


if __name__ == "__main__":
    unittest.main()
