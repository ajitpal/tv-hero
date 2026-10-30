#!/usr/bin/env python3
"""Fails if anything about to be published (website, release notes) contains private details: home network
addresses, Tailscale addresses, passwords or key material.

usage: check_public.py <dir-or-file>...
"""
import re
import sys
from pathlib import Path

PATTERNS = [
    (r"\b192\.168\.\d+\.\d+\b", "a home network address"),
    (r"\b10\.\d+\.\d+\.\d+\b", "a private network address"),
    (r"\b100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.\d+\.\d+\b", "a Tailscale address"),
    (r"(?i)password\s*[:=]\s*\S+", "a password"),
    (r"-----BEGIN [A-Z ]*PRIVATE KEY-----", "a private key"),
    (r"(?i)storePassword|keyPassword", "signing configuration"),
]


def main():
    bad = 0
    for arg in sys.argv[1:]:
        p = Path(arg)
        files = [p] if p.is_file() else [f for f in p.rglob("*") if f.suffix in {".html", ".md", ".json", ".txt", ".css", ".js"}]
        for f in files:
            text = f.read_text(errors="ignore")
            for rx, what in PATTERNS:
                for m in re.finditer(rx, text):
                    print(f"::error file={f}::{what}: {m.group(0)[:40]}")
                    bad += 1
    if bad:
        sys.exit(1)
    print("nothing private found")


if __name__ == "__main__":
    main()
