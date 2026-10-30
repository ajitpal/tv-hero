#!/usr/bin/env python3
"""Collects a release (CI): both signed APKs under stable names, update.json for the phone's updater, and
NOTES.md from this version's CHANGELOG section.

usage: collect_release.py <version> <out-dir>
"""
import hashlib
import json
import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RELEASES = "https://github.com/ajitpal/tv-hero-releases/releases"
APPS = [
    # id, package, gradle file, built APK
    ("tv", "com.ajit.tvhero", "tv-launcher/build.gradle", "tv-launcher/build/outputs/apk/release/tv-launcher-release.apk"),
    ("phone", "com.ajit.tvhero.remote", "companion/build.gradle", "companion/build/outputs/apk/release/companion-release.apk"),
]
CERT = "309705ca333f547dfc8bb7d5d02cff8ca63b64cc49d5a533f45b6e06544c9124"


def notes(version):
    text = (ROOT / "CHANGELOG.md").read_text()
    m = re.search(rf"^## {re.escape(version)}\n(.*?)(?=^## |\Z)", text, re.S | re.M)
    if not m:
        sys.exit(f"CHANGELOG.md has no section for {version}")
    return m.group(1).strip() + "\n"


def main():
    version, out = sys.argv[1], Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    apps = []
    for app_id, package, gradle, apk in APPS:
        text = (ROOT / gradle).read_text()
        name = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", text).group(1)
        code = int(re.search(r"versionCode\s+(\d+)", text).group(1))
        if name != version:
            sys.exit(f"{gradle} says {name}, not {version}")
        file = f"tv-hero-{app_id}-{version}.apk"
        shutil.copyfile(ROOT / apk, out / file)
        data = (out / file).read_bytes()
        apps.append({
            "id": app_id, "package": package, "versionCode": code, "versionName": name, "file": file,
            "url": f"{RELEASES}/download/v{version}/{file}",
            "sha256": hashlib.sha256(data).hexdigest(), "size": len(data),
        })
    manifest = {
        "version": version, "tag": f"v{version}",
        "published": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "notes": f"{RELEASES}/tag/v{version}",
        "cert_sha256": CERT, "apps": apps,
    }
    (out / "update.json").write_text(json.dumps(manifest, indent=1) + "\n")
    (out / "NOTES.md").write_text(notes(version) + "\n**Install:** phone app first (`tv-hero-phone`), then update the TV from "
                                  "the phone (Settings → About → Software Update). SHA-256 checksums are in `update.json`.\n")
    print(json.dumps(manifest, indent=1))


if __name__ == "__main__":
    main()
