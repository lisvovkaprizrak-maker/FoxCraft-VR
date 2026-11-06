#!/usr/bin/env python3
"""Safe, opt-in Meta Quest smoke-test helper for FoxCraft experimental APK.

Read-only checks run by default. Installing, launching or inspecting logs
require explicit flags. Never uninstalls applications or clears user data.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

PACKAGE = "com.foxcraftvr.quest"
ACTIVITY = "pojlib.UnityPlayerActivity"


class DeviceError(RuntimeError):
    pass


def command(args: list[str], *, timeout: int = 45) -> str:
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        raise DeviceError(f"Could not execute {args[0]}: {exc}") from exc
    if result.returncode:
        raise DeviceError(
            f"{args[0]} returned code {result.returncode}: " +
            (result.stderr or result.stdout).strip()[:800]
        )
    return result.stdout


def resolve_aapt() -> str:
    direct = shutil.which("aapt")
    if direct:
        return direct
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        root = os.environ.get(key)
        if not root:
            continue
        tools = sorted((Path(root) / "build-tools").glob("*/aapt"), reverse=True)
        if tools:
            return str(tools[0])
    raise DeviceError(
        "Android 'aapt' tool is required to verify the APK package before installation."
    )


def verify_apk(apk: Path) -> None:
    if not apk.is_file() or apk.stat().st_size < 100_000:
        raise DeviceError(f"APK is missing or too small: {apk}")
    result = command([resolve_aapt(), "dump", "badging", str(apk)])
    match = re.search(r"^package: name='([^']+)'", result, flags=re.MULTILINE)
    if not match or match.group(1) != PACKAGE:
        raise DeviceError(
            f"APK package must be {PACKAGE}; detected {match.group(1) if match else 'unknown'}"
        )


def choose_device(adb: str, requested: str | None) -> str:
    output = command([adb, "devices"], timeout=20)
    devices = [
        fields[0] for row in output.splitlines()[1:]
        if len(fields := row.split()) >= 2 and fields[1] == "device"
    ]
    if requested:
        if requested not in devices:
            raise DeviceError(f"Selected Quest {requested!r} is not online/authorized. Online: {devices}")
        return requested
    if len(devices) != 1:
        raise DeviceError("Connect exactly one authorized Quest, or pass --device SERIAL. "
                          f"Currently online: {devices}")
    return devices[0]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path,
                        help="APK path to validate with aapt (required for --install)")
    parser.add_argument("--device", help="ADB serial when multiple devices are connected")
    parser.add_argument("--install", action="store_true",
                        help="Explicitly sideload/reinstall FoxCraft's test package, without clearing app data")
    parser.add_argument("--launch", action="store_true",
                        help="Explicitly start the FoxCraft test application")
    parser.add_argument("--logs", action="store_true",
                        help="Print privacy-filtered FoxCraft Unity and Minecraft Java startup markers")
    args = parser.parse_args()

    try:
        if args.install and args.apk is None:
            raise DeviceError("--install requires --apk")
        if args.apk is not None:
            verify_apk(args.apk)

        adb = shutil.which("adb")
        if adb is None:
            raise DeviceError("Android adb is not in PATH. Install Android platform-tools.")
        device = choose_device(adb, args.device)
        prefix = [adb, "-s", device]
        print("Connected device:", device)

        if args.install:
            print("Installing verified FoxCraft test APK; QuestCraft itself is untouched.")
            print(command(prefix + ["install", "-r", str(args.apk)], timeout=180).strip())

        existing = command(prefix + ["shell", "pm", "path", PACKAGE])
        if not existing.strip().startswith("package:"):
            print("FoxCraft test package is not installed. "
                  "Pass --apk FILE --install after building a valid APK.")
            return 0

        print(PACKAGE + " is installed.")
        if args.launch:
            print(command(prefix + ["shell", "am", "start", "-n",
                                    PACKAGE + "/" + ACTIVITY]).strip())

        if args.logs:
            # Minecraft Java startup messages may use a different logcat tag
            # from Unity. Filter in memory to FoxCraft markers; don't print
            # unfiltered system logs containing other applications' data.
            output = command(prefix + ["logcat", "-d", "-t", "1200"], timeout=30)
            shown = [line for line in output.splitlines()
                     if "[FoxCraft]" in line or "FoxCraftKeyboard" in line]
            print("\n".join(shown[-60:]) if shown else
                  "No FoxCraft launcher/mod markers in the last 1200 log lines.")

        print("\nOn the headset, manually check: Minecraft 1.21.5 Fabric launch; "
              "chat and commands; RU/EN letters; number row 1–0; 450 ms symbols; "
              "ё/Ё; controller pointer hold/release; server address field; "
              "relaunch without duplicate mods.")
        print("The smoke helper does not assert game/VR correctness.")
        return 0
    except DeviceError as exc:
        print("FoxCraft Quest smoke check: " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
