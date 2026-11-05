#!/usr/bin/env python3
"""Validate and stage an externally built Pojlib AAR for a local Unity build.

Never overwrites the source project's AAR unless --replace is explicitly passed.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import tempfile
import zipfile

DEFAULT_SOURCE = Path("pojlib/build/outputs/aar/Pojlib-release.aar")
DEFAULT_DEST = Path("Assets/Plugins/Android/Pojlib-release.aar")
REQUIRED = {"AndroidManifest.xml", "classes.jar"}


def digest(path: Path) -> str:
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def validate_aar(path: Path) -> str:
    if not path.is_file() or path.stat().st_size < 200:
        raise ValueError("Missing or incomplete Pojlib AAR: " + str(path))
    try:
        with zipfile.ZipFile(path) as archive:
            missing = REQUIRED - set(archive.namelist())
            if missing:
                raise ValueError("Incomplete Pojlib AAR; missing: " + ", ".join(sorted(missing)))
            for entry in REQUIRED:
                if archive.getinfo(entry).file_size == 0:
                    raise ValueError("Empty Pojlib AAR member: " + entry)
            bad = archive.testzip()
            if bad is not None:
                raise ValueError("Corrupt Pojlib AAR member: " + bad)
            with zipfile.ZipFile(__import__("io").BytesIO(archive.read("classes.jar"))) as classes:
                if not any(name.endswith(".class") for name in classes.namelist()):
                    raise ValueError("Pojlib classes.jar contains no Java classes")
    except (zipfile.BadZipFile, OSError) as exc:
        raise ValueError("Invalid Pojlib AAR or classes.jar") from exc
    return digest(path)


def stage_aar(source: Path, destination: Path, replace: bool = False) -> str:
    source_hash = validate_aar(source)
    if destination.exists():
        if not destination.is_file():
            raise ValueError("Pojlib destination must be a file")
        if digest(destination) == source_hash:
            return "UNCHANGED"
        if not replace:
            raise ValueError("Different AAR already staged; explicit --replace required")
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix=".foxcraft-pojlib-", dir=destination.parent)
    os.close(fd)
    try:
        shutil.copyfile(source, temp)
        if digest(Path(temp)) != source_hash:
            raise ValueError("Pojlib AAR checksum mismatch before replacement")
        os.replace(temp, destination)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)
    return "STAGED"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--destination", type=Path, default=DEFAULT_DEST)
    parser.add_argument("--replace", action="store_true",
                        help="Replace a different existing AAR in this checkout")
    args = parser.parse_args()
    try:
        print(stage_aar(args.source, args.destination, args.replace))
    except (ValueError, OSError) as exc:
        parser.exit(2, "ERROR: " + str(exc) + "\n")
    print("AAR staged locally; no Unity build, APK or Quest test was performed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
