#!/usr/bin/env python3
"""Release checks (CI): both apps share one version, it has a CHANGELOG section, and a tag matches it.

usage: check_release.py [tag]     e.g. check_release.py v2.7.0
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def version(gradle):
    text = (ROOT / gradle).read_text()
    name = re.search(r"versionName\s+['\"]([^'\"]+)['\"]", text).group(1)
    code = int(re.search(r"versionCode\s+(\d+)", text).group(1))
    return name, code


def main():
    tv, tv_code = version("tv-launcher/build.gradle")
    phone, phone_code = version("companion/build.gradle")
    problems = []
    if tv != phone:
        problems.append(f"TV app is {tv} but the phone app is {phone}: they must match")
    changelog = (ROOT / "CHANGELOG.md").read_text()
    if f"\n## {tv}\n" not in changelog:
        problems.append(f"CHANGELOG.md has no '## {tv}' section")
    if len(sys.argv) > 1 and sys.argv[1].lstrip("v") != tv:
        problems.append(f"tag {sys.argv[1]} doesn't match version {tv}")
    if problems:
        for p in problems:
            print("::error::" + p)
        sys.exit(1)
    print(f"version {tv} (TV code {tv_code}, phone code {phone_code}) ok")


if __name__ == "__main__":
    main()
