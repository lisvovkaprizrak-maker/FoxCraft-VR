# FoxCraft-VR development and build guide

FoxCraft-VR is a Unity-based Android XR wrapper/launcher built with Pojlib for QCXR projects.

## Requirements

- **Unity Editor 2022.3.62f3**. This version comes from `ProjectSettings/ProjectVersion.txt`; it supersedes the older 2022.3.62f2 reference in the original README.
- Unity **Android Build Support**, including the SDK, NDK and OpenJDK modules installed through Unity Hub.
- Git and Git LFS if your checkout uses LFS-managed assets.
- An Android XR headset and USB debugging enabled if testing on a device.

## Open the project

1. Clone the repository with Git, including submodules if applicable.
2. In Unity Hub, choose **Add project from disk** and select the repository root.
3. Open it with Unity **2022.3.62f3**.
4. Allow Unity to resolve packages and import assets before attempting a build.
5. If a package fails to resolve, verify the local directories referenced by `Packages/manifest.json`:
   - `Packages/LocalPackages/unity.webp`
   - `Packages/LocalPackages/PICO Unity OpenXR Integration SDK-1.3.0`

The manifest paths above are relative to the `Packages` directory. Check their presence in your local checkout; do not replace them with unrelated package versions without verifying compatibility.

## Android XR build checklist

1. Select **Android** as the build target in Unity's Build Settings and switch platform if needed.
2. Open **Project Settings → XR Plug-in Management** and review the OpenXR configuration for the target headset.
3. Review **OpenXR Features** and enabled input profiles. The current project contains Meta Quest and PICO-related settings; do not assume all headsets have been tested.
4. Check **Player Settings → Other Settings** for the application identifier, API levels, scripting backend and architectures before building.
5. Configure your **own Android signing keystore** if producing a signed release. Do not commit keystores or credentials.
6. Create a development build and install it on a test device. Verify launch, controllers, permissions and the wrapper's entry flow.

### Current configuration notes

At the time this guide was added, `ProjectSettings/ProjectSettings.asset` specifies **minimum Android SDK 29** and **target SDK 34**. These are project settings, not a guarantee of compatibility or store acceptance. Review them for your release target.

## Troubleshooting

| Symptom | First checks |
| --- | --- |
| Unity asks to upgrade project | Confirm Unity 2022.3.62f3 is installed and selected. |
| Package Manager cannot resolve a file dependency | Check the two local package paths above and that the checkout is complete. |
| OpenXR starts but controllers do not respond | Inspect enabled interaction profiles and input mappings for the specific headset. |
| Android signing fails | Set a valid local keystore and alias in Player Settings; avoid machine-specific paths in commits. |
| Android build fails after importing | Check the Unity Console for the first error, then review Android SDK/NDK/OpenJDK modules. |

## Before opening a pull request

- Keep Unity `.meta` files alongside newly added/renamed assets.
- Avoid committing generated `Library`, `Temp`, `Build`, `Logs` and device-specific secrets.
- State which headset and Android version were tested.
- Include a short reproduction procedure for fixes.
- Do not claim a build is verified unless it has run on the target hardware.
