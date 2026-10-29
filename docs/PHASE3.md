# Phase 3 plan: any Android TV, set up from the phone

Status (29 Oct 2026):
- **M1 done:** ADB client (`adb/`) and capability probe. The real TV was probed with it; it reports Full level on the Xiaomi. The encoded public key matches `adbkey.pub` byte for byte, and a streamed install took 3.3 s.
- **M3 built (2.6):** Timer, Stopwatch and Workout (HIIT) on the TV, a Tools button on Home and a "tv-hero Tools" launcher entry, and phone TV tab → Tools with live control and custom workouts. Tools don't count as screen time.
- **M2 built:** phone Settings → About → TV App: Check the TV and Update the TV App, with the APK built into the phone app. It still needs a check on Ajit's phone.
- The base screensaver (2.4) is also done.
- Everything else here is still a plan.

**Target:** any Android TV or Google TV box or television (Android 9 / API 28 and later), not only the Xiaomi MiTV-AXSO0 that tv-hero was built on. Someone with only the tv-hero phone app sets one up to the state the home TV is in today, and gets the same parental controls, screensaver and tools.

Phase 3 delivers three things, on every supported TV and at every setup level:

| Part | What the family gets | At Full level | At Guard only | At Remote only |
|---|---|---|---|---|
| **A. Setup** | Any Android TV set up from the phone alone | ✓ | ✓ | ✓ |
| **B. Screensaver** | Watch + date, then weather scenes, as the TV's screensaver and on demand | ✓ TV screensaver + Home idle + button | ✓ as the TV screensaver where allowed, else as an app | ✓ as an app |
| **C. Tools** | Timer, stopwatch, HIIT with music, more built-in tools, add-ons others build | ✓ Tools row on Home | ✓ Tools app on the TV's home | ✓ Tools app |

All three ship in **one TV app** (tv-hero) that the setup wizard installs. The screensaver and the tools are
chosen in the wizard (step "Extras") and can be changed later from the phone or the TV.

Section D is the device matrix and testing that make "any Android TV" true for all three parts.

Statements marked *(check)* are what I expect from Android's documentation and earlier work, but they haven't been tried on that kind of device yet. They're the first things to test.

---

## What changes when it isn't one known TV

The 2.x work assumed one model: a known package list to disable, Android 9 rules, a launcher that can replace Home, a known remote. Across all Android TVs, each of these varies:

| Area | How it varies | Phase 3 answer |
|---|---|---|
| Android version | 9 to 14+ (TV releases skip some numbers). Background activity starts are limited from 10; restricted settings for sideloaded apps from 13 *(check whether ADB installs are exempt)*; TLS 1.3 from 10. | **Detect capabilities, don't assume a version.** Each step probes first and has a fallback. |
| Android TV vs Google TV | Google TV (Android 10+ devices with `launcherx`) brings its stock home back on the Home key on some devices; some firmware locks the screensaver to Ambient mode *(check)*. | **Three setup levels** (below): full launcher, guard only, remote only. The Home key is caught by the accessibility service (the existing `HomeKeyService` already does this). |
| Turning on ADB | Android 11+ has **Wireless debugging** with pairing codes; Android 9–10 needs "Network debugging" (only on some firmware) or one USB connection. | The phone supports both: ADB pairing (the TLS pairing-code flow) on 11+, and plain TCP 5555 on older TVs. Picture guides per brand for finding Developer options. |
| Bloat and ads | Different on every brand (PatchWall, TCL Home, Sony and Philips extras, Realtek/MediaTek services). | **Recipes per brand/model, plus a safe generic core** (below). Unknown TVs get the core only. |
| Hardware | 1–4 GB RAM, 32- or 64-bit, 720p–4K, different remotes and input sources. | The TV app stays plain Java/Views and light. Layout already scales; key mapping is in `config.xml` (`remote_keys`), extended per brand. |
| Power and inputs | HDMI-CEC, live TV and input apps differ. | Never disable input or tuner packages. Live TV and HDMI stay reachable in every profile that has them. |

## Setup levels (chosen automatically, can be changed)

| Level | What the TV gets | When |
|---|---|---|
| **Full** | tv-hero is Home, the guard runs, debloat, screensaver, Tools row on Home, everything in 2.x | Home can be replaced, or the Home key can be caught reliably |
| **Guard only** | The stock home stays. The guard still enforces profiles, limits, bedtime, quiz and lock screens; tv-hero's screensaver (as the TV screensaver where allowed) and the Tools app are there too. | Google TV devices that insist on their own home *(check per device)* |
| **Remote only** | Pairing, remote, power, volume; no parental rules | The person chooses it, or the guard can't be enabled (for example a firmware that blocks accessibility services) |

The phone app shows which level a TV is at, and what would lift it a level.

---

## A. Set up any Android TV from the phone alone

### Flow

```
Phone app ──ADB (pairing code on 11+, TCP 5555 on 9–10; home Wi-Fi only)──▶ TV
  1. connect   2. probe capabilities   3. pick level + recipe   4. baseline
  5. install tv-hero (+ chosen apps)   6. Home / guard / screensaver   7. debloat
  8. extras: screensaver + tools   9. pair (mutual TLS, existing)   10. family template   11. ADB off + check
```

### Pieces to build

1. **ADB client in the phone app.** It needs:
   - shell and sync/push
   - RSA auth
   - **TLS pairing for Wireless debugging on Android 11+** (the six-digit code the TV shows)

   Candidates: an existing library (`dadb` from mobile.dev is the one I know; its licence, upkeep and support for pairing codes must be checked) or our own implementation. The ADB key belongs to the phone app, separate from its TLS identity.
2. **Capability probe.** It reads, without changing anything:
   - Android version, brand, model, firmware and CPU ABI
   - whether it's Google TV
   - RAM and free storage
   - the current home, whether the home can be changed, and whether an accessibility service can be enabled
   - whether `pm grant … WRITE_SECURE_SETTINGS` works
   - notification listener access, Private DNS support, screensaver settings
   - TV inputs (`dumpsys tv_input`), and remote key codes if the person presses a few keys

   The result decides the level and which recipe steps run.
3. **Recipes.** These are versioned JSON lists of steps, signed into each phone app release and never fetched live. Each step has *check*, *apply*, *verify* and *undo*. There are three layers:
   - **Core** (every TV): install tv-hero, enable the guard and media listener, grant secure settings, set the screensaver, pair, apply the family template.
   - **Brand overlay** (Xiaomi PatchWall, TCL, Sony, Philips, Hisense Android models, Chromecast/Google TV Streamer, Nvidia Shield, generic Realtek and MediaTek boxes): ads and telemetry packages, Home-key handling, remote keys, input names.
   - **Model overlay** (tested models): the full debloat lists. The Xiaomi MiTV-AXSO0 lists from `debloat/` are the first.

   Unknown models get core plus the brand overlay's *safe* entries only. Every disable is checked against a **never-touch list**: system UI, setup wizard, Play services, input methods, TV input and tuner services, HDMI-CEC, audio HALs, OTA updaters unless the person chooses.
4. **Baseline and undo.** Before any change the phone saves the package states, home, accessibility, DNS and screensaver settings. They stay on the phone only, because they hold device identifiers. **Return TV to Stock** replays the undo steps.
5. **APK delivery.**
   - The TV app (about 600 KB, plain Java, one APK for arm/arm64/x86) ships inside the phone app, so versions always match.
   - Third-party apps are optional. They come from official sources with pinned SHA-256 hashes, or the person installs them from the Play Store on the TV. They are never bundled.
   - Brave on Android 9 stays capped at 1.80.124. On newer TVs, the current release.
6. **Family template.** A password-protected `GET /v1/backup` exports profiles, Apps for Everyone (as app *names and packages*), the dock, the start-up profile, Earn Time, Guest Time and profile switches. Apps that aren't on the new TV are skipped and listed. Paired phones, the password hash and the PIN are never included.
7. **Wizard UI** in the phone app, in the HIG style:
   - progress list (✓ done, ↺ undo)
   - pictures for each brand's Developer options
   - the setup level shown at every step
   - nothing loosens without saying so

### Steps

| # | Step | By | Notes |
|---|---|---|---|
| 0 | Turn on Developer options and (Wireless) debugging; on 11+ read the pairing code to the phone | person, guided | Brand-specific pictures. On 9–10 without network debugging: one USB-to-computer step, or this TV gets Remote only. |
| 1 | Connect: pair (11+) or TCP 5555 (9–10); press **Allow** on the TV's prompt | phone + person | Refuses public, guest or cellular networks. |
| 2 | Probe capabilities; choose the level and recipe; show the plan before changing anything | phone | The person can drop any optional step. |
| 3 | Save the baseline | phone | |
| 4 | Install tv-hero; grant `WRITE_SECURE_SETTINGS` and notification listener access | phone | |
| 5 | Make tv-hero Home (Full level), check it with a Home press, and put the old home back on failure | phone | Port of `tools/install-tv.sh`. On Google TV, check whether the Home key comes back to tv-hero. |
| 6 | Enable the guard; confirm it's bound (re-set the setting if not; reboot as the last resort) | phone | |
| 7 | **Extras: screensaver.** tv-hero's watch as the TV's screensaver (old one kept for undo), idle time, style, weather on/off and the location for weather (from the phone) | phone | Where firmware locks the screensaver *(check)*: Home idle clock and the button instead. |
| 7b | **Extras: tools.** Pick the built-in tools to show (Timer, Stopwatch, HIIT…), which profiles see them, and optional add-ons | phone | Tools are in the tv-hero app, so nothing extra is installed; add-ons follow the app approval rules. |
| 8 | Debloat in batches, with the input safety check after each | phone | Brand and model overlays only. |
| 9 | Optional apps (browser, Brave/YouTube tile, WhatsApp) | phone + person | First-run screens need the remote. |
| 10 | Pair the phone (code comparison + Allow on the TV) and set the password and PIN | person | Unchanged protocol 2. |
| 11 | Apply a template: **Same as my other TV** or **Start simple** | phone (mTLS) | |
| 12 | Optional: Tailscale, DNS filtering (Private DNS on 10+ where it works with a VPN *(check)*; otherwise Tailscale or router DNS) | person + phone | Sign-ins stay manual. |
| 13 | ADB off, port closed, Wireless debugging off; summary and undo | phone | Uses only the normal off switch (see LEARNINGS 2.3.0). |

### Security

- ADB is only used during setup, on a private network, and switched off and checked at the end. An unfinished setup is flagged on the next app start.
- Pairing codes and ADB keys are never logged; **Forget This TV** forgets the ADB key too.
- Recipes and pinned hashes ship inside the signed phone app. An installed APK must match its hash.
- Nothing is uninstalled: everything is disable/enable and can be undone.
- Baselines and logs stay on the phone.
- Each setup level states plainly what it can't protect against. For example, at Guard only, a child who reaches the stock home's settings could disable the guard. The guard already blocks Settings in kid profiles; the check is per device.

---

## B. Screensaver (watch, then weather), on every TV

**Done (2.4, home TV):**
- an analogue watch with the time, weekday and date, on a time-of-day gradient that drifts gently against burn-in
- it is the TV's own screensaver, starts after 5 idle minutes on Home, from the watch button on Home, or from the phone
- screen time doesn't count while it shows (2.4.1)

**Phase 3:**
1. **On any TV.**
   - At Full level and Guard only, the wizard sets tv-hero's watch as the TV's screensaver (`screensaver_components`), keeps the old choice for undo, and sets the idle time where the TV allows (WRITE_SETTINGS granted over ADB).
   - Where Google TV firmware keeps its own Ambient mode *(check)*, the watch still opens from Home's idle timer (Full level), from the phone, or from a **Clock** tile/app.
2. **Weather scenes:** clear (day sky, stars at night), clouds, rain, fog or haze, storm, heat. Each is a slow gradient with a few drawn shapes. No video and no blur, so it runs on 1 GB TVs, with a lighter mode chosen automatically on low-RAM devices.
3. **Weather source:** the launcher still reads no network itself. The weather add-on fetches the weather and the screensaver reads its last reading. The service must be chosen and its terms checked; Open-Meteo is a candidate. The location comes from the phone (a city or coarse coordinates), never from the TV.
4. **Faces and content:**
   - faces: analogue, digital, both, and a large "across the room" digital
   - optional temperature and the next hours, today's calendar, or a quote
   - Hindi or English date
5. **Settings** (phone: Settings → Screensaver; TV: Parent Menu → Screensaver): start now, idle time, face, weather on or off, dim at night, which profiles may change it.
6. **Rules kept:** it never counts as screen time, never shows over a lock screen, and any key closes it.

## C. Tools on the TV, on every TV

Tools are small full-screen apps inside tv-hero, made for the remote and readable across the room.
- **At Full level** they sit in a **Tools** row on Home.
- **At Guard only and Remote only** they live in a **tv-hero Tools** app on the TV's own home screen.
- **From the phone** a Tools tab starts, sets up and controls them (start, pause, next).

1. **Built-in tools (first set):**

   | Tool | What it does | Phone control |
   |---|---|---|
   | **Timer** | Big countdown, several named timers ("Pasta 10 min"), chime and a message at the end | set, start, pause, +1 min |
   | **Stopwatch** | Large digits with laps | start, lap, stop |
   | **HIIT / workout timer** | Work, rest, rounds and sets; presets (Tabata 20/10 × 8, EMOM, AMRAP, custom); 3-2-1 beeps; a colour per phase; the exercise name on screen; a total-time bar | build presets, start, pause, skip |
   | **Music under workouts** | Plays the person's own music: files from the phone (sent to the TV), a USB stick, or a music app already on the TV. Its volume is turned down under the beeps and back up after. | pick the playlist |
   | **Breathing** | Calm in/out animation for 1–5 minutes | start |
   | **Homework timer** | Focus blocks with breaks; can unlock Earn Time questions after | start |
   | **Chore and reward board** | Kids' tasks; done ones can give Earn Time minutes (parent approves) | add tasks, approve |

   Streaming-music apps are only used if they keep playing in the background on that TV. Nothing is downloaded from streaming services.
2. **Add-ons anyone can build** (the weather add-on already works this way):
   - **Contract:** an ordinary APK with an activity for the action `com.ajit.tvhero.TOOL`, plus metadata: name, icon, category, and which profiles see it by default.
   - **Discovery:** Home and the Tools app list installed tools, and the phone lists them too (`GET /v1/tools`, `POST /v1/tools/open`).
   - **Optional control channel:** a bound service or broadcast with documented commands (start, pause, next), so the phone can drive an add-on through tv-hero without the add-on opening its own network port.
   - **SDK pack:** a template project, the Timer as a sample, D-pad and HIG rules, and a checklist for 1 GB TVs.
   - **Safety:**
     - add-ons are apps, so they stay blocked until a parent approves them
     - profiles decide who sees each add-on
     - lock screens cover them, and their time counts like any app
     - they get no access to tv-hero's API, settings or family data
     - a "tools catalogue" (later) would list reviewed add-ons only
3. **Rules for every tool:** plain Java and Views, D-pad first, works offline, light on memory, tested on the device matrix (D). Screen time counts while a tool is open, like any app. A parent can mark a tool as "doesn't count" (Timer, Breathing).

## D. Device matrix and testing

Every device run checks all three parts: setup reaches its level and undoes cleanly; the screensaver starts (as the TV screensaver or the fallback) and closes on any key; the Timer and HIIT tools run with the remote and from the phone, with music ducking.

| Kind | Examples | How |
|---|---|---|
| Emulator | Android TV images for API 28, 30, 31, 33, 34; Google TV images *(check which API levels are available)* | **Automated:** run every recipe step and its undo in CI on emulators, including the ADB pairing flow where the emulator supports it. |
| Android TV 9 | Xiaomi MiTV-AXSO0 (home) | Real device, existing |
| Android TV 11+ | Mi Box S 2nd gen, Nvidia Shield, TCL and Philips Android TVs | Real devices, borrowed or bought, one per brand overlay |
| Google TV | Chromecast with Google TV, Google TV Streamer, Sony Bravia, TCL Google TV | Real devices: check Guard-only versus Full, the Home key and the screensaver |

- **Done means:** on each device in the matrix, setup reaches its level, every step undoes cleanly, the guard survives a reboot, and ADB is off at the end.
- **Telemetry:** none. Problems are reported only through a **Share setup report** button the person presses. The report holds the probe result and failed steps, with no identifiers.

## Milestones

| M | Part | What | Check |
|---|---|---|---|
| M1 | A | ADB client (TCP and pairing code) and capability probe, read-only | It reports the right capabilities on the Xiaomi and one Android 11+ device. |
| M2 | A | Install and update tv-hero from the phone on any TV (core recipe, no debloat) | It updates the home TV without a computer. |
| M3 | C | **Tools 1:** Timer, Stopwatch, HIIT with presets and beeps; Tools row on Home, Tools app, phone Tools tab | Used on the home TV. |
| M4 | C | Music under workouts (phone files, USB, ducking) | A HIIT session with music. |
| M5 | B | Screensaver settings (face, idle, dim) on phone and TV; fallback where the TV screensaver is locked | Home TV and one Google TV device. |
| M6 | A | Setup levels, Google TV Home-key handling, and the Extras step (screensaver and tools) in the wizard | Results recorded per device in the matrix. |
| M7 | A | Recipe engine with undo and the never-touch list; Xiaomi overlay; emulator CI | Green on all emulator API levels. |
| M8 | B | Weather scenes, weather from the add-on, location from the phone | A week on the home TV. |
| M9 | A | Backup, "Same as my other TV" template | Export the home TV, import onto a second TV. |
| M10 | C | Add-on contract, control channel and SDK pack; Breathing, Homework and Chores tools | A sample add-on built from the SDK, by someone other than us if possible. |
| M11 | A | Brand overlays for 2–3 more brands; brand picture guides | Real-device runs. |
| M12 | all | Docs: README "Set up any TV", Screensaver, Tools, SDK, SECURITY | |

## Open decisions (Ajit)

1. **Which devices to buy or borrow first** for the matrix. My suggestion: one Google TV device (Chromecast with Google TV or the Streamer) and one Android TV 11+ box.
2. **Distribution of the phone app:** sideloaded APK (as now), or a store listing later. Play Store policy for an app that sets up and debloats other devices over ADB must be checked before planning on it.
3. **Third-party apps in the wizard:** download from official sources with pinned hashes, or leave them to the Play Store on the TV.
4. **Community recipes:** accept brand or model overlays from others (reviewed, then shipped in releases), or keep them in-house.
5. **Languages:** Hindi and others for the wizard and TV screens, now or later.
6. **Which tools first** after Timer, Stopwatch and HIIT (suggested: Breathing, then Homework timer, then Chores).
7. **Weather service** for the screensaver (the terms must allow this use) and whether the screensaver may show the temperature at all.

## Risks

- **Google TV may not let a third-party launcher stay Home.** Guard only is the fallback, so parental control still works; the probe tells the person up front.
- **Older TVs without network debugging** need one USB step from a computer, or stay Remote only.
- **Firmware differences inside one model** (updates change package names). Recipes check before acting, and unknown packages are left alone.
- **Disabling the wrong package** can break input, HDMI or setup. The never-touch list, batches, a check after each batch and instant undo guard against it, and unknown models never get model-level lists.
- **Restricted settings on Android 13+** might block enabling the accessibility service for some install paths *(check: expected to be fine via ADB)*.
