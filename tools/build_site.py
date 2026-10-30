#!/usr/bin/env python3
"""Builds the public website from site/*.md, CHANGELOG.md and the phone screenshots.

usage: build_site.py <out-dir>      (needs: pip install markdown==3.7)
"""
import html
import re
import shutil
import sys
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent.parent
# Screens that show a home network address or other setup detail stay out of the public site.
SKIP_SHOTS = {"13-pair.png", "10-who-can-control.png", "11-internet-control.png"}

PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title><meta name="description" content="A calm home screen and family controls for Android TV, run from your phone.">
<style>
:root {{ --bg:#fbfbfd; --fg:#1d1d1f; --mute:#6e6e73; --card:#fff; --tint:#0071e3; --line:#d2d2d7; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#000; --fg:#f5f5f7; --mute:#a1a1a6; --card:#1c1c1e; --tint:#2997ff; --line:#38383a; }} }}
* {{ box-sizing:border-box }} body {{ margin:0; background:var(--bg); color:var(--fg);
 font:17px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif; }}
nav {{ position:sticky; top:0; backdrop-filter:saturate(180%) blur(20px); background:color-mix(in srgb,var(--bg) 80%,transparent);
 border-bottom:1px solid var(--line); z-index:1 }}
nav div {{ max-width:980px; margin:auto; padding:12px 20px; display:flex; gap:22px; align-items:center; flex-wrap:wrap }}
nav a {{ color:var(--fg); text-decoration:none; font-size:14px }} nav a.brand {{ font-weight:600; font-size:17px; margin-right:auto }}
main {{ max-width:980px; margin:auto; padding:32px 20px 80px }}
h1 {{ font-size:clamp(34px,6vw,56px); line-height:1.07; letter-spacing:-.02em; margin:.3em 0 .2em }}
h2 {{ font-size:28px; letter-spacing:-.01em; margin-top:2em }} a {{ color:var(--tint) }}
p, li {{ color:var(--fg) }} li {{ margin:.45em 0 }} code {{ background:var(--card); border:1px solid var(--line); border-radius:6px; padding:1px 6px; font-size:.9em }}
a.button {{ display:inline-block; background:var(--tint); color:#fff; padding:12px 22px; border-radius:980px; text-decoration:none; margin:8px 10px 8px 0; font-weight:500 }}
a.button.ghost {{ background:transparent; color:var(--tint); border:1px solid var(--tint) }}
.shots {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(180px,1fr)); gap:18px; margin-top:16px }}
.shots figure {{ margin:0; min-width:0 }} .shots img {{ width:100%; border-radius:22px; border:1px solid var(--line); background:var(--card) }}
.shots figcaption {{ color:var(--mute); font-size:13px; text-align:center; margin-top:6px }}
footer {{ color:var(--mute); font-size:13px; text-align:center; padding:30px 20px; border-top:1px solid var(--line) }}
</style></head><body>
<nav><div><a class="brand" href="index.html">tv-hero</a><a href="install.html">Install</a><a href="security.html">Security</a>
<a href="privacy.html">Privacy</a><a href="changelog.html">What's new</a>
<a href="https://github.com/ajitpal/tv-hero-releases/releases/latest">Download</a></div></nav>
<main>{body}</main><footer>tv-hero {version} · MIT licence · no accounts, no tracking</footer></body></html>
"""


def render(md_text):
    title = "tv-hero"
    m = re.match(r"---\ntitle: (.*?)\n---\n", md_text)
    if m:
        title, md_text = m.group(1), md_text[m.end():]
    body = markdown.markdown(md_text, extensions=["attr_list"])
    return title, body


def main():
    out = Path(sys.argv[1])
    if out.exists():
        shutil.rmtree(out)
    (out / "shots").mkdir(parents=True)
    version = re.search(r"^## (\S+)", (ROOT / "CHANGELOG.md").read_text(), re.M).group(1)
    shots = []
    for png in sorted((ROOT / "docs/screenshots/phone").glob("*.png")):
        if png.name in SKIP_SHOTS:
            continue
        shutil.copyfile(png, out / "shots" / png.name)
        caption = re.sub(r"^\d+-", "", png.stem).replace("-", " ").capitalize()
        shots.append(f'<figure><img src="shots/{png.name}" alt="{html.escape(caption)}" loading="lazy">'
                     f"<figcaption>{html.escape(caption)}</figcaption></figure>")
    gallery = '<div class="shots">' + "".join(shots) + "</div>"
    for md in sorted((ROOT / "site").glob("*.md")):
        title, body = render(md.read_text())
        body = body.replace("<p>{{screenshots}}</p>", gallery)
        (out / (md.stem + ".html")).write_text(PAGE.format(title=html.escape(title), body=body, version=version))
    changelog = (ROOT / "CHANGELOG.md").read_text()
    _, body = render(changelog)
    (out / "changelog.html").write_text(PAGE.format(title="What's new in tv-hero", body=body, version=version))
    (out / ".nojekyll").write_text("")
    print(f"site for {version}: {len(list(out.glob('*.html')))} pages, {len(shots)} screenshots")


if __name__ == "__main__":
    main()
