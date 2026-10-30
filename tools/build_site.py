#!/usr/bin/env python3
"""Builds the public website (GitHub Pages) from site/, brand/, CHANGELOG.md and the screenshots.

usage: build_site.py <out-dir>      (needs: pip install markdown==3.7)
"""
import html
import re
import shutil
import sys
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / "site"
# Phone screens shown on the site, with captions. Setup screens with addresses stay out.
PHONES = [
    ("17-welcome.png", "Welcome"),
    ("01-tv.png", "What's on"),
    ("04-kid-profile.png", "A child's rules"),
    ("16-guest.png", "Guest Time"),
    ("14-tv-dark.png", "Dark mode"),
    ("18-set-up-new-tv.png", "Set up a new TV"),
    ("07-remote.png", "Remote"),
    ("15-family-dark.png", "Family"),
]
EXTRA_PHONES = ["02-family.png"]  # used directly by index.html
SWATCHES = [("Hero Indigo", "#5B6CFF"), ("Deep Violet", "#4338CA"), ("Midnight", "#1E1B4B"),
            ("Spark Amber", "#FFB020"), ("Mint", "#30D158"), ("Alert Red", "#FF453A")]


def md_page(md_text):
    title = "tv-hero"
    m = re.match(r"---\ntitle: (.*?)\n---\n", md_text)
    if m:
        title, md_text = m.group(1), md_text[m.end():]
    return title, markdown.markdown(md_text, extensions=["attr_list", "tables", "fenced_code"])


def main():
    out = Path(sys.argv[1])
    if out.exists():
        shutil.rmtree(out)
    (out / "assets").mkdir(parents=True)
    (out / "shots/phone").mkdir(parents=True)
    (out / "shots/tv").mkdir(parents=True)
    version = re.search(r"^## (\S+)", (ROOT / "CHANGELOG.md").read_text(), re.M).group(1)

    for f in (SITE / "assets").iterdir():
        shutil.copyfile(f, out / "assets" / f.name)
    for f in ("mark-32.png", "mark-64.png", "mark-180.png", "mark-192.png", "mark-512.png", "social-preview.png"):
        shutil.copyfile(ROOT / "brand/png" / f, out / "assets" / f)
    for f in ("logo-mark.svg", "logo-wordmark.svg", "logo-wordmark-white.svg"):
        shutil.copyfile(ROOT / "brand" / f, out / "assets" / f)
    for f in (ROOT / "docs/screenshots/tv").glob("*.png"):
        shutil.copyfile(f, out / "shots/tv" / f.name)

    for name in EXTRA_PHONES:
        shutil.copyfile(ROOT / "docs/screenshots/phone" / name, out / "shots/phone" / name)
    phones = []
    for name, caption in PHONES:
        shutil.copyfile(ROOT / "docs/screenshots/phone" / name, out / "shots/phone" / name)
        phones.append(f'<figure><img src="shots/phone/{name}" alt="{html.escape(caption)}" loading="lazy">'
                      f"<figcaption>{html.escape(caption)}</figcaption></figure>")

    index = (SITE / "index.html").read_text().replace("{{version}}", version).replace("{{phones}}", "".join(phones))
    (out / "index.html").write_text(index)

    shell = (SITE / "_page.html").read_text()

    def page(name, title, body, eyebrow):
        body = re.sub(r"<h1>.*?</h1>", "", body, count=1, flags=re.S)  # the title is in the page header
        (out / name).write_text(shell.replace("{{title}}", html.escape(title)).replace("{{body}}", body)
                                .replace("{{eyebrow}}", html.escape(eyebrow)).replace("{{version}}", version))

    eyebrows = {"install": "Get started", "security": "Trust", "privacy": "Trust"}

    for md in sorted(SITE.glob("*.md")):
        title, body = md_page(md.read_text())
        page(md.stem + ".html", title, body, eyebrows.get(md.stem, "Guide"))
    _, body = md_page((ROOT / "CHANGELOG.md").read_text())
    page("changelog.html", "What's new", body, "Releases")

    brand = (ROOT / "brand/BRAND.md").read_text().replace("](png/", "](assets/")
    _, body = md_page(brand)
    sw = "".join(f'<div class="swatch"><div style="background:{h}"></div><span><b>{n}</b><br>{h}</span></div>' for n, h in SWATCHES)
    body = body.replace("<h2>Colour</h2>", '<h2>Colour</h2><div class="swatches">' + sw + "</div>", 1)
    page("brand.html", "Brand", '<img src="assets/logo-wordmark.svg" alt="tv-hero" style="height:64px">' + body, "Guidelines")

    (out / ".nojekyll").write_text("")
    print(f"site for {version}: {len(list(out.glob('*.html')))} pages")


if __name__ == "__main__":
    main()
