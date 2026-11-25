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
    parser.add_argument("--version", choices=("14.39.0", "14.43.0", "14.44.0", "14.45.0"), help="require this Pinterest version in the log")
    parser.add_argument("--microg", action="store_true", help="expect the optional microG routes")
    parser.add_argument("--bottom-bar-messages", action="store_true", help="expect the optional Messages tab")
    parser.add_argument("--bottom-bar-create", action="store_true", help="expect the optional Create button")
    parser.add_argument("--quick-save", action="store_true", help="expect the optional quick save patch")
    args = parser.parse_args()
    log = args.log.read_text()
    targets = re.findall(r"Filtering patches for com\.pinterest v(\S+)\.\.\.", log)
    if len(targets) != 1 or targets[0] not in {"14.39.0", "14.43.0", "14.44.0", "14.45.0"}:
        parser.exit(1, "FAIL expected a single supported Pinterest patch run\n")

    if args.version is not None and targets[0] != args.version:
        parser.exit(1, f"FAIL log version {targets[0]} differs from requested {args.version}\n")

    applied = Counter(re.findall(r"^INFO: Applied: (.+?)\s*$", log, re.MULTILINE))
    expected = Counter(PATCHES)
    latest = targets[0] == "14.45.0"
    if latest:
        expected["Hide repeat Save in saved pin menus"] = 1
    if args.firebase_registration:
        expected["Repair Firebase registration"] = 1
    if args.microg:
        expected["GmsCore support for Pinterest"] = 1
    if args.bottom_bar_messages:
        expected["Show Messages in the bottom bar"] = 1
    if args.bottom_bar_create:
        expected["Show Create in the bottom bar"] = 1
    if args.quick_save:
        expected["Quick save to profile"] = 1
    errors = [f"missing patch: {name}" for name in expected - applied]
    errors.extend(f"unexpected or repeated patch: {name}" for name in applied - expected)
    hooks = [line for line in log.splitlines() if "[morphe]" in line and ": hooked to " in line]
    expected_hooks = {**HOOKS, "restore the Firebase Installations certificate header": int(args.firebase_registration)}
    expected_hooks["route credential provider and messaging to microG-RE"] = int(args.microg)
    expected_hooks["long-press menu items"] = 1
    expected_hooks["bind the native long-press selection"] = int(targets[0] in {"14.44.0", "14.45.0"})
    for marker in ("native board option icons", "shared saved pin long-press options",
                   "Compose long-press download item", "Compose long-press selected pin"):
        expected_hooks[marker] = int(latest)
    for marker in ("bind profile toggle and board picker", "bind profile toggle to legacy close-up modules",
                   "bind profile toggle to radial pin"):
        expected_hooks[marker] = int(args.quick_save)
    for marker, count in expected_hooks.items():
        actual = sum(marker in line for line in hooks)
        if actual != count:
            errors.append(f"{marker}: expected {count}, found {actual}")
    expected_meta = int(targets[0] in {"14.43.0", "14.44.0", "14.45.0"})
    if sum("gate Meta Audience Network consent" in line for line in hooks) != expected_meta:
        errors.append(f"Meta Audience Network: expected {expected_meta} consent hooks")
    if len(re.findall(r"^INFO: Saved to ", log, re.MULTILINE)) != 1:
        errors.append("CLI did not report one saved APK")
    if errors:
        parser.exit(1, "FAIL " + "\nFAIL ".join(errors) + "\n")
    print(f"PASS {targets[0]}: {sum(expected.values())} patches and {sum(expected_hooks.values()) + expected_meta} critical hooks")


if __name__ == "__main__":
    main()
