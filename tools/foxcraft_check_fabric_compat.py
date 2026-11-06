#!/usr/bin/env python3
"""Inspect Fabric JAR metadata before placing a mod into a Minecraft instance.

Checks declared Minecraft compatibility ONLY. A matching declaration does not
establish that the mod runs on QuestCraft/Meta Quest hardware.
"""
import argparse
import json
from pathlib import Path
import sys
import zipfile


def check_compatibility(jar_path: Path, minecraft_version: str) -> tuple[bool, str]:
    if not jar_path.is_file():
        return False, f"JAR not found: {jar_path}"
    try:
        with zipfile.ZipFile(jar_path) as archive:
            metadata = json.loads(archive.read("fabric.mod.json").decode("utf-8"))
    except (OSError, zipfile.BadZipFile, KeyError, ValueError, UnicodeDecodeError) as error:
        return False, f"Cannot read Fabric metadata from {jar_path}: {error}"

    mod_name = metadata.get("name", metadata.get("id", "unknown mod"))
    requirements = metadata.get("depends", {})
    required = requirements.get("minecraft") if isinstance(requirements, dict) else None
    # Keep this check conservative: only a literal version or literal choices
    # are accepted. Do not assume complex Fabric version ranges are compatible.
    declared = [required] if isinstance(required, str) else required
    if not isinstance(declared, list) or not declared or not all(isinstance(v, str) for v in declared):
        return False, f"{mod_name}: missing or unsupported Minecraft version requirement: {required!r}"
    if minecraft_version not in declared:
        return False, (
            f"{mod_name}: requires Minecraft {', '.join(declared)}; "
            f"your instance uses {minecraft_version}. DO NOT install this JAR. "
            "Remove it from mods/ to recover from a startup failure."
        )
    return True, (
        f"{mod_name}: metadata declares Minecraft {minecraft_version}. "
        "This is NOT a runtime compatibility test. Verify Fabric API, other mods and QuestCraft separately."
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path, help="Path to the Fabric JAR")
    parser.add_argument("--minecraft", required=True, help="Minecraft instance version, e.g. 1.21.5")
    args = parser.parse_args()
    ok, message = check_compatibility(args.jar, args.minecraft)
    print(("METADATA MATCH: " if ok else "STOP: ") + message)
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
