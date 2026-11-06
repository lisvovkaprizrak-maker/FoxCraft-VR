#!/usr/bin/env python3
"""Fast, read-only FoxCraft checks. Does not run Unity or produce an APK."""
import json
import sys
from pathlib import Path

EXPECTED_UNITY = '2022.3.62f3'
EXPECTED_OPENXR = '1.14.3'


def check(root: Path) -> list[str]:
    problems = []
    for rel in ('Assets', 'Packages', 'ProjectSettings'):
        if not (root / rel).is_dir():
            problems.append(f'Missing Unity directory: {rel}')

    version_file = root / 'ProjectSettings/ProjectVersion.txt'
    if not version_file.is_file():
        problems.append('Missing ProjectSettings/ProjectVersion.txt')
    else:
        lines = version_file.read_text(encoding='utf-8').splitlines()
        versions = [v.partition(':')[2].strip() for v in lines if v.startswith('m_EditorVersion:')]
        if versions != [EXPECTED_UNITY]:
            problems.append(f'Unity version must be {EXPECTED_UNITY}; found {versions!r}')

    manifest_file = root / 'Packages/manifest.json'
    if not manifest_file.is_file():
        problems.append('Missing Packages/manifest.json')
    else:
        try:
            manifest = json.loads(manifest_file.read_text(encoding='utf-8'))
            openxr = manifest['dependencies'].get('com.unity.xr.openxr')
            if openxr != EXPECTED_OPENXR:
                problems.append(f'OpenXR version must be {EXPECTED_OPENXR}; found {openxr!r}')
        except (ValueError, KeyError, TypeError) as error:
            problems.append(f'Invalid Packages/manifest.json: {error}')

    bridge = root / 'Assets/Scripts/QuestChatKeyboardOverlayBridge.cs'
    if bridge.exists():
        if not bridge.is_file() or bridge.stat().st_size == 0:
            problems.append('Unity keyboard bridge exists but is empty or not a file')
    return problems


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else '.').resolve()
    problems = check(root)
    if problems:
        for problem in problems:
            print('FAIL:', problem)
        return 1
    print('PASS: Unity project layout, Unity 2022.3.62f3 and OpenXR 1.14.3')
    if (root / 'Assets/Scripts/QuestChatKeyboardOverlayBridge.cs').is_file():
        print('INFO: Unity bridge file present; runtime/compilation NOT verified')
    else:
        print('INFO: Unity bridge not installed yet; this is an initial preflight')
    print('INFO: No Unity build, APK or Quest 3 keyboard test performed')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
