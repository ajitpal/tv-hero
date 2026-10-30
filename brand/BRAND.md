# tv-hero brand guidelines

tv-hero is the calm, safe home screen for family TV. The brand should feel like a good babysitter: **warm, trustworthy and a little bit fun**, never loud.

## Name

- Written **tv-hero**: all lower case, with a hyphen. Never "TVHero", "Tv Hero" or "TV-HERO".
- At the start of a sentence it stays lower case: "tv-hero keeps…".
- The apps are "tv-hero" on the TV and "tv-hero" on the phone. When you need to tell them apart: "the TV app", "the phone app".

## The mark

![mark](png/mark-192.png)

| Part | Means |
|---|---|
| **Shield** | Protection: profiles, limits, bedtime and a pinned, encrypted connection |
| **Play** | The fun part: it's still a TV, made for watching |
| **Spark** (amber star) | Time earned: kids win minutes with questions; the small reward that makes rules feel fair |

- Files: `logo-mark.svg` (app icon, avatar), `logo-wordmark.svg` (light backgrounds), `logo-wordmark-white.svg` (dark backgrounds), `social-preview.png` (1280 × 640), `tv-banner.svg` (the TV's launcher banner).
- **Clear space:** at least the spark's width on every side.
- **Minimum size:** 24 px for the mark, 96 px wide for the wordmark.
- **Don't:** recolour the shield, rotate it, add effects or outlines, put the mark on a busy photo, or move the spark.
- The PNGs, the TV icons and the banner are generated from the SVG: `python3 tools/brand/make_brand.py`.

## Website

The site follows an Apple-like rhythm: full-width sections alternating Night (#0B1026) and white or Mist (#F5F5F7), Outfit Light for big headlines, small spaced-out monospace labels above them, real TV and phone mockups, and one accent colour. Motion is slow and quiet (fade-up on scroll, a dotted wave behind the hero) and stops for people who prefer reduced motion.

## Colour

| Name | Hex | Use |
|---|---|---|
| Hero Indigo | `#5B6CFF` | Primary: links, buttons, focus, the start of the brand gradient |
| Deep Violet | `#4338CA` | The middle of the gradient, the play triangle, pressed states |
| Midnight | `#1E1B4B` | The end of the gradient; dark backgrounds with `#0B1026` |
| Spark Amber | `#FFB020` | Rewards and timers only: earned time, the spark, countdown accents |
| Mint | `#30D158` | Success: allowed, done, rest phases |
| Alert Red | `#FF453A` | Destructive actions and the last seconds of a timer |
| Ink / Paper | `#1D1D1F` / `#FBFBFD` | Text and backgrounds on light pages |

The brand gradient runs **Indigo → Violet → Midnight**, top-left to bottom-right (315°). Amber is an accent: use it on less than a tenth of any screen.

On the TV, screens that lock the TV (Time's up, Bedtime, Paused) are opaque and dark, so nothing behind them shows. Workout phases use colour to say what to do: blue get ready, red work, teal rest, purple set rest.

## Type

- **Outfit** (open source, in the TV app) for display text: the wordmark, big clocks and timers, headlines. Weights: ExtraLight 200 for big digits, Regular 400, Medium 500, SemiBold 600 for the wordmark.
- **System font** (SF Pro, Roboto, Segoe UI) for body text and the phone app, which follows Apple's Human Interface Guidelines.
- On the TV, text is read from the sofa: body at least 18 sp, buttons 20–24 sp, timers 84–170 sp.

## Voice

- **To parents:** calm, plain and honest. Say what happens ("The TV shows Time's up and the app closes"), not how clever it is. Name limits openly.
- **To kids** (on the TV): short, friendly and encouraging. "That's all the TV for today, Aarav", "No minutes lost. Have another go!", "Well done!". Never shaming.
- Title Case for buttons and menu items (Earn Time, Ask for Time). Sentence case for everything else.
- No exclamation marks except in cheers for kids. No jargon: say "home Wi-Fi", not "LAN"; "TV debugging", not "ADB".

## Motion

Gentle and quick: 200–420 ms, decelerating. Screens rise softly as they open, focus scales tiles by about 10%, timer rings fill smoothly, and the last three seconds beat once each. Nothing flashes or bounces.

## Layout and UI

- **TV:** D-pad first. Every action is reachable with arrows and OK, focus is always visible (white pill), and Back always means "leave" (asking first if something is running).
- **Phone:** inset grouped lists, large titles, one primary action per screen, and the password asked for only when a rule is loosened.
- **Website:** the dark hero with the indigo glow, white cards on light pages, rounded 22 px corners and generous space.
