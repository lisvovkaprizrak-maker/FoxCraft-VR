# FoxCraft launcher C# compile preflight

This folder is an **offline compile check**, NOT a replacement for the Unity Editor.

From repository root, with the .NET 8 SDK installed:

```sh
dotnet build tests/unity-compile/FoxCraftUnitySyntax.csproj --configuration Release
```

The project compiles the **actual** source files `Assets/Scripts/InstanceButton.cs`,
`Assets/Scripts/FoxCraftInGameKeyboardInstaller.cs` and
`Assets/Editor/FoxCraftKeyboardAndroidBuild.cs` against tiny **test doubles**
in `UnityApiStubs.cs`. No built library from this test should ever be put
into a Unity APK.

### What it catches

C# parse errors, incorrectly scoped variables, missing using directives,
invalid coroutine syntax, invalid lambda signatures, some local type and
method-call errors.

### What it does *not* catch

Differences between the stubs and real Unity 2022.3.62f3 packages, missing
Unity Android Build Support, Android manifest problems, Pojlib JNI failures,
streaming-assets file access, device-specific VR input or Vivecraft GUI focus.

Those must be checked by a **real Unity Android build**, then on a Meta Quest.
The PR CI deliberately does not claim an APK was built when this step is green.
