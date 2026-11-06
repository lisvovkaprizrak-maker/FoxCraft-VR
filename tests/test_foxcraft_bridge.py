import base64
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class FoxCraftBridgeIntegrationTests(unittest.TestCase):
    def test_questcraft_manifest_declares_meta_overlay(self):
        doc = ET.parse(ROOT / "Assets/Plugins/Android/AndroidManifest.xml").getroot()
        features = [
            node for node in doc.findall("uses-feature")
            if node.attrib.get(ANDROID + "name") == "oculus.software.overlay_keyboard"
        ]
        self.assertEqual(len(features), 1)
        self.assertEqual(features[0].attrib.get(ANDROID + "required"), "false")

    def test_existing_launch_activity_and_focusaware_preserved(self):
        doc = ET.parse(ROOT / "Assets/Plugins/Android/AndroidManifest.xml").getroot()
        app = doc.find("application")
        self.assertIsNotNone(app)
        activities = app.findall("activity")
        self.assertIn("pojlib.UnityPlayerActivity",
                      [a.attrib.get(ANDROID + "name") for a in activities])
        meta = app.findall("meta-data")
        self.assertIn("com.oculus.vr.focusaware",
                      [item.attrib.get(ANDROID + "name") for item in meta])

    def test_qckb1_unity_source_uses_session_and_safe_exchange(self):
        src = (ROOT / "Assets/Scripts/QuestChatKeyboardOverlayBridge.cs").read_text(encoding="utf8")
        for token in ("QCKB1", '"OPEN"', '"CLOSE"', '"SUBMIT"', '"COMMIT"',
                      '"CANCEL"', "Guid.TryParse", "response.tmp",
                      "TouchScreenKeyboard.Open", "OnApplicationFocus",
                      "OnApplicationPause"):
            with self.subTest(token=token):
                self.assertIn(token, src)
        self.assertNotIn('Debug.Log(keyboard.text)', src)
        self.assertIn("Char.IsHighSurrogate(safeText[limit - 1])", src)
        self.assertIn("Char.IsLowSurrogate(safeText[limit])", src)

    def test_russian_text_wire_encoding_is_utf8_base64(self):
        text = "Привет, FoxCraft! Ёж 🦊"
        encoded = base64.b64encode(text.encode("utf-8")).decode("ascii")
        packet = f"QCKB1\nCOMMIT\n123e4567-e89b-12d3-a456-426614174000\n{encoded}\n"
        data = packet.splitlines()
        self.assertEqual(data[0], "QCKB1")
        self.assertEqual(data[1], "COMMIT")
        self.assertEqual(base64.b64decode(data[3]).decode("utf-8"), text)

    def test_no_unintended_android_permissions_added(self):
        doc = ET.parse(ROOT / "Assets/Plugins/Android/AndroidManifest.xml").getroot()
        perms = [p.attrib.get(ANDROID + "name", "") for p in doc.findall("uses-permission")]
        self.assertNotIn("android.permission.SYSTEM_ALERT_WINDOW", perms)
        self.assertNotIn("android.permission.BIND_INPUT_METHOD", perms)


class FoxCraftInstanceVersionTests(unittest.TestCase):
    def test_unity_fabric_targets_match_minecraft_1215(self):
        import json
        metadata = json.loads((ROOT / "Fabric/foxchatkeyboard/src/main/resources/fabric.mod.json").read_text(encoding="utf-8"))
        target = metadata["depends"]["minecraft"]
        self.assertEqual(target, "1.21.5")
        unity_bridge = (ROOT / "Assets/Scripts/QuestChatKeyboardOverlayBridge.cs").read_text(encoding="utf-8")
        installer = (ROOT / "Assets/Scripts/FoxCraftFabricModInstaller.cs").read_text(encoding="utf-8")
        self.assertIn(f'InstanceName = "{target}"', unity_bridge)
        self.assertIn(f'Instance = "{target}"', installer)
        self.assertIn('JarName = "FoxChatKeyboard-bridge-0.6.1.jar"', installer)


if __name__ == "__main__":
    unittest.main()
