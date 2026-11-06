#!/usr/bin/env python3
"""Validate a locally compiled QCKB1 Fabric JAR and stage it for Unity APK packaging.

Does not install anything onto a Quest. Refuses to overwrite different bytes
unless explicitly asked to do so.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile
import zipfile

DEFAULT_SOURCE = Path("Fabric/foxchatkeyboard/FoxChatKeyboard-bridge-0.6.1-fabric-1.21.5.jar")
DEFAULT_DEST = Path("Assets/StreamingAssets/FoxChatKeyboard-bridge-0.6.1.jar")


def validate_jar(path: Path) -> str:
    if not path.is_file() or path.stat().st_size < 1024:
        raise ValueError("Missing or incomplete Fabric JAR: " + str(path))
    try:
        with zipfile.ZipFile(path) as jar:
            files = jar.namelist()
            metadata = json.loads(jar.read("fabric.mod.json"))
            if metadata.get("id") != "questchatkeyboardbridge":
                raise ValueError("Unexpected Fabric mod id")
            if metadata.get("version") != "0.6.1":
                raise ValueError("Unexpected Fabric mod version")
            if metadata.get("depends", {}).get("minecraft") != "1.21.5":
                raise ValueError("Wrong Minecraft version")
            if "com/questchatkeyboard/bridge/QuestChatKeyboardBridge.class" not in files:
                raise ValueError("Fabric entrypoint class missing")
            if any(p.startswith("net/fabricmc/") for p in files):
                raise ValueError("Fabric API stubs accidentally packaged")
    except (zipfile.BadZipFile, KeyError, json.JSONDecodeError) as exc:
        raise ValueError("Invalid Fabric JAR") from exc
    return hashlib.sha256(path.read_bytes()).hexdigest()


def stage_jar(source: Path, destination: Path, replace=False) -> str:
    src_hash = validate_jar(source)
    if destination.exists():
        if destination.is_dir():
            raise ValueError("Destination is a directory")
        old_hash = hashlib.sha256(destination.read_bytes()).hexdigest()
        if old_hash == src_hash:
            return "UNCHANGED"
        if not replace:
            raise ValueError("Different JAR already staged; explicit --replace required")
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix=".foxcraft-jar-", dir=destination.parent)
    os.close(fd)
    try:
        shutil.copyfile(source, temp)
        os.replace(temp, destination)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)
    assert hashlib.sha256(destination.read_bytes()).hexdigest() == src_hash
    return "STAGED"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--destination", type=Path, default=DEFAULT_DEST)
    parser.add_argument("--replace", action="store_true")
    args = parser.parse_args()
    try:
        print(stage_jar(args.source, args.destination, args.replace))
    except (ValueError, OSError) as ex:
        parser.exit(2, "ERROR: " + str(ex) + "\n")
    print("The JAR is staged for Unity; no APK or Quest keyboard test was performed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
