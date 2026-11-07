# FoxCraft launcher — QuestCraft menu with VR and phone modes

Status: **preview implementation; not an Android phone game release**.

## Scope

- Keep QuestCraft's launcher scene and functional menus: Microsoft login, saved
  accounts, Minecraft instances, Modrinth mods/modpacks, settings, RAM, logs and
  VR play. FoxCraft adds a fox-branded mode selector rather than replacing these
  screens or modifying the Minecraft gameplay mod.
- VR/Quest/Pico uses the original Pojlib + Vivecraft launch flow.
- Phone uses the original QuestCraft desktop/flat launcher camera, Android touch
  and the native software keyboard. The Minecraft **version selection** is
  independent of VR and populated from Mojang's official release manifest.
- The selected mode and phone version are stored in the existing
  `launcher.conf`. Older configs are migrated without resetting accounts.
- The test Android package is `com.foxcraftvr.quest`; it must not overwrite
  an existing QuestCraft installation.

## Phone Minecraft runtime integration — mandatory before claiming phone play

The QuestCraft bundled Pojlib and Vivecraft modules do **not** constitute a
standalone, touch-capable 2D Minecraft renderer. Do not call
`pojlib.API.launchInstance` in phone mode. Currently the launch guard displays
an explicit error until a compatible game renderer is integrated.

A future **in-APK** Android module must expose:

```java
package dev.foxcraft.mobile;
public final class MobileMinecraftBridge {
    public static boolean isAvailable(android.app.Activity activity);
    public static boolean launch(
        android.app.Activity activity,
        pojlib.account.MinecraftAccount account,
        String minecraftVersion);
}
```

The implementation must:
1. Bundle compatible Java runtime, LWJGL/GL and Android surface integration
   *for 2D Minecraft* (review licenses and attribution of any Pojav/Amethyst
   sources); do not assume QuestCraft's OpenXR renderer works on a phone.
2. Download and validate exactly the selected Minecraft version, dependencies,
   assets and compatible mod loader. Keep phone game profiles separate from VR.
3. Provide touch movement, camera, buttons, inventory, chat, software keyboard
   and pause/back navigation. Respect Android lifecycle and device RAM.
4. Reuse a securely authenticated account without logging/exporting Microsoft
   tokens. No third-party intent handoff of credentials.
5. Have instrumentation/device tests on a Quest 3 and ordinary Android ARM64
   phone, including offline/error paths, first login and actual Minecraft
   world creation/entry.

Until then, the selector and version list can be tested, but phone **Играть**
must not silently launch the VR runtime.

## Building

Open the Unity project in **Unity 2022.3.62f3** with Android Build Support.
The manual GitHub Actions build path is
`.github/workflows/foxcraft-keyboard-apk.yml`, which requires Unity license
credentials in repository secrets. It builds a separate untested FoxCraft APK
only after verifying the bundled `Assets/Plugins/Android/Pojlib-release.aar`
and the staged Fabric 1.21.5 keyboard JAR. A successful C# preflight check is
**not** an APK build or proof of working VR/phone play.

The original QuestCraft menus continue to use QuestCraft APIs/links until
dedicated FoxCraft replacements exist; preserve upstream GPL and attribution.


## Automated launcher verification

The pull-request preflight builds the real FoxCraft launcher C# components
against lightweight Unity API test doubles and executes startup smoke checks:

- First-run launcher config creation and default phone mode on non-headsets.
- Preserving Microsoft accounts, RAM, game profile selection and cached phone
  versions when changing VR/phone options.
- Migrating QuestCraft-era launcher configs without deleting existing accounts.
- Backing up malformed settings before starting from defaults.
- Waiting for JNIStorage and a Pojlib activity before attempting Microsoft login.
- Exactly one initial sign-in attempt; Play remains locked until Pojlib confirms
  an authenticated account.
- Checking the APK package, manifest, required native libraries, signing and
  bundled Fabric keyboard via `tools/verify_foxcraft_test_apk.py`.

The smoke runner lives in `tests/unity-login-compile/SmokeProgram.cs` and is
part of `.github/workflows/foxcraft-keyboard-apk.yml`. These checks use fakes
for Android/Unity APIs: they do **not** prove a Unity APK runs on real phones,
and the phone's 2D renderer remains unimplemented.

## Manual acceptance criteria (when a testable APK exists)

1. A Quest headset installs FoxCraft alongside QuestCraft, signs in and
   launches a supported VR Minecraft instance without the old game being
   overwritten.
2. All pre-existing QuestCraft launcher menus still work: profiles,
   Microsoft login, mods/modpacks, developer settings and logs.
3. A normal Android ARM64 phone installs FoxCraft and can touch/tap the entire
   flat launcher menu, enter account text, select and persist a separate Java
   Edition release.
4. Once the in-APK 2D backend is implemented, **Играть** starts the selected
   version with on-screen controls, does not start OpenXR, and returns safely to
   the launcher. Until then it must show an explicit unavailable-runtime
   message rather than silently launch VR.
5. Restarting either device retains the selected mode, correct Minecraft
   version, account identity and installed game profiles.

Do not distribute a phone-playable build until criterion 4 actually passes.


## FoxCraft selector placement inside QuestCraft's actual Unity scene

The source scene `Assets/Scenes/Main.unity` has a 1200×600 world-space
`MainCanvas`. Its bottom bar already contains **Играть**, the instance
selection, mod/instance management and settings buttons.

The FoxCraft mode selector is a small orange **VR / ТЕЛ.** button docked in
the unused gap between the mod and settings buttons. The larger mode/version
chooser opens only on demand, above the bottom bar, and can be dismissed
without changing an existing QuestCraft profile. This avoids covering
**Играть**, as the original always-open panel did.

The source geometry is enforced by
`tests/test_foxcraft_unity_scene.py`, alongside checks that the
original UIHandler, LoginHandler, JNIStorage, world-space canvases and
independent desktop phone camera remain wired. These are source-level checks;
actual tap targets and legibility still need testing on a physical phone.
