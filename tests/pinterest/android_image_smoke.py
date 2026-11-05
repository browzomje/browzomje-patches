#!/usr/bin/env python3
"""Open a public Pin on an unlocked Android test device and verify a new image download."""
import argparse
import hashlib
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.pinterest"
DOWNLOAD_DIR = "/sdcard/Download/Pinterest/Pins"


def adb(*args):
    return subprocess.run(
        ["adb", *args], check=True, capture_output=True, timeout=30
    ).stdout


def wait_for(read, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = read()
        if result:
            return result
        time.sleep(1)
    return None


def bounds_for(selector):
    output = adb("exec-out", "uiautomator", "dump", "/dev/tty")
    start, end = output.find(b"<?xml"), output.rfind(b"</hierarchy>")
    if start == -1 or end == -1:
        return None
    try:
        root = ET.fromstring(output[start:end + len(b"</hierarchy>")])
    except ET.ParseError:
        return None
    matches = set()
    for node in root.iter("node"):
        if node.get("package") != PACKAGE:
            continue
        if selector not in (node.get("resource-id"), node.get("text"), node.get("content-desc")):
            continue
        coords = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if coords:
            left, top, right, bottom = map(int, coords.groups())
            if right > left and bottom > top:
                matches.add(((left + right) // 2, (top + bottom) // 2))
    if len(matches) > 1:
        raise RuntimeError("selector matched multiple controls")
    return next(iter(matches), None)


def tap(point):
    adb("shell", "input", "tap", *map(str, point))


def files_for(pin_id):
    if not adb("shell", f"test -d {DOWNLOAD_DIR} && echo yes || true").strip():
        return set()
    pattern = re.compile(rf"{pin_id}(?: \(\d+\))?\.(?:jpe?g|png|webp|gif)", re.IGNORECASE)
    return {
        name for name in adb("shell", "ls", "-1", DOWNLOAD_DIR).decode().splitlines()
        if pattern.fullmatch(name)
    }


def is_image(data):
    return (
        (data.startswith(b"\xff\xd8\xff") and data.endswith(b"\xff\xd9"))
        or (data.startswith(b"\x89PNG\r\n\x1a\n") and data.endswith(b"IEND\xaeB\x60\x82"))
        or (data.startswith((b"GIF87a", b"GIF89a")) and data.endswith(b";"))
        or (data.startswith(b"RIFF") and data[8:12] == b"WEBP"
            and int.from_bytes(data[4:8], "little") + 8 == len(data))
    )


def verify_installed_apk(apk):
    user = adb("shell", "am", "get-current-user").decode().strip()
    if not user.isdecimal():
        raise RuntimeError("could not determine Android's active user")
    paths = adb("shell", "pm", "path", "--user", user, PACKAGE).decode().splitlines()
    if len(paths) != 1 or not paths[0].startswith("package:"):
        raise RuntimeError("expected one installed Pinterest APK for the active user")
    installed = paths[0].removeprefix("package:")
    digest = adb("shell", "sha256sum " + shlex.quote(installed)).decode().split()[0]
    with apk.open("rb") as source:
        expected = hashlib.file_digest(source, "sha256").hexdigest()
    if digest != expected:
        raise RuntimeError("installed Pinterest does not match the candidate APK")


def run(args):
    verify_installed_apk(args.apk)
    before = files_for(args.pin_id)
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW",
        "-d", f"https://www.pinterest.com/pin/{args.pin_id}/", "-p", PACKAGE)
    menu = wait_for(lambda: bounds_for(args.menu), 30)
    if menu is None:
        raise RuntimeError("Pin menu did not appear")
    if args.swipe:
        adb("shell", "input", "swipe", *map(str, args.swipe), "400")
        time.sleep(1)
        menu = wait_for(lambda: bounds_for(args.menu), 15)
        if menu is None:
            raise RuntimeError("Pin menu did not appear after swipe")
    tap(menu)
    download = wait_for(lambda: bounds_for(args.download), 20)
    if download is None:
        raise RuntimeError("download action did not appear")
    start = time.monotonic()
    tap(download)

    def downloaded():
        for name in sorted(files_for(args.pin_id) - before):
            data = adb("exec-out", "cat " + shlex.quote(f"{DOWNLOAD_DIR}/{name}"))
            if is_image(data) and (not args.sha256 or hashlib.sha256(data).hexdigest() == args.sha256):
                return True
        return False

    if not wait_for(downloaded, args.timeout):
        raise RuntimeError("no complete matching download before timeout")
    print(f"PASS image download in {time.monotonic() - start:.1f}s")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pin_id", help="numeric ID of a deliberately public test Pin")
    parser.add_argument("--apk", type=Path, required=True, help="exact patched APK installed on the test device")
    parser.add_argument("--menu", required=True, help="exact resource ID, text or accessibility label")
    parser.add_argument("--download", required=True, help="exact resource ID, text or accessibility label")
    parser.add_argument("--swipe", nargs=4, type=int, metavar=("X1", "Y1", "X2", "Y2"))
    parser.add_argument("--sha256", help="expected image hash for the current download quality setting")
    parser.add_argument("--timeout", type=int, default=60, help="seconds allowed after tapping Download")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+", args.pin_id):
        parser.error("Pin ID must contain only digits")
    if args.sha256 and not re.fullmatch(r"[0-9a-f]{64}", args.sha256):
        parser.error("expected SHA-256 must use 64 lowercase hexadecimal characters")
    if args.swipe and not args.sha256:
        parser.error("a carousel swipe requires the selected page's expected SHA-256")
    if args.timeout <= 0:
        parser.error("timeout must be positive")
    try:
        run(args)
    except RuntimeError as error:
        parser.exit(1, f"FAIL {error}\n")
    except (subprocess.SubprocessError, OSError):
        parser.exit(1, "FAIL Android test device command failed\n")


if __name__ == "__main__":
    main()
