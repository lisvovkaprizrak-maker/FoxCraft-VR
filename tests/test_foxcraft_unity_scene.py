"""Check that the actual Unity scene still wires QuestCraft's flat/VR menus.

Static checks complement the C# runtime tests. Device rendering must still be
verified on physical Quest and Android handsets.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCENE = ROOT / "Assets/Scenes/Main.unity"


def object_block(scene: str, object_type: int, file_id: str) -> str:
    expression = rf"^--- !u!{object_type} &{re.escape(str(file_id))}\n(.*?)(?=^--- !u!|\Z)"
    match = re.search(expression, scene, re.MULTILINE | re.DOTALL)
    return match.group(1) if match else ""


def script_guid(filename: str) -> str:
    text = (ROOT / f"Assets/Scripts/{filename}.cs.meta").read_text(encoding="utf-8")
    match = re.search(r"^guid: ([0-9a-f]{32})$", text, re.MULTILINE)
    if not match:
        raise ValueError("Missing Unity MonoScript GUID: " + filename)
    return match.group(1)


class FoxCraftSceneWiringTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scene = SCENE.read_text(encoding="utf-8")

    def test_original_menu_and_account_components_are_wired(self):
        for script in ("UIHandler", "JNIStorage", "LoginHandler", "ConfigHandler"):
            with self.subTest(script=script):
                self.assertIn("guid: " + script_guid(script), self.scene)
        for item in ("MainCanvas", "EventSystem", "DesktopCamera", "Main Camera"):
            self.assertIn("m_Name: " + item + "\n", self.scene)

    def test_flat_camera_is_not_child_of_vr_camera(self):
        # Root-level flat camera survives when DesktopCam disables the VR rig
        # on ordinary Android phones.
        desktop = object_block(self.scene, 1, "879282673")
        self.assertIn("m_Name: DesktopCamera\n", desktop)
        transform = object_block(self.scene, 4, "879282676")
        self.assertIn("m_GameObject: {fileID: 879282673}", transform)
        self.assertIn("m_Father: {fileID: 0}", transform)
        camera = object_block(self.scene, 20, "879282675")
        self.assertIn("m_GameObject: {fileID: 879282673}", camera)

    def test_original_desktop_cam_retargets_all_menus(self):
        match = re.search(
            r"^--- !u!114 &[-0-9]+\n((?:(?!^--- !u!).)*?guid: "
            + script_guid("DesktopCam")
            + r"(?:(?!^--- !u!).)*?)(?=^--- !u!|\Z)",
            self.scene, re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(match, "DesktopCam MonoBehaviour missing from Main.unity")
        component = match.group(1)
        self.assertIn("VrCam: {fileID: 1079186315}", component)
        self.assertIn("DesktopCamera: {fileID: 879282673}", component)
        for canvas in ("1895559470", "328686344"):
            self.assertIn(f"- {{fileID: {canvas}}}", component)
        main_canvas = object_block(self.scene, 223, "1895559470")
        self.assertIn("m_RenderMode: 2", main_canvas)
        self.assertIn("m_GameObject: {fileID: 1895559465}", main_canvas)

    def test_phone_mode_is_attached_without_replacing_questcraft_scene(self):
        ui = (ROOT / "Assets/Scripts/UIHandler.cs").read_text(encoding="utf-8")
        self.assertIn("AddComponent<FoxCraftLaunchModeMenu>()", ui)
        self.assertIn("AddComponent<FoxCraftPhoneTouchInput>()", ui)
        camera = (ROOT / "Assets/Scripts/DesktopCam.cs").read_text(encoding="utf-8")
        self.assertIn("FoxCraftDevice.IsVrHeadset", camera)
        self.assertIn("screenCanvas.worldCamera = flatCamera", camera)


if __name__ == "__main__":
    unittest.main()
