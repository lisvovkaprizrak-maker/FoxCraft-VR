import io
import tempfile
import unittest
import zipfile
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from foxcraft_stage_pojlib import stage_aar, validate_aar


def make_aar(path: Path, *, missing_manifest: bool = False, marker: str = "a"):
    classes = io.BytesIO()
    with zipfile.ZipFile(classes, "w") as inner:
        inner.writestr("com/foxcraft/Example.class", marker.encode() * 32)
    with zipfile.ZipFile(path, "w") as outer:
        if not missing_manifest:
            outer.writestr("AndroidManifest.xml", "<manifest package='example'/>")
        outer.writestr("classes.jar", classes.getvalue())


class PojlibStagingTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.source = self.root / "Pojlib-release.aar"
        self.destination = self.root / "Assets/Plugins/Android/Pojlib-release.aar"

    def tearDown(self):
        self.tmp.cleanup()

    def test_stage_and_idempotence(self):
        make_aar(self.source)
        expected = validate_aar(self.source)
        self.assertEqual(stage_aar(self.source, self.destination), "STAGED")
        self.assertEqual(validate_aar(self.destination), expected)
        self.assertEqual(stage_aar(self.source, self.destination), "UNCHANGED")

    def test_overwrite_requires_explicit_replace(self):
        make_aar(self.source)
        stage_aar(self.source, self.destination)
        make_aar(self.source, marker="b")
        with self.assertRaisesRegex(ValueError, "explicit --replace"):
            stage_aar(self.source, self.destination)
        self.assertEqual(stage_aar(self.source, self.destination, replace=True), "STAGED")

    def test_reject_missing_manifest(self):
        make_aar(self.source, missing_manifest=True)
        with self.assertRaisesRegex(ValueError, "AndroidManifest.xml"):
            validate_aar(self.source)

    def test_reject_bad_zip(self):
        self.source.write_bytes(b"not an archive" * 30)
        with self.assertRaisesRegex(ValueError, "Invalid Pojlib"):
            validate_aar(self.source)

    def test_reject_empty_classes_jar(self):
        with zipfile.ZipFile(self.source, "w") as outer:
            outer.writestr("AndroidManifest.xml", "<manifest/>")
            outer.writestr("classes.jar", b"")
        with self.assertRaisesRegex(ValueError, "Empty Pojlib"):
            validate_aar(self.source)


if __name__ == "__main__":
    unittest.main()
