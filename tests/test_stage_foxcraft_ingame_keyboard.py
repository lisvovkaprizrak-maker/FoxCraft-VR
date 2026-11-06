"""Offline tests for staging only the correct FoxCraft Fabric mod into Unity assets."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "stage_foxcraft_ingame_keyboard.py"
spec = importlib.util.spec_from_file_location("foxcraft_stage_ingame_keyboard", SCRIPT)
stage_module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(stage_module)


class StageKeyboardTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "source.jar"
        self.destination = self.root / "unity" / "keyboard.jar"

    def jar(self, target, mod_id="foxcraft_keyboard", minecraft="1.21.5", marker="a"):
        target.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(target, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({
                "id": mod_id, "depends": {"minecraft": minecraft}
            }))
            jar.writestr("dummy/payload.txt", marker * 2100)
            jar.writestr("dev/foxcraft/keyboard/FoxCraftKeyboardClient.class", b"CAFEBABE")
            jar.writestr("dev/foxcraft/keyboard/VirtualKeyboardOverlay.class", b"CAFEBABE")
            jar.writestr("dev/foxcraft/keyboard/KeyboardPointerInput.class", b"CAFEBABE")

    def test_copy_and_idempotent(self):
        self.jar(self.source)
        self.assertIn("Staged:", stage_module.stage(self.source, self.destination, False))
        self.assertIn("Already staged:", stage_module.stage(self.source, self.destination, False))
        self.assertEqual(self.source.read_bytes(), self.destination.read_bytes())

    def test_wrong_mod_id_rejected(self):
        self.jar(self.source, mod_id="not_foxcraft")
        with self.assertRaises(ValueError):
            stage_module.stage(self.source, self.destination, False)
        self.assertFalse(self.destination.exists())

    def test_incompatible_game_version_rejected(self):
        self.jar(self.source, minecraft="1.21.1")
        with self.assertRaises(ValueError):
            stage_module.stage(self.source, self.destination, False)
        self.assertFalse(self.destination.exists())

    def test_different_jar_never_overwritten_without_permission(self):
        self.jar(self.source, marker="a")
        self.jar(self.destination, marker="b")
        before = self.destination.read_bytes()
        with self.assertRaises(ValueError):
            stage_module.stage(self.source, self.destination, False)
        self.assertEqual(before, self.destination.read_bytes())
        self.assertIn("Staged:", stage_module.stage(self.source, self.destination, True))
        self.assertEqual(self.source.read_bytes(), self.destination.read_bytes())

    def test_corrupt_existing_jar_is_preserved_without_replace(self):
        self.jar(self.source)
        self.destination.parent.mkdir(parents=True, exist_ok=True)
        self.destination.write_bytes(b"not a valid jar")
        with self.assertRaises(ValueError):
            stage_module.stage(self.source, self.destination, False)
        self.assertEqual(b"not a valid jar", self.destination.read_bytes())
        stage_module.stage(self.source, self.destination, True)
        self.assertEqual(self.source.read_bytes(), self.destination.read_bytes())

    def test_missing_fabric_entrypoint_is_rejected(self):
        with ZipFile(self.source, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps({
                "id": "foxcraft_keyboard", "depends": {"minecraft": "1.21.5"}
            }))
            jar.writestr("dummy/payload.txt", "x" * 3000)
        with self.assertRaisesRegex(ValueError, "Required keyboard class missing"):
            stage_module.stage(self.source, self.destination, False)
        self.assertFalse(self.destination.exists())

    def test_corrupt_source_rejected(self):
        self.source.write_bytes(b"not zip")
        with self.assertRaises(ValueError):
            stage_module.stage(self.source, self.destination, False)


if __name__ == "__main__":
    unittest.main()
