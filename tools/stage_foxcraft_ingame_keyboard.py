#!/usr/bin/env python3
"""Safely stage the built FoxCraft Fabric 1.21.5 JAR into Unity StreamingAssets.

Requires no network, Android device, or Unity license. The Unity launcher later
copies this staged JAR into the active compatible Minecraft instance at launch.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
from zipfile import BadZipFile, ZipFile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "MinecraftMod" / "FoxCraftKeyboard" / "build" / "libs" / "foxcraft-keyboard-0.1.0-alpha.jar"
DEFAULT_TARGET = ROOT / "Assets" / "StreamingAssets" / "foxcraft-keyboard-0.1.0-alpha.jar"
EXPECTED_MOD = "foxcraft_keyboard"
EXPECTED_MINECRAFT = "1.21.5"


def inspect_jar(path: Path) -> str:
    """Return SHA256 for a non-truncated JAR containing the expected Fabric metadata."""
    if not path.is_file() or path.stat().st_size < 1024:
        raise ValueError(f"Missing or unusually small JAR: {path}")
    try:
        with ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ValueError("Corrupted JAR ZIP entry")
            metadata = json.loads(archive.read("fabric.mod.json").decode("utf-8"))
    except (BadZipFile, KeyError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"Invalid Fabric JAR: {exc}") from exc
    if metadata.get("id") != EXPECTED_MOD:
        raise ValueError(f"Unexpected Fabric mod id: {metadata.get('id')!r}")
    if metadata.get("depends", {}).get("minecraft") != EXPECTED_MINECRAFT:
        raise ValueError("This package is not explicitly targeting Minecraft 1.21.5")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def stage(source: Path, destination: Path, replace: bool) -> str:
    digest = inspect_jar(source)
    if destination.exists():
        old_digest = inspect_jar(destination)
        if old_digest == digest:
            return f"Already staged: {destination} (SHA256 {digest})"
        if not replace:
            raise ValueError(
                f"Refusing to overwrite a different JAR at {destination}; pass --replace deliberately"
            )
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(".jar.pending")
    try:
        shutil.copyfile(source, temporary)
        if inspect_jar(temporary) != digest:
            raise ValueError("Staged copy hash mismatch")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return f"Staged: {destination} (SHA256 {digest})"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--dest", type=Path, default=DEFAULT_TARGET)
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args()
    try:
        print(stage(args.source, args.dest, args.replace))
    except (ValueError, OSError) as exc:
        print(f"FoxCraft staging failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
