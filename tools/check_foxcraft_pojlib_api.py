#!/usr/bin/env python3
"""Check the bundled Pojlib AAR against Unity's JNI calls before APK builds.

Uses Java's javap only to inspect class signatures. No Java code is executed
and the AAR, user worlds and signing files are not changed.
"""
from __future__ import annotations

import argparse
from io import BytesIO
from pathlib import Path
import subprocess
import sys
import tempfile
from zipfile import BadZipFile, ZipFile

EXPECTED_CLASSES = (
    "pojlib/API.class",
    "pojlib/util/Constants.class",
    "pojlib/util/json/MinecraftInstances$Instance.class",
)


def verify_aar(path: Path) -> None:
    if not path.is_file() or path.stat().st_size < 1024:
        raise ValueError("Missing Pojlib-release.aar")

    try:
        with ZipFile(path) as archive:
            if "AndroidManifest.xml" not in archive.namelist():
                raise ValueError("Pojlib AAR has no AndroidManifest.xml")
            classes_bytes = archive.read("classes.jar")
        with ZipFile(BytesIO(classes_bytes)) as classes:
            missing = set(EXPECTED_CLASSES) - set(classes.namelist())
            if missing:
                raise ValueError("Required Pojlib Java classes missing: " + ", ".join(sorted(missing)))
    except (BadZipFile, KeyError) as exc:
        raise ValueError("Invalid Pojlib AAR or classes.jar ZIP") from exc

    with tempfile.TemporaryDirectory() as folder:
        classes = Path(folder) / "classes.jar"
        classes.write_bytes(classes_bytes)
        def describe(class_name: str) -> str:
            try:
                result = subprocess.run(
                    ["javap", "-public", "-classpath", str(classes), class_name],
                    check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                    text=True, timeout=30,
                )
            except (FileNotFoundError, subprocess.CalledProcessError,
                    subprocess.TimeoutExpired) as exc:
                raise ValueError("javap could not inspect " + class_name) from exc
            return result.stdout

        api = describe("pojlib.API")
        constants = describe("pojlib.util.Constants")
        instance = describe("pojlib.util.json.MinecraftInstances$Instance")

        # QuestCraft-6.0.0 API.prelaunch() is void. A boolean JNI call will
        # request a nonexistent JVM signature and crash the launch path.
        if "public static void prelaunch(" not in api:
            raise ValueError("Pojlib API.prelaunch must return void")
        if "public static void launchInstance(" not in api:
            raise ValueError("Pojlib API.launchInstance missing")
        if "public static java.lang.String USER_HOME;" not in constants:
            raise ValueError("Pojlib Constants.USER_HOME missing")
        if "public java.lang.String gameDir;" not in instance:
            raise ValueError("Pojlib Instance.gameDir missing")
        if "public java.lang.String versionName;" not in instance:
            raise ValueError("Pojlib Instance.versionName missing")

    print("Pojlib API preflight OK: void prelaunch, launchInstance, USER_HOME, gameDir, versionName")
    print("Device compatibility and Unity Android compilation are not verified.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aar", type=Path,
                        default=Path("Assets/Plugins/Android/Pojlib-release.aar"))
    args = parser.parse_args()
    try:
        verify_aar(args.aar)
    except (ValueError, OSError) as exc:
        print("FoxCraft Pojlib preflight failed: " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
