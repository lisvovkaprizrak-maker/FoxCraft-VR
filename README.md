# FoxCraft — Minecraft Java launcher for Quest and Android

FoxCraft is an **in-development** Android launcher derived from the open-source
[QCXR XR Wrapper / QuestCraft](https://github.com/QuestCraftPlusPlus/QuestCraft).
It retains QuestCraft's launcher functionality while adding FoxCraft visual
identity and a planned two-mode launcher.

**Current state:** a source-code preview, not yet a tested dual-platform APK.
The in-launcher Android phone **version picker** is implemented, but playable
phone mode still requires an integrated touch-capable **2D Minecraft engine**.
The QuestCraft VR runtime cannot be reused as a 2D phone engine.

## Modes

| Mode | Launcher | Minecraft game |
| --- | --- | --- |
| VR (Meta Quest / Pico) | Existing QuestCraft menus, FoxCraft mode selector | QuestCraft / Pojlib game launch path; physical headset testing required |
| Телефон (Android) | Flat menu, touch UI, official Minecraft release list, cached versions | 2D launch bridge prepared; phone rendering/control engine not yet bundled |

Both modes share the original QuestCraft menu sections for Microsoft accounts,
Minecraft instances, mod management, changelog, and settings. The selected
phone version and VR/phone mode are saved separately in `launcher.conf`.
A phone does not invoke the QuestCraft OpenXR renderer.

## Source and development

- **Unity:** 2022.3.62f3 with Android Build Support.
- **Minecraft runtime:** the project's bundled Pojlib AAR and QuestCraft stack.
- **Android package:** `com.foxcraftvr.quest`, separate from QuestCraft.
- **FoxCraft application icon:** `Assets/Resources/FoxCraftIcon.png`.
- **APK build entry:** `FoxCraftKeyboardAndroidBuild.BuildTestApk`, from
  `Assets/Editor/FoxCraftKeyboardAndroidBuild.cs`.
- **Automated checks:** `.github/workflows/foxcraft-keyboard-apk.yml`
  (source preflight on pull requests; **actual Unity APK build is manual**).
- **Detailed integration guide:** [FoxCraft Android modes](docs/FOXCRAFT_LAUNCHER_ANDROID_MODES.md).

The APK build verifies its Pojlib AAR and staged Minecraft 1.21.5 Fabric JAR.
It requires a valid Unity license/credentials in GitHub Actions or a licensed
local Unity editor. A passing CI preflight is **not** a signed installable APK.

## Licensing and upstream credits

FoxCraft continues the original project's license terms: see [LICENSE](LICENSE)
(GPLv3). QuestCraft/QCXR launcher functionality and contributions remain
credited to their upstream authors, including @TheJudge156, @CADIndie, and
@computer-catt. Minecraft, Meta Quest, Unity, and other names belong to their
respective owners. FoxCraft is an independent derivative and is not an official
Mojang/Microsoft product.

This project does **not** include Mojang's proprietary Minecraft game files:
users must authenticate or otherwise have a valid right to play.
