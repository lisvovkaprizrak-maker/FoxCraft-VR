# FoxCraft custom VR keyboard — first implementation

The first FoxCraft keyboard component is in `Assets/Scripts/FoxCraft/UI/FoxCraftKeyboard.cs`.

## Scope

- Runtime-generated world-space Unity UI keyboard with a dark/navy and orange visual theme, rounded key backgrounds, highlighted hover states and a text-preview panel.
- English QWERTY, Russian ЙЦУКЕН and digits/punctuation.
- Shift, space, backspace (including UTF-16 surrogate pairs), left/right cursor movement, submit and cancel.
- Opens against `TMP_InputField` via `ShowFor(field)`, or standalone via `ShowWithText(initialText)`.
- Emits `OnTextChanged(string)`, `OnSubmitted(string)` and `OnCancelled` for an integration adapter.

**Not yet implemented:** Minecraft Java text bridge, input-method composition/selection, exact 3D key geometry, hand-poke interaction, controller focus locking, persistence across game/Unity boundaries or device testing. This is a Unity-side prototype, not a playable keyboard inside Minecraft.

## Set up in a launcher scene

1. Create a GameObject named `FoxCraftKeyboardController` and add the `FoxCraftKeyboard` component.
2. Assign a TMP font asset with Cyrillic glyph coverage. The default TMP font may not support Russian characters.
3. Optionally assign the headset/camera `placementAnchor`, to show the board in front of the user. Adjust `localOffset` and `worldScale` for comfortable distance and size.
4. Configure an **EventSystem** with **XR UI Input Module** (avoid multiple input modules).
5. The generated canvas now has **TrackedDeviceGraphicRaycaster** from XR Interaction Toolkit 3.0.5. Configure an XR Ray Interactor with UI interaction enabled; a compatible EventSystem and its XR UI Input Module are still required. The canvas event camera is inferred from the placement anchor's Camera or `Camera.main`—verify it is populated.
6. Call `keyboard.ShowFor(yourTmpInputField)` from the input field's select event or from an explicit edit button.
7. Verify that controller clicks trigger `Button.onClick`, the text field updates, and the keyboard can be dismissed.

## Java/Minecraft integration contract

The keyboard emits entire edited **text snapshots** rather than raw key codes. Integrate through a **separate adapter** that determines which Minecraft text field is focused, forwards changes to the Java runtime and sends submit/cancel actions. Do not interpret OnTextChanged as a chat-send event. Also suppress gameplay input when the keyboard is active.

For an implementation inside Minecraft/Vivecraft, investigate the existing Android/JNI input adapter before connecting this Unity component. This project has not yet verified a working cross-runtime text injection path.

## Known prototype limitations

- UI is generated in play mode and does not yet use designed art assets or prefabs.
- Input selection, clipboard, dead keys and IME composition are not supported.
- Cursor movement operates on UTF-16 code points, not full Unicode grapheme clusters.
- Keys now have rounded 2D styling and hover highlighting; true curved 3D geometry, animated presses, sound and haptics remain future enhancements.
- The approved illustrative mockup is a design target, not a pixel-perfect screenshot of the current Unity UI.
- There are no automated Unity play-mode tests or confirmed on-device results.

## Launcher field binding

Attach `FoxCraftKeyboardFieldBinding` to a `TMP_InputField` and assign the scene keyboard in the Inspector. Selecting the field calls `FoxCraftKeyboard.ShowFor(field)`; `Open()` may also be connected to a button.

## Game text input coordinator

`FoxCraftGameTextInputCoordinator` controls one game text session. It is intentionally not a Java/JNI bridge.

1. When Minecraft focuses an editable field, an independently implemented game bridge calls `OpenFromGame(currentText)`.
2. `OnGameTextChanged(string)` sends complete updated text snapshots to the bridge. It must target the active Minecraft text field, never global gameplay controls.
3. `OnGameTextSubmitted(string)` signals completed entry and closes the session.
4. `OnGameTextCancelled` signals cancellation. The bridge must restore its pre-edit text snapshot or discard provisional changes.
5. `OnGameKeyboardOpened` and `OnGameKeyboardClosed` can be used to pause/resume gameplay controller input while editing.
6. `CloseFromGame()` dismisses the keyboard if the game loses text focus.

**Important:** This is the Unity-side interface contract only. It will not type into Minecraft until the QuestCraft/Pojlib runtime is explicitly connected, including a consistent text-focus lifecycle. Do not call `OpenFromGame` merely upon launching the game.

## Next verification tasks

- Open the Unity project in its pinned editor version and compile the scripts.
- Configure the scene XR UI Input Module, XR Ray Interactor and a Cyrillic-capable TMP font.
- Test launcher field selection, text preview, character deletion, cancel and Enter on Meta Quest hardware.
- Locate the exact Java-side field/input handler in the upstream QuestCraft/Pojlib code and implement a JNI bridge with a single text-editing authority.

## Integration update: existing launcher scene

`Assets/Scripts/KeyboardHandler.cs` now prefers the custom keyboard and automatically creates a `FoxCraftKeyboard` component when needed. It routes the existing search, RAM and instance-name fields. Clicking a keyboard key does not dismiss the input session; clicking another UI element closes it. Existing prefab button methods remain as a fallback.

**Scene hierarchy caution:** if the old keyboard GameObject contains the `KeyboardHandler` component, it cannot simply be deactivated without disabling the handler. Move the handler to a separate active object before removing the old prefab. A warning is logged in this case. The new keyboard falls back to a tagged `MainCamera` when a placement anchor has not been assigned.

## Editor scene check

Open the scene in Unity and select `Tools → FoxCraft → Validate Keyboard Scene`. The non-mutating check reports the presence of a keyboard handler, EventSystem, XR UI input module, tagged MainCamera and game input coordinator.

The checker does **not** validate Java focus callbacks, sign an APK, compile the project or run on a VR device.

## Known runtime integration status

- Launcher focus routing exists in code but has not been compiled or headset-tested.
- `FoxCraftGameFocusReceiver` provides optional `UnitySendMessage` entry points for Minecraft-side focus signals. Both its receive toggle and the native GLFW injection toggle are disabled by default.
- A Minecraft/Vivecraft mod or Pojlib callback must call `OpenTextField` / `CloseTextField` when a genuine game text field gains or loses focus.
- See `FOXCRAFT_GLFW_BRIDGE.md` for input JNI details and remaining limitations.


## Current Unity editor workflow (2026-11)

1. Open the project using the editor version from `ProjectSettings/ProjectVersion.txt`.
2. Open the launcher's main scene. In the menu, choose **FoxCraft → Install Keyboard In Open Scene**.
3. The installer creates/reuses the `FoxCraftGameTextBridge` object, adds keyboard/coordinator/focus/JNI components, and connects existing `KeyboardHandler` components to the new keyboard.
4. Save the modified scene **only after inspecting** the new object and assigned input fields. This guide and installer have not themselves saved or verified a working scene.
5. Run **FoxCraft → Validate Keyboard Setup**. Review warnings for the XR UI module, main camera and a TextMeshPro font with Cyrillic glyphs.
6. Enter Play Mode, then choose **FoxCraft → Run Keyboard Smoke Checks**. This checks local keyboard editing and event lifecycle, **not** Android JNI, headsets or a Minecraft world.
7. In a Quest development build, use the **Probe Pojlib GLFW API (no input)** context menu on `FoxCraftPojlibGlfwInputBridge` to check whether the embedded AAR exposes the required Java signatures. This does not inject input.
8. Leave `acceptGameFocusMessages` and `enableNativeInput` **disabled** until Minecraft's focused-field events are integrated and tested.

### VR positioning and focus constraints

- The keyboard defaults to a stationary world-space panel, positioned in front of `placementAnchor` or `MainCamera`. Toggle `followHeadMovement` only for a head-following interface.
- Its front is oriented toward the user. Real controller interaction additionally requires an XR UI Input Module and a UI-capable ray interactor in the scene.
- The old launcher keyboard's button callbacks are kept for compatibility, but the legacy input handler must not forward text while a game text session owns the keyboard.
- The game text bridge is intentionally not an automatic keyboard: it requires a trusted Minecraft-side open/close callback.
- `ShowFor` refuses to replace an active game text session; hidden keyboard edits are ignored.
