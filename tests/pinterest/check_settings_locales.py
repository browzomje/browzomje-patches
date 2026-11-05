#!/usr/bin/env python3
"""Compare the extension catalog with a decoded Pinterest APK locale configuration."""
from pathlib import Path
import runpy
import sys

if __name__ == "__main__":
    if len(sys.argv) == 2 and not sys.argv[1].startswith("-"):
        sys.argv.insert(1, "--locale-config")
    runpy.run_path(str(Path(__file__).resolve().parents[2] / "scripts/pinterest_l10n.py"), run_name="__main__")
