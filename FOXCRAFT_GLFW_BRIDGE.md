# FoxCraft → QuestCraft: experimental GLFW keyboard bridge

**Status: code written, not compiled or validated on a headset.** Do not enable input injection until the game has an actual focused text field.

## Upstream APIs checked in source

Upstream `QuestCraftPlusPlus/Pojlib`, branch `QuestCraft-6.0.0`:

- `src/main/java/org/lwjgl/glfw/CallbackBridge.java`: static `sendChar(char, int)`, `sendKeyPress(int)`.
- `src/main/java/pojlib/input/LwjglGlfwKeycode.java`: key constants (Escape 256, Enter 257, Backspace 259, Right 262, Left 263).
- `src/main/java/pojlib/UnityPlayerActivity.java`: configures an input event queue in `onCreate()`.

FoxCraft ships `Assets/Plugins/Android/Pojlib-release.aar` and uses `pojlib.API` in `JNIStorage.cs`. **The bundled AAR has not been binary-inspected** for exact exports; verify at runtime.

## Components

- `FoxCraftKeyboard`: world-space keyboard, RU/EN layout, character insertion and edit key events.
- `FoxCraftGameTextInputCoordinator`: owns the editing session, opens via `OpenFromGame(initialText)`, and publishes change, submit and cancel events.
- `FoxCraftPojlibGlfwInputBridge`: invokes Java `org.lwjgl.glfw.CallbackBridge` using Unity Android JNI. **Native injection disabled by default.**

Place all three on the same active scene object, or assign references in the Inspector. The bridge subscribes automatically to coordinator events.

## What is still required for real Minecraft text input

1. Find or implement a trustworthy game-side signal identifying an actual focused Minecraft editable screen (chat, command box, server address, world naming). The launcher cannot infer editable-field focus solely from OpenXR, or from the GLFW pointer-grab state.
2. Signal Unity's `OpenFromGame(initialText)` with the field's content when focus begins and `CloseFromGame()` when it ends. This needs a Pojlib / Minecraft-Vivecraft integration point. No such signal has been wired yet.
3. Only then enable `enableNativeInput` and test the bridge using a debug development build on a headset. The bridge suppresses output when disabled or when running outside Android.
4. Verify that Minecraft is receiving callbacks, and that its own keyboard shortcuts / VR-controller controls are paused while typing.
5. Verify Cyrillic, ASCII punctuation, backspace, cursor movement, send and escape. Supplementary Unicode (e.g. some emoji) is currently **not supported** by Pojlib's Java `char` callback.
6. Confirm initial text and caret position correctly match the Minecraft GUI's editor. The current bridge emits discrete operations rather than replacement snapshots; text selection and multiline composition are not supported.

## Safety and compatibility notes

- Do **not** connect `OnGameTextChanged` (whole buffer snapshots) to the GLFW char callback. Doing so would duplicate the whole message on every edit.
- The GLFW bridge uses discrete `OnGameCharacterInserted`, `OnGameBackspacePressed`, `OnGameCursorLeft`, `OnGameCursorRight`, submit and cancel events.
- `Cancel` forwards Escape rather than attempting to revert a Java editor's partially injected text; cancellation semantics depend on the actual Minecraft screen.
- Errors calling JNI disable the bridge to avoid flooding logs.
- No changes were made to `Pojlib-release.aar`, the Minecraft runtime or signing credentials.
- This branch provides a **working API adapter in code**, not a verified in-game keyboard feature. The entry/focus handshake and on-device tests are blockers to claiming full integration.

## References

- https://github.com/QuestCraftPlusPlus/Pojlib/blob/QuestCraft-6.0.0/src/main/java/org/lwjgl/glfw/CallbackBridge.java
- https://github.com/QuestCraftPlusPlus/Pojlib/blob/QuestCraft-6.0.0/src/main/java/pojlib/input/LwjglGlfwKeycode.java
- https://github.com/QuestCraftPlusPlus/Pojlib/blob/QuestCraft-6.0.0/src/main/java/pojlib/UnityPlayerActivity.java

## Unity focus message contract (experimental)

Attach `FoxCraftGameFocusReceiver` to the same active Unity GameObject as `FoxCraftGameTextInputCoordinator` (or set the reference explicitly).

- `UnityPlayer.UnitySendMessage(gameObjectName, "OpenTextField", base64Utf8Text)`: opens an editing session. Encode the initial text as standard Base64 from UTF-8; send an empty string for empty text.
- `UnityPlayer.UnitySendMessage(gameObjectName, "CloseTextField", "")`: closes without submitting.
- Both `acceptGameFocusMessages` on the receiver and `enableNativeInput` on the GLFW bridge are **false by default**.
- The exact GameObject name must match the object in the running Unity scene.
- **No Minecraft-side call site is implemented yet**; these are receiver endpoints, not proof that in-game text focus is detectable.

Never enable the bridge based only on `CallbackBridge.isGrabbing()`: pointer-grab state is not a reliable indicator of text editing. Do not send text unless a known Minecraft GUI has focus.
