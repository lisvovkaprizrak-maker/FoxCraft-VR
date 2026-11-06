#!/usr/bin/env python3
"""Check the bundled Pojlib AAR against Unity's JNI calls before APK builds.

Uses Java's javap only to inspect class signatures. No Java code is executed
and the AAR, user worlds and signing files are not changed.
"""
from __future__ import annotations

import argparse
from io import BytesIO
from pathlib import Path
import re
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

        # The AAR shipped with this Unity project is NOT binary-equivalent
        # to upstream QuestCraft-6.0.0 source. Its API.prelaunch is boolean.
        # Always validate this signature against the actual packaged AAR.
        prelaunch = [
            line.strip() for line in api.splitlines()
            if " prelaunch(" in line
        ]
        if not any(line.startswith("public static boolean prelaunch(")
                   for line in prelaunch):
            raise ValueError(
                "Bundled Pojlib must expose boolean API.prelaunch: " +
                ("; ".join(prelaunch) if prelaunch else "(missing)")
            )
        print("Bundled Pojlib prelaunch signature(s):", *prelaunch, sep="\\n  ")
        if "public static void launchInstance(" not in api:
            raise ValueError("Pojlib API.launchInstance missing")
        if "public static java.lang.String USER_HOME;" not in constants:
            raise ValueError("Pojlib Constants.USER_HOME missing")
        if "public java.lang.String gameDir;" not in instance:
            raise ValueError("Pojlib Instance.gameDir missing")
        if "public java.lang.String versionName;" not in instance:
            raise ValueError("Pojlib Instance.versionName missing")
        if "public java.lang.String mainClass;" not in instance:
            raise ValueError("Pojlib Instance.mainClass missing (cannot verify Fabric loader)")

        # These are called from JNIStorage, InstanceButton and launcher settings.
        # Validate the BUNDLED AAR, not only the upstream Java source tree.
        required_methods = (
            "loadAll", "getQCSupportedVersions", "hasConnection",
            "createNewInstance", "fixDataPermissions", "restartLauncher",
            "mirrorNativesInFolder", "unzipSavesFromFolder",
        )
        missing_methods = [
            name for name in required_methods
            if re.search(r"\\b" + re.escape(name) + r"\\(", api) is None
        ]
        if missing_methods:
            raise ValueError("Missing bundled Pojlib launcher API methods: " +
                             ", ".join(missing_methods))
        for field in ("gameReady", "model"):
            if re.search(r"\\b" + field + r";", api) is None:
                raise ValueError("Missing bundled Pojlib launcher API field: " + field)

    print("Pojlib API preflight OK: game lifecycle, instance management, loader metadata and required Unity JNI fields")
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
