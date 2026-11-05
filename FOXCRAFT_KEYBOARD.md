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
