# FoxCraft — in-game VR keyboard (Fabric prototype)

This folder contains the **Minecraft-side** keyboard, separate from the Unity launcher keyboard in PR #3. Target: Minecraft 1.21.5, Java 21, Fabric and QuestCraft/Vivecraft.

## Implemented in source

- RU / EN letter layouts, Shift, symbols, space, cursor arrows, backspace and Enter.
- A dedicated top number row **1 2 3 4 5 6 7 8 9 0** on every layout.
- Small alternate characters on letter keys; tap for the letter, hold for **450 ms** to type the alternate into the focused Minecraft text field. Dragging away cancels the pending character.
- Dark background, orange shimmering border and hover/long-press feedback.
- Minecraft-screen GUI rendering rather than Unity's launcher canvas.

## Run logic smoke tests without Minecraft

Install **JDK 21**. From this directory:

```bash
mkdir -p build/smoke
javac -encoding UTF-8 -d build/smoke \
  src/main/java/dev/foxcraft/keyboard/KeyboardLayout.java \
  src/main/java/dev/foxcraft/keyboard/KeyHoldController.java \
  src/test/java/dev/foxcraft/keyboard/KeyboardLogicSmokeTest.java
java -cp build/smoke dev.foxcraft.keyboard.KeyboardLogicSmokeTest
```

These tests verify the number row, Russian and English alternate symbols, panel geometry, ordinary press, 450 ms hold, pointer drag-away cancellation and focus-loss cancellation.

## Build the Minecraft mod

Install **JDK 21** and **Gradle 8.12+**; network access to Fabric Maven repositories is needed. Run `gradle build` in this directory. The expected mod JAR is under `build/libs`.

This directory does not contain a Gradle wrapper. Do not confuse the resulting mod JAR with a FoxCraft Android APK. The mod needs integration with the matching Minecraft 1.21.5 / Fabric / Vivecraft environment and actual testing on a Quest headset.

## Remaining validation

- Confirm Fabric and Yarn compile against pinned dependencies.
- Confirm Vivecraft VR-controller pointer clicks trigger Minecraft screen mouse press/release.
- Confirm focused fields in chat, slash commands and the multiplayer-server UI.
- Test Cyrillic font support, Enter and Escape behavior, long-press duration and responsive size on the headset.
- Package and activate the compiled mod as a core mod in the correct QuestCraft instance.
- The previously shared artwork is a **concept image**, not a screenshot of the running mod.

## Related

- Unity launcher keyboard: PR #3 in this repository.
- QuestCraft / Pojlib mod compatibility baseline: Minecraft 1.21.5, Vivecraft 1.2.5 and Fabric API 0.128.2.
