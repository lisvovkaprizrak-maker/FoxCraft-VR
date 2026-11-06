# FoxCraft — in-game VR keyboard (Fabric prototype)

This folder contains the **Minecraft-side** keyboard, separate from the Unity launcher keyboard in PR #3. Target: Minecraft 1.21.5, Java 21, Fabric and QuestCraft/Vivecraft.

## Implemented in source

- RU / EN letter layouts, Shift, symbols, space, cursor arrows, backspace and Enter.
- A dedicated top number row **1 2 3 4 5 6 7 8 9 0** on every layout. The `123` symbols layer offers punctuation such as `@ # / ? { }` instead of duplicating the number row.
- Small alternate characters on letter keys; tap for the letter, hold for **450 ms** to type a punctuation symbol into the focused Minecraft text field. Long-press `Е` types `ё` (or `Ё` with Shift). The upper number row is separate from these alternates. Dragging away cancels the pending character.
- Dark background, orange shimmering border and hover/long-press feedback.
- Minecraft-screen GUI rendering rather than Unity's launcher canvas.

## Run logic smoke tests without Minecraft

Install **JDK 21**. From this directory:

```bash
mkdir -p build/smoke
javac -encoding UTF-8 -d build/smoke \
  src/main/java/dev/foxcraft/keyboard/KeyboardLayout.java \
  src/main/java/dev/foxcraft/keyboard/KeyHoldController.java \
  src/main/java/dev/foxcraft/keyboard/KeyboardPointerInput.java \
  src/test/java/dev/foxcraft/keyboard/KeyboardLogicSmokeTest.java
java -cp build/smoke dev.foxcraft.keyboard.KeyboardLogicSmokeTest
```

These tests verify the number row, Russian/English punctuation alternates, uppercase `Ё`, panel geometry, the 450 ms hold threshold, and VR pointer capture including key gaps, drag-away and focus-loss cancellation.

## Build the Minecraft mod

Install **JDK 21** and **Gradle 8.12+**; network access to Fabric Maven repositories is needed. Run `gradle build` in this directory. The expected mod JAR is under `build/libs`.

This directory does not contain a Gradle wrapper. Do not confuse the resulting mod JAR with a FoxCraft Android APK. The mod needs integration with the matching Minecraft 1.21.5 / Fabric / Vivecraft environment and actual testing on a Quest headset.

## Packaging into a FoxCraft Unity APK (experimental)

A Fabric `.jar` is **not** an Android APK. To bundle this mod inside a future FoxCraft Android build:

1. Build the mod with `gradle build` or download its verified GitHub Actions artifact.
2. From the repository root run `python3 tools/stage_foxcraft_ingame_keyboard.py`. The script verifies the JAR ZIP, `fabric.mod.json`, mod ID and pinned Minecraft **1.21.5** dependency.
3. The script writes `Assets/StreamingAssets/foxcraft-keyboard-0.1.0-alpha.jar`. It refuses to overwrite a different existing JAR without explicit `--replace`.
4. Build the Unity project. After Pojlib `prelaunch`, the launcher verifies both Minecraft `versionName` **1.21.5** and the instance Java `mainClass` belonging to Fabric's `KnotClient`, then calls `FoxCraftInGameKeyboardInstaller.EnsureInstalled` **before** starting Minecraft. The installer checks the Java profile's `gameDir`, uses Pojlib's `USER_HOME/instances` boundary, copies the JAR into that profile's `mods` folder and never overwrites a keyboard mod already present.
5. A missing staged JAR or unsupported Pojlib path produces a warning and the game launches **without** the keyboard, avoiding accidental damage to existing profiles.

**Untested on-device:** This does not prove Pojlib's `USER_HOME` path, the Android StreamingAssets URL, controller events or Unity compilation. The existing separate FoxCraft APK pipeline in PR #1 targets Minecraft **1.21.1** and does not automatically become compatible with this **1.21.5** mod. Never install the 1.21.5 JAR into a 1.21.1 profile.

## Dedicated Android APK test pipeline

A **separate** Unity Android test route for **Minecraft 1.21.5** is now on this branch:

- `.github/workflows/foxcraft-keyboard-apk.yml` runs lightweight Python checks on the PR; it **does not** build Unity or an APK on pull requests.
- A manual `workflow_dispatch` (available once that workflow exists on the default branch) compiles the Fabric mod, stages its verified JAR into Unity, checks the existing Pojlib AAR, builds the ARM64 Android APK with `FoxCraftKeyboardAndroidBuild.BuildTestApk`, and validates the resulting package ID `com.foxcraftvr.quest` before uploading the **UNTESTED** APK artifact.
- Unity requires version `2022.3.62f3`, Android Build Support and a valid Unity licence configured privately in GitHub Actions; do not commit credentials.
- Unity tests and actual Meta Quest interaction **have not run**. A green preflight status is not an APK build.

Русскоязычная инструкция: `docs/FOXCRAFT_KEYBOARD_APK_RU.md`.

## Remaining validation

- Confirm Fabric and Yarn compile against pinned dependencies.
- Confirm Vivecraft VR-controller pointer clicks trigger Minecraft screen mouse press/release. The pointer state machine has automated Java tests, but actual Quest controller event wiring still requires hardware validation.
- Confirm focused fields in chat, slash commands and the multiplayer-server UI.
- Test Cyrillic font support, Enter and Escape behavior, long-press duration and responsive size on the headset.
- Package and activate the compiled mod as a core mod in the correct QuestCraft instance.
- The previously shared artwork is a **concept image**, not a screenshot of the running mod.

## Related

- Unity launcher keyboard: PR #3 in this repository.
- QuestCraft / Pojlib mod compatibility baseline: Minecraft 1.21.5, Vivecraft 1.2.5 and Fabric API 0.128.2.
