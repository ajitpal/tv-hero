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
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gallery  # noqa: E402  (tools/gallery.py: every screenshot with title, description and tags)

SITE_URL = "https://ajitpal.github.io/tv-hero/"
THUMB = {"tv": (640, 360), "phone": (270, 600)}
SWATCHES = [("Hero Indigo", "#5B6CFF"), ("Deep Violet", "#4338CA"), ("Midnight", "#1E1B4B"),
            ("Spark Amber", "#FFB020"), ("Mint", "#30D158"), ("Alert Red", "#FF453A")]


def md_page(md_text):
    title = "tv-hero"
    m = re.match(r"---\ntitle: (.*?)\n---\n", md_text)
    if m:
        title, md_text = m.group(1), md_text[m.end():]
    return title, markdown.markdown(md_text, extensions=["attr_list", "tables", "fenced_code"])


def copy_shots(out):
    """Full-size screenshots plus small WebP thumbnails (fast pages; the viewer loads the full PNG)."""
    from PIL import Image
    for d in ("tv", "phone"):
        (out / "shots" / d).mkdir(parents=True, exist_ok=True)
        (out / "shots/thumbs" / d).mkdir(parents=True, exist_ok=True)
    for device, name, *_ in gallery.SHOTS:
        src = ROOT / "docs/screenshots" / device / name
        shutil.copyfile(src, out / "shots" / device / name)
        im = Image.open(src).convert("RGB")
        im.thumbnail(THUMB[device], Image.LANCZOS)
        im.save(out / "shots/thumbs" / device / (Path(name).stem + ".webp"), "WEBP", quality=82, method=6)
    return gallery.SHOTS


def filters(kind):
    chips = [("all", "All"), ("tv", "On the TV"), ("phone", "On the phone")] + gallery.TAGS
    return (f'<div class="filters" role="toolbar" aria-label="Filter screenshots" data-for="{kind}">'
            + "".join(f'<button type="button" data-filter="{t}"{" class=on" if t == "all" else ""}>{html.escape(l)}</button>'
                      for t, l in chips) + "</div>")


def shot_link(sh, grid=False):
    device, name, title, desc, tags = sh
    w, h = (640, 360) if device == "tv" else (270, 600)
    where = "On the TV" if device == "tv" else "On the phone"
    alt = f"tv-hero {where.lower()}: {title}. {desc}"
    chips = " · ".join(gallery.label(t) for t in tags)
    return (f'<a class="shot s-{device}" href="shots/{device}/{name}" data-tags="{device} {" ".join(tags)}" '
            f'data-title="{html.escape(title)}" data-desc="{html.escape(desc)}" data-meta="{html.escape(where + " · " + chips)}">'
            f'<img src="shots/thumbs/{device}/{Path(name).stem}.webp" width="{w}" height="{h}" alt="{html.escape(alt)}" loading="lazy" decoding="async">'
            f'<span class="cap"><b>{html.escape(title)}</b><em>{html.escape(where)}</em></span></a>')


def json_ld(shots):
    import json
    data = {"@context": "https://schema.org", "@type": "ImageGallery", "name": "tv-hero screenshots",
            "description": "Screens of tv-hero, the family TV home screen and parental controls for Android TV, and its phone app.",
            "url": SITE_URL + "gallery.html",
            "associatedMedia": [{"@type": "ImageObject", "contentUrl": f"{SITE_URL}shots/{d}/{n}",
                                 "thumbnailUrl": f"{SITE_URL}shots/thumbs/{d}/{Path(n).stem}.webp",
                                 "name": "tv-hero: " + t, "caption": c, "description": c,
                                 "keywords": ", ".join(["tv-hero", "Android TV", "parental controls"] + [gallery.label(x) for x in tags])}
                                for d, n, t, c, tags in shots]}
    app = {"@context": "https://schema.org", "@type": "SoftwareApplication", "name": "tv-hero",
           "applicationCategory": "LifestyleApplication", "operatingSystem": "Android TV, Android 9+",
           "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"}, "url": SITE_URL,
           "license": "https://opensource.org/licenses/MIT", "screenshot": [f"{SITE_URL}shots/{d}/{n}" for d, n, *_ in shots[:8]],
           "description": "A calm, safe home screen and family controls for Android TV, run from your phone."}
    return ('<script type="application/ld+json">' + json.dumps(data, ensure_ascii=False) + "</script>"
            '<script type="application/ld+json">' + json.dumps(app, ensure_ascii=False) + "</script>")


def main():
    if len(sys.argv) != 2 or sys.argv[1].startswith("-"):
        sys.exit("usage: build_site.py <out-dir>")
    out = Path(sys.argv[1])
    if out.exists():
        shutil.rmtree(out)
    (out / "assets").mkdir(parents=True)
    version = re.search(r"^## (\S+)", (ROOT / "CHANGELOG.md").read_text(), re.M).group(1)

    for f in (SITE / "assets").iterdir():
        shutil.copyfile(f, out / "assets" / f.name)
    for f in ("mark-32.png", "mark-64.png", "mark-180.png", "mark-192.png", "mark-512.png", "social-preview.png"):
        shutil.copyfile(ROOT / "brand/png" / f, out / "assets" / f)
    for f in ("logo-mark.svg", "logo-wordmark.svg", "logo-wordmark-white.svg"):
        shutil.copyfile(ROOT / "brand" / f, out / "assets" / f)
    shots = copy_shots(out)
    strip = filters("strip") + '<div class="strip">' + "".join(shot_link(sh) for sh in shots) + "</div>"
    index = (SITE / "index.html").read_text().replace("{{version}}", version).replace("{{gallery_strip}}", strip) \
        .replace("</head>", json_ld(shots) + "</head>", 1)
    (out / "index.html").write_text(index)

    shell = (SITE / "_page.html").read_text()

    def page(name, title, body, eyebrow, wide=False, head="", description=None):
        body = re.sub(r"<h1>.*?</h1>", "", body, count=1, flags=re.S)  # the title is in the page header
        text = shell.replace("{{title}}", html.escape(title)).replace("{{body}}", body) \
            .replace("{{eyebrow}}", html.escape(eyebrow)).replace("{{version}}", version).replace("{{path}}", name) \
            .replace("</head>", head + "</head>", 1)
        if description:
            text = re.sub(r'<meta name="description" content="[^"]*">', f'<meta name="description" content="{html.escape(description)}">', text, count=1)
        if wide:
            text = text.replace('<main class="page">', '<main class="page wide">', 1)
        (out / name).write_text(text)

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

    body = ('<p class="lead center">Every screen of tv-hero, on the TV and on the phone. Tap one to see it full size; '
            "filter by what you care about.</p>" + filters("grid")
            + '<h2 class="grid-head">On the TV</h2><div class="grid-shots g-tv">' + "".join(shot_link(sh) for sh in shots if sh[0] == "tv") + "</div>"
            + '<h2 class="grid-head">On the phone</h2><div class="grid-shots g-phone">' + "".join(shot_link(sh) for sh in shots if sh[0] == "phone") + "</div>"
            + '<p class="note">Screens are rendered with made-up sample families. Names, numbers and addresses are not real.</p>')
    page("gallery.html", "Every screen", body, "Gallery", wide=True, head=json_ld(shots),
         description="Screenshots of tv-hero: the calm Android TV Home screen, Time's up and bedtime screens, the Earn Time quiz for kids, "
                     "the Parent Menu, tools like Timer and Tabata workout, and the parents' phone app.")

    pages = ["", "gallery.html", "install.html", "security.html", "privacy.html", "changelog.html", "brand.html"]
    urls = []
    for pg in pages:
        imgs = ""
        if pg in ("", "gallery.html"):
            imgs = "".join(f"<image:image><image:loc>{SITE_URL}shots/{sh[0]}/{sh[1]}</image:loc>"
                           f"<image:title>{html.escape('tv-hero: ' + sh[2])}</image:title>"
                           f"<image:caption>{html.escape(sh[3])}</image:caption></image:image>" for sh in shots)
        urls.append(f"<url><loc>{SITE_URL}{pg}</loc>{imgs}</url>")
    (out / "sitemap.xml").write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" '
                                     'xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">' + "".join(urls) + "</urlset>\n")
    (out / "robots.txt").write_text(f"User-agent: *\nAllow: /\nSitemap: {SITE_URL}sitemap.xml\n")
    (out / ".nojekyll").write_text("")
    print(f"site for {version}: {len(list(out.glob('*.html')))} pages")


if __name__ == "__main__":
    main()
