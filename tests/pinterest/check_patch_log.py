#!/usr/bin/env python3
"""Check a full Pinterest patch run for missing patches and critical hook coverage."""
import argparse
from collections import Counter
from pathlib import Path
import re

PATCHES = """
Choose startup tab
Copy direct link
Disable AppsFlyer tracking
Disable Google Engage
Disable Google Engage worker
Disable ads
Disable email confirmation dialog
Disable third-party trackers
Download board
Download images in highest available quality
Download pin from long press
Download video
Filter pin menu actions
Hide Create nav button
Hide Notifications nav button
Hide Search nav button
Hide ad views
Hide comments
Hide greeting header buttons
Hide save for later tooltip
Hide screenshot share menu
Hide search history
Keep Home feed
Morphe runtime names
Morphe settings entry
Morphe settings screen (label)
Morphe settings screen (manifest)
Neutralize advertising ID
Open links in the default browser
Sanitize copied links
Sanitize shared links
Set pin as wallpaper
Show pin image information
Use the system share sheet
""".strip().splitlines()

HOOKS = {
    "filter video list before dynamic grid operation captures indices": 5,
    "copy video-filtered legacy/search list before ID index rebuild": 1,
    "shared native image pipeline": 1,
    "bind inline metadata to module Pin": 1,
    "launcher-only native pending-tab request": 1,
    "native state retained": 1,
    "unconditional save-anchor gate": 1,
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--firebase-registration", action="store_true", help="expect the optional Firebase repair")
    args = parser.parse_args()
    log = args.log.read_text()
    targets = re.findall(r"Filtering patches for com\.pinterest v(\S+)\.\.\.", log)
    if len(targets) != 1 or targets[0] not in {"14.39.0", "14.43.0"}:
        parser.exit(1, "FAIL expected a single 14.39.0 or 14.43.0 patch run\n")

    applied = Counter(re.findall(r"^INFO: Applied: (.+?)\s*$", log, re.MULTILINE))
    expected = Counter(PATCHES)
    if args.firebase_registration:
        expected["Repair Firebase registration"] = 1
    errors = [f"missing patch: {name}" for name in expected - applied]
    errors.extend(f"unexpected or repeated patch: {name}" for name in applied - expected)
    hooks = [line for line in log.splitlines() if "[morphe]" in line and ": hooked to " in line]
    expected_hooks = {**HOOKS, "restore the Firebase Installations certificate header": int(args.firebase_registration)}
    for marker, count in expected_hooks.items():
        actual = sum(marker in line for line in hooks)
        if actual != count:
            errors.append(f"{marker}: expected {count}, found {actual}")
    expected_meta = int(targets[0] == "14.43.0")
    if sum("gate Meta Audience Network consent" in line for line in hooks) != expected_meta:
        errors.append(f"Meta Audience Network: expected {expected_meta} consent hooks")
    if len(re.findall(r"^INFO: Saved to ", log, re.MULTILINE)) != 1:
        errors.append("CLI did not report one saved APK")
    if errors:
        parser.exit(1, "FAIL " + "\nFAIL ".join(errors) + "\n")
    print(f"PASS {targets[0]}: {sum(expected.values())} patches and {sum(expected_hooks.values()) + expected_meta} critical hooks")


if __name__ == "__main__":
    main()
