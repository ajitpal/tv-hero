#!/usr/bin/env python3
"""Builds tv-hero's brand assets from brand/logo-mark.svg and the Outfit font in the TV app:
the wordmark (text as outlines, no font needed to view it), app icons, the TV banner, the website's
favicons and the social preview. Run after changing the mark:  python3 tools/brand/make_brand.py
Needs: fontTools, playwright (Chromium).
"""
import io
from pathlib import Path

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / "brand"
FONT = ROOT / "tv-launcher/src/main/res/font/outfit.ttf"

INDIGO, VIOLET, MIDNIGHT, SPARK, MINT = "#5B6CFF", "#4338CA", "#1E1B4B", "#FFB020", "#30D158"


def text_path(text, weight, size):
    font = instantiateVariableFont(TTFont(FONT), {"wght": weight}, inplace=False)
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    upm = font["head"].unitsPerEm
    scale = size / upm
    x = 0.0
    pen = SVGPathPen(glyphs)
    for ch in text:
        name = cmap[ord(ch)]
        tp = TransformPen(pen, (scale, 0, 0, -scale, x, size * 0.8))
        glyphs[name].draw(tp)
        x += glyphs[name].width * scale
    return pen.getCommands(), x


def mark_inner():
    svg = (BRAND / "logo-mark.svg").read_text()
    return svg[svg.index(">") + 1: svg.rindex("</svg>")]


def wordmark(color, file):
    d, width = text_path("tv-hero", 600, 64)
    w = 108 + 22 + width + 6
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w:.0f} 108" height="108">'
           f'<g>{mark_inner()}</g><path transform="translate(130,26)" d="{d}" fill="{color}"/></svg>')
    (BRAND / file).write_text(svg)
    return svg


def render(svg, size, out, background="transparent"):
    from playwright.sync_api import sync_playwright
    w, h = size
    html = (f"<html><body style='margin:0;background:{background}'>"
            f"<div style='width:{w}px;height:{h}px;display:flex;align-items:center;justify-content:center'>{svg}</div></body></html>")
    with sync_playwright() as p:
        b = p.chromium.launch()
        page = b.new_page(viewport={"width": w, "height": h})
        page.set_content(html)
        page.screenshot(path=str(out), omit_background=background == "transparent")
        b.close()


def sized(svg, w, h):
    return svg.replace('width="512" height="512"', f'width="{w}" height="{h}"', 1)


def main():
    light = wordmark("#1D1D1F", "logo-wordmark.svg")
    dark = wordmark("#FFFFFF", "logo-wordmark-white.svg")
    mark = (BRAND / "logo-mark.svg").read_text()
    out = BRAND / "png"
    out.mkdir(exist_ok=True)
    for s in (512, 192, 180, 64, 32):
        render(sized(mark, s, s), (s, s), out / f"mark-{s}.png")
    # Social preview (GitHub repo card, link previews): 1280 × 640.
    d, width = text_path("tv-hero", 600, 120)
    tag_d, tag_w = text_path("The calm, safe home screen for family TV", 400, 38)
    social = (f'<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="640" viewBox="0 0 1280 640">'
              f'<defs><linearGradient id="s" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#0B1026"/>'
              f'<stop offset="1" stop-color="{MIDNIGHT}"/></linearGradient>'
              f'<radialGradient id="g" cx="0.3" cy="0.3" r="0.8"><stop offset="0" stop-color="{INDIGO}" stop-opacity="0.45"/>'
              f'<stop offset="1" stop-color="{INDIGO}" stop-opacity="0"/></radialGradient></defs>'
              f'<rect width="1280" height="640" fill="url(#s)"/><rect width="1280" height="640" fill="url(#g)"/>'
              f'<g transform="translate(110,190) scale(2.4)">{mark_inner()}</g>'
              f'<path transform="translate(410,206)" d="{d}" fill="#FFFFFF"/>'
              f'<path transform="translate(416,372)" d="{tag_d}" fill="#C7C9F9"/></svg>')
    (BRAND / "social-preview.svg").write_text(social)
    render(social, (1280, 640), out / "social-preview.png", "#0B1026")
    # TV launcher banner (320 × 180 dp, drawn at xhdpi) and launcher icon.
    bd, bw = text_path("tv-hero", 600, 92)
    banner = (f'<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">'
              f'<defs><linearGradient id="b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{INDIGO}"/>'
              f'<stop offset="0.6" stop-color="{VIOLET}"/><stop offset="1" stop-color="{MIDNIGHT}"/></linearGradient></defs>'
              f'<rect width="640" height="360" fill="url(#b)"/>'
              f'<g transform="translate(70,128) scale(1.42)"><path d="M54 22c9.5 4.6 19 6.8 28 7v22.5C82 70 70.5 81.6 54 88 37.5 81.6 26 70 26 51.5V29c9-.2 18.5-2.4 28-7z" fill="#fff" transform="translate(-26,-22)"/>'
              f'<path transform="translate(-26,-22)" d="M47.5 43.2c0-2 2.2-3.3 4-2.2l15.6 9.6c1.6 1 1.6 3.4 0 4.4L51.5 64.6c-1.8 1.1-4-.2-4-2.2z" fill="{VIOLET}"/>'
              f'<path transform="translate(-26,-22)" d="M75 15l2.2 5.8L83 23l-5.8 2.2L75 31l-2.2-5.8L67 23l5.8-2.2z" fill="{SPARK}"/></g>'
              f'<path transform="translate(208,132)" d="{bd}" fill="#FFFFFF"/></svg>')
    (BRAND / "tv-banner.svg").write_text(banner)
    res = ROOT / "tv-launcher/src/main/res"
    render(banner, (640, 360), res / "drawable-xhdpi/banner.png", INDIGO)
    for density, s in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        (res / f"mipmap-{density}").mkdir(exist_ok=True)
        render(sized(mark, s, s), (s, s), res / f"mipmap-{density}/ic_launcher.png")
    print("brand assets written")


if __name__ == "__main__":
    main()
