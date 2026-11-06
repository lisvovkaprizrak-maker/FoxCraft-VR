#!/usr/bin/env python3
"""Fail closed before publishing an UNTESTED FoxCraft Quest APK.

Checks the compiled Android package ID, launch activity, ARM64 Unity runtime,
the embedded Fabric keyboard JAR and its target version. The binary signature
is validated by Android SDK apksigner (never by just inspecting ZIP entries).
Does not install or launch anything.
"""
from __future__ import annotations

import argparse
from io import BytesIO
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from zipfile import BadZipFile, ZipFile

PACKAGE = "com.foxcraftvr.quest"
ACTIVITY = "pojlib.UnityPlayerActivity"
JAR_NAME = "foxcraft-keyboard-0.1.0-alpha.jar"
CLASSES = {
    "dev/foxcraft/keyboard/FoxCraftKeyboardClient.class",
    "dev/foxcraft/keyboard/VirtualKeyboardOverlay.class",
    "dev/foxcraft/keyboard/KeyboardPointerInput.class",
}


class ApkVerificationError(ValueError):
    pass


def sdk_tool(name: str) -> str:
    tool = shutil.which(name)
    if tool:
        return tool
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(env)
        if not root:
            continue
        entries = sorted((Path(root) / "build-tools").glob("*/" + name),
                         key=lambda p: [int(n) if n.isdecimal() else n
                                        for n in re.split(r"(\d+)", p.parent.name)])
        if entries:
            return str(entries[-1])
    raise ApkVerificationError(f"Android SDK build-tools executable missing: {name}")


def run_tool(arguments: list[str]) -> str:
    try:
        result = subprocess.run(arguments, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True,
                                timeout=45, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ApkVerificationError("Could not run Android SDK tool") from exc
    if result.returncode:
        # Don't leak local file paths/account info from untrusted build output.
        raise ApkVerificationError(
            f"{Path(arguments[0]).name} exited {result.returncode}: "
            + (result.stderr or result.stdout)[-450:])
    return result.stdout


def verify_apk(path: Path) -> str:
    if not path.is_file() or path.stat().st_size < 100_000:
        raise ApkVerificationError("Compiled Quest APK not found or suspiciously small")

    try:
        with ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ApkVerificationError("Corrupt APK ZIP entry")
            files = archive.namelist()
            if "AndroidManifest.xml" not in files:
                raise ApkVerificationError("Compiled AndroidManifest.xml is missing")
            if "lib/arm64-v8a/libunity.so" not in files:
                raise ApkVerificationError("Unity ARM64 native runtime is missing")
            if any(name.startswith("lib/armeabi-v7a/") for name in files):
                raise ApkVerificationError("Unexpected ARMv7 code in ARM64 Quest test APK")

            # Unity copies StreamingAssets directly to the APK assets directory.
            # Accept a nested assets/ prefix but never a JAR in an unrelated APK folder.
            matches = [
                name for name in files if name == "assets/" + JAR_NAME
                or (name.startswith("assets/") and name.endswith("/" + JAR_NAME))
            ]
            if len(matches) != 1:
                raise ApkVerificationError("Expected exactly one bundled FoxCraft keyboard JAR in APK assets")
            with ZipFile(BytesIO(archive.read(matches[0]))) as jar:
                if jar.testzip() is not None:
                    raise ApkVerificationError("Bundled keyboard JAR is corrupt")
                metadata = json.loads(jar.read("fabric.mod.json").decode("utf-8"))
                if metadata.get("id") != "foxcraft_keyboard":
                    raise ApkVerificationError("Bundled JAR has incorrect Fabric mod ID")
                if metadata.get("depends", {}).get("minecraft") != "1.21.5":
                    raise ApkVerificationError("Bundled JAR has incorrect Minecraft version")
                if not CLASSES.issubset(jar.namelist()):
                    raise ApkVerificationError("Bundled JAR is missing game keyboard classes")
    except (BadZipFile, KeyError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ApkVerificationError("Malformed APK or bundled keyboard mod") from exc

    badging = run_tool([sdk_tool("aapt"), "dump", "badging", str(path)])
    package = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    if package is None or package.group(1) != PACKAGE:
        raise ApkVerificationError("Unexpected Android package identifier")
    launchable = re.search(r"^launchable-activity: name='([^']+)'", badging,
                           re.MULTILINE)
    if launchable is None or launchable.group(1) != ACTIVITY:
        raise ApkVerificationError("Pojlib VR launcher activity not exported as launchable")
    if "arm64-v8a" not in badging:
        raise ApkVerificationError("Android package metadata does not advertise ARM64")

    run_tool([sdk_tool("apksigner"), "verify", "--verbose", str(path)])
    return (f"Verified UNTESTED FoxCraft Quest APK: package={PACKAGE}; "
            f"activity={ACTIVITY}; ARM64 Unity runtime; valid signature; "
            f"embedded Fabric Minecraft 1.21.5 keyboard. "
            f"VR headset operation is NOT validated.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    try:
        print(verify_apk(args.apk))
        return 0
    except (ApkVerificationError, OSError) as exc:
        print("FoxCraft APK verification failed: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
