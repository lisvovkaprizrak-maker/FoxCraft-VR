# FoxCraft product target: QuestCraft + Meta Quest keyboard

## Product outcome

FoxCraft should be a standalone Android VR application based on the QuestCraft stack (Minecraft: Java Edition + Vivecraft + Pojlib), with native-feeling text entry through the **Meta Quest system keyboard overlay**. The goal is a playable Minecraft experience, not only a launcher.

This document defines target behavior. **It does not claim that the keyboard integration or a playable FoxCraft APK is implemented.**

## End-user acceptance criteria

1. The user can install FoxCraft on a supported Meta Quest headset and enter a Minecraft VR world.
2. The launcher can sign in through supported Minecraft authentication and start the installed game version.
3. When focusing a supported text field (chat, slash command, server address, world name or settings search), the system keyboard opens.
4. Text, editing operations, confirmation and cancellation reach the intended Minecraft or launcher input field.
5. Keyboard dismissal restores controller focus without accidentally moving the player or submitting text.
6. Both controller pointer interaction and, where supported, hand input work with the keyboard.
7. Multiplayer and local-world flows continue to function after repeated keyboard open/close cycles.

## Technical approach

### 1. Launcher layer (Unity)

- Preserve the existing Unity / Pojlib launcher architecture while identifying which text boxes are native Unity UI and which are inside the Minecraft/Vivecraft runtime.
- Add a central `TextInputCoordinator` abstraction for: request focus, show keyboard, insert/edit text, submit, cancel and restore focus.
- Keep keyboard support optional on non-Meta devices; fall back to the existing input method.
- Do not modify Android XR input profiles until device-specific testing confirms they are needed.

### 2. Meta system keyboard overlay

Prefer the **system keyboard overlay**, per Meta's current Unity guidance, rather than the deprecated `OVRVirtualKeyboard` prefab.

- Confirm the Meta XR SDK and OpenXR package combination that supports the overlay.
- Check required Android manifest declarations and overlay/focus lifecycle behavior.
- Do not assume that merely calling Unity's `TouchScreenKeyboard.Open()` will produce a working VR keyboard in the Minecraft runtime.
- Note: the current project manifest has `com.unity.xr.openxr` **1.14.3**. Newer OpenXR releases include changes to Meta system keyboard support; treat package upgrades as a separate compatibility-tested task, not an automatic change.

### 3. Minecraft / Vivecraft / Pojlib layer

A Unity keyboard overlay is **not by itself** an input bridge for Minecraft's Java GUI.

- Identify the existing Java ↔ Android / Unity text-input pathway and decide on one authoritative text editor while the overlay is open.
- Support Unicode text and composition (IME), backspace/delete, cursor selection, enter/submit and escape/cancel.
- Avoid duplicating keystrokes or sending player movement actions when the text field owns focus.
- Propagate keyboard visibility and app focus changes safely to the game loop.
- Keep platform-specific bridge code isolated, so the QuestCraft runtime can be updated independently.

## Implementation milestones

### M1 — Baseline
- Identify the launch scene, Unity input handlers, Java/Minecraft input path and Android activity.
- Confirm a clean Android development build and boot to the game on a test headset.

### M2 — Launcher keyboard
- Integrate the Meta system overlay with a launcher text field.
- Validate typing, Unicode, deletion, confirmation and cancellation.

### M3 — In-game keyboard bridge
- Connect overlay input to Minecraft chat and commands.
- Extend to multiplayer server address, world naming and other Minecraft text fields.
- Handle XR focus transitions and controller behavior.

### M4 — Playable release
- Verify Minecraft startup, local worlds, multiplayer, controller interactions, performance and repeated text entry.
- Produce signed APK via project-local secret configuration, without committing signing credentials.

## Device test matrix

| Area | Minimum test |
| --- | --- |
| Launcher | Focus, type, edit, submit, cancel |
| In-game chat | Open, type Cyrillic/Latin, send, reopen |
| Commands | Prefix `/`, mixed symbols, execute |
| Multiplayer | Enter server hostname/IP and connect |
| Worlds | Create and rename using keyboard |
| Focus | Open/dismiss repeatedly without movement |
| Connectivity | Work after launcher switches to game |
| Unsupported hardware | Existing input remains usable |

## Known constraints

- `ProjectSettings/ProjectVersion.txt` pins Unity 2022.3.62f3.
- `Packages/manifest.json` pins `com.unity.xr.openxr` to 1.14.3.
- Package/SDK compatibility and actual on-device behavior need verification.
- Minecraft ownership/account requirements and upstream license obligations remain applicable.
- Upstream QuestCraft, Vivecraft and Pojlib sources should be credited and their respective licenses respected.

## References

- https://github.com/QuestCraftPlusPlus/QuestCraft
- https://developers.meta.com/vr/design/virtual-keyboard_implementation/
- https://developers.meta.com/vr/documentation/unity/VK-unity-overview/ (deprecated implementation; use current system overlay)
