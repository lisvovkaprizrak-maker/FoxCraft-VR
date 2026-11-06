import json
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from foxcraft_check_fabric_compat import check_compatibility


class FabricCompatibilityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.jar = Path(self.temp.name) / "keyboard.jar"

    def tearDown(self):
        self.temp.cleanup()

    def make_jar(self, version):
        with ZipFile(self.jar, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({
                "id": "questchatkeyboardbridge",
                "name": "FoxChat Keyboard",
                "depends": {"minecraft": version}
            }))

    def test_1211_mod_rejected_for_1215(self):
        self.make_jar("1.21.1")
        ok, explanation = check_compatibility(self.jar, "1.21.5")
        self.assertFalse(ok)
        self.assertIn("DO NOT install", explanation)
        self.assertIn("1.21.1", explanation)
        self.assertIn("1.21.5", explanation)

    def test_matching_version_is_only_metadata_match(self):
        self.make_jar("1.21.1")
        ok, explanation = check_compatibility(self.jar, "1.21.1")
        self.assertTrue(ok)
        self.assertIn("NOT a runtime compatibility test", explanation)

    def test_missing_metadata_is_rejected(self):
        with ZipFile(self.jar, "w") as jar:
            jar.writestr("unrelated.txt", "test")
        ok, _ = check_compatibility(self.jar, "1.21.5")
        self.assertFalse(ok)

    def test_missing_version_is_rejected(self):
        self.make_jar("*")
        ok, _ = check_compatibility(self.jar, "1.21.5")
        self.assertFalse(ok)


if __name__ == "__main__":
    unittest.main()
