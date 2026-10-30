<p align="center">
  <img src="brand/png/mark-192.png" width="96" alt="tv-hero">
</p>

<h1 align="center">tv-hero</h1>

<p align="center"><b>The TV your kids love. The rules you trust.</b><br>
A calm, safe home screen and family controls for Android TV, run from your phone.</p>

<p align="center">
  <a href="https://github.com/ajitpal/tv-hero/releases/latest"><img alt="Download" src="https://img.shields.io/github/v/release/ajitpal/tv-hero?label=download&color=5B6CFF"></a>
  <a href="https://github.com/ajitpal/tv-hero/actions/workflows/android.yml"><img alt="Build" src="https://github.com/ajitpal/tv-hero/actions/workflows/android.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="MIT licence" src="https://img.shields.io/badge/licence-MIT-1E1B4B"></a>
  <a href="https://ajitpal.github.io/tv-hero/"><img alt="Website" src="https://img.shields.io/badge/website-ajitpal.github.io%2Ftv--hero-4338CA"></a>
</p>

<p align="center"><img src="docs/screenshots/tv/01-home.png" width="760" alt="tv-hero's Home screen on a TV"></p>

---

## Why tv-hero

Family TV time shouldn't be a daily negotiation. **tv-hero** turns an Android TV into a calm screen with real, fair rules. Each child gets their own apps, time and bedtime, and you stay in charge from your phone.

No ads. No feeds. No accounts. No tracking. Free and open source.

## For parents 👨‍👩‍👧

- **A profile for every child.** Each sees only their own apps plus the ones for everyone, with their own daily limit and bedtime. Up to ten profiles, plus **Guest** with its own allowance.
- **Time's up means stop.** The video pauses, the app closes and nothing opens behind the screen. The only ways on: ask you, earn time, or your PIN.
- **Run it from your phone.** See what's on, give or take minutes, **end today's TV now**, pause the whole TV, set a sleep timer or a night volume cap, and approve requests as they arrive under *Needs You*.
- **Apps you approve.** Newly installed apps wait for your OK. Settings and the Play Store are for parents only.
- **Fair switching.** One free profile switch a day; after that, kids ask you. Moving to a profile without limits always needs a parent.
- **Only real watching counts.** Screen time runs only while an app is open: not Home, the screensaver, Settings or the built-in tools.

## For kids 🧒

- **Earn more time with fun questions.** When the day's TV is used up, kids press **⭐ Earn Time** and answer questions on the big screen: ➗ Maths, 🔤 English words, 🌍 General knowledge, अ Hindi and 🧩 Puzzles, at Easy, Medium or Hard. Each right answer adds minutes, up to a daily maximum you choose. There's a cheer for every right answer, and "No minutes lost. Have another go!" for a wrong one.
- **Timer, Stopwatch and Workout.** Big digits and progress rings for the whole room. HIIT presets like Tabata, with 3-2-1 beeps and a colour for each phase. Leave a workout running and open YouTube: a small timer stays in the corner.
- **A watch screensaver.** An analogue watch with a smooth seconds hand, and the date, on a gradient that follows the time of day.

<p align="center">
  <img src="docs/screenshots/tv/02-watch-screensaver.png" width="380" alt="Watch screensaver">
  <img src="docs/screenshots/tv/03-times-up.png" width="380" alt="Time's up after earning every bonus minute">
</p>

## On your phone 📱

<p align="center">
  <img src="docs/screenshots/phone/17-welcome.png" width="150" alt="Welcome">
  <img src="docs/screenshots/phone/01-tv.png" width="150" alt="TV tab">
  <img src="docs/screenshots/phone/02-family.png" width="150" alt="Family">
  <img src="docs/screenshots/phone/04-kid-profile.png" width="150" alt="A child's profile">
  <img src="docs/screenshots/phone/18-set-up-new-tv.png" width="150" alt="Set up a new TV">
</p>

What's on now, who's watching, time left, kids' requests, a **remote**, typing, and the phone as a **game controller**. It works over your home Wi-Fi, or over the internet when you switch that on.

## Get started in about two minutes

1. **Install the phone app**: `tv-hero-phone-<version>.apk` from the [latest release](https://github.com/ajitpal/tv-hero/releases/latest).
2. **Switch on TV debugging** (the app shows you where). Then tap **Set Up a New TV**. The phone finds the TV, you press Allow, and it installs and sets up tv-hero.
3. **Pair**: compare a six-digit code on both screens, press **Allow** on the TV, create a parent password and add your family.

Full guide: **[ajitpal.github.io/tv-hero/install.html](https://ajitpal.github.io/tv-hero/install.html)**. Everything else, screen by screen: [docs/USER-GUIDE.md](docs/USER-GUIDE.md).

## Built like it matters 🔒

- **Encrypted and pinned.** Phone and TV talk over mutual TLS. Their keys live in the Android Keystore and can't leave either device.
- **A person says yes.** Pairing shows the same code on both screens and needs **Allow on the TV**.
- **Loosening needs the password.** Tightening never does.
- **Internet control is off by default.** Every paired phone is listed and can be removed at once.
- **Verified updates.** The phone checks each download's checksum, version and release signature before installing it on the phone or the TV.
- **No tracking.** Watch history stays on the TV, for your eyes only.

Details: [docs/SECURITY.md](docs/SECURITY.md) · [Privacy](site/privacy.md)

## Works with

Android TV and Google TV, **Android 9 and later**. It was built on, and is lightest on, a Xiaomi Mi TV 4A Pro with 1 GB of RAM. On Google TV devices that insist on their own Home screen, tv-hero runs as a guard: profiles, limits and lock screens still work. The phone app needs Android 9 or later.

---

## For developers

| Part | Folder | What it is |
|---|---|---|
| **TV app** | `tv-launcher/` | Java/Views, built for a 1 GB TV: Home screen, the **guard** (an accessibility service for profiles, limits, lock screens and prompts), tools, the screensaver and the control server. `com.ajit.tvhero` |
| **Phone app** | `companion/` | Kotlin + Compose in an Apple-HIG style: pairing, family, apps, remote, game controller, Set Up a New TV, updates. `com.ajit.tvhero.remote` |
| **Protocol** | `protocol/` | Plain Java shared by both apps: ports, paths, TLS settings, pairing maths, pinning, with test vectors |
| **ADB client** | `adb/` | Plain Java ADB over TCP with RSA auth, a capability probe and setup steps; the phone uses it to install and update the TV app |
| **Tools** | `tools/` | Computer-side scripts: install, debloat and restore, a Python client for the TV's API, release and website builders |
| **Brand** | `brand/` | Logo, wordmark, colours and guidelines ([BRAND.md](brand/BRAND.md)) |
| **Website** | `site/` | Source of [ajitpal.github.io/tv-hero](https://ajitpal.github.io/tv-hero/) |
| **Docs** | `docs/` | [ARCHITECTURE](docs/ARCHITECTURE.md), [SECURITY](docs/SECURITY.md), [PROTOCOL](docs/PROTOCOL.md), [DESIGN](docs/DESIGN.md), [PHASE3](docs/PHASE3.md), [RELEASING](docs/RELEASING.md), [TROUBLESHOOTING](docs/TROUBLESHOOTING.md) |

```bash
# JDK 17+, Android SDK 34
./gradlew :protocol:test :adb:test \
  :tv-launcher:assembleRelease :tv-launcher:testReleaseUnitTest :tv-launcher:lintRelease \
  :companion:assembleRelease :companion:testReleaseUnitTest :companion:lintRelease
./gradlew :companion:testDebugUnitTest -Pscreenshots=record   # phone screenshots → docs/screenshots/phone/
```

Release builds without `keystore.properties` are signed with the debug key; only CI signs real releases. Pushing a tag `vX.Y.Z` builds, tests, signs and publishes both apps with `update.json`, and updates the website ([RELEASING](docs/RELEASING.md)).

Contributions are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md). Found a security problem? Please report it privately ([SECURITY.md](SECURITY.md)).

## Licence

MIT, see [LICENSE](LICENSE). The Outfit font is under the SIL Open Font License (`tv-launcher/FONT-LICENSE-OFL.txt`).
