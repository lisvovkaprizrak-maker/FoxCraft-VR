import json
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from foxcraft_preflight import check


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = Path(self.dir.name)
        for sub in ('Assets', 'Packages', 'ProjectSettings'):
            (self.root / sub).mkdir()
        (self.root / 'ProjectSettings/ProjectVersion.txt').write_text('m_EditorVersion: 2022.3.62f3\n')
        (self.root / 'Packages/manifest.json').write_text(json.dumps({'dependencies': {'com.unity.xr.openxr': '1.14.3'}}))

    def tearDown(self):
        self.dir.cleanup()

    def test_valid_project(self):
        self.assertEqual(check(self.root), [])

    def test_wrong_unity_version(self):
        (self.root / 'ProjectSettings/ProjectVersion.txt').write_text('m_EditorVersion: 2022.3.62f2\n')
        self.assertTrue(any('Unity version' in x for x in check(self.root)))

    def test_missing_openxr(self):
        (self.root / 'Packages/manifest.json').write_text(json.dumps({'dependencies': {}}))
        self.assertTrue(any('OpenXR version' in x for x in check(self.root)))

    def test_invalid_manifest(self):
        (self.root / 'Packages/manifest.json').write_text('{broken')
        self.assertTrue(any('Invalid' in x for x in check(self.root)))


if __name__ == '__main__':
    unittest.main()
