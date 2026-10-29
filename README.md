# tv-hero

A clean, kid-safe Android TV for one TV: a Xiaomi Mi TV 4A Pro (MiTV-AXSO0, Android TV 9), controlled from your phone.

| Part | Folder | What it does |
|---|---|---|
| **TV app** | `tv-launcher/` | Apple TV-style home screen (clock, greeting, one row of apps, no ads), plus the **guard**: blocks apps, holds every new install until a parent approves it, runs kids' profiles (up to 10), bedtime and time limits, and serves the phone app over an encrypted channel. Package `com.ajit.tvhero`. |
| **Phone app** | `companion/` | Pair with the TV (compare a code, press Allow on the TV), power and volume, block/approve apps, kids' profiles and requests, home screen, who can control the TV, internet control, remote, typing, and **the phone as a game controller**. Fingerprint app lock. Package `com.ajit.tvhero.remote`. |
| **Protocol** | `protocol/` | Plain Java shared by both apps: ports, paths, TLS settings, pairing maths, certificate pinning, with tests. |
| **Tools** | `tools/` | ADB over Wi-Fi from a computer: connect, screenshots, debloat/restore, install the TV app, a test client for the TV's API. |
| **Debloat lists** | `debloat/` | What was switched off on this TV (telemetry, ads, Xiaomi background services, unused apps), each list undoable. |
| **Docs** | `docs/` | `ARCHITECTURE`, `SECURITY`, `PROTOCOL` (v2), `DESIGN`, `SPEC` (measured hardware), `PLAN`, `RESEARCH`, `TROUBLESHOOTING`. |

## How it works

```
 Phone (companion)                                 TV (tv-launcher, 1 GB RAM, Android 9)
 ┌───────────────────────────┐  mutual TLS :8766  ┌──────────────────────────────────────────┐
 │ TV, Family, Apps, Remote, │ ─────────────────▶ │ Guard (accessibility service)            │
 │ Settings                  │  both keys in the  │  • apps, approvals, profiles, bedtime     │
 │ Keystore key, pinned TV   │  Android Keystore, │  • prompts: Allow / Don't Allow           │
 │ certificate, app lock     │  pinned by SHA-256 │  • Keystore key, pinned phone certs       │
 │                           │  (home Wi-Fi, or   │ Pairing :8765 (home network only)         │
 │                           │  Tailscale if on)  │ Home screen (launcher)                    │
 │ Remote arrows, typing,    │  Bluetooth HID     │  sees the phone as keyboard + remote +    │
 │ GAME CONTROLLER           │ ─────────────────▶ │  game pad, like any Bluetooth device      │
 └───────────────────────────┘                    └──────────────────────────────────────────┘
```

- **Rules live on the TV.** They keep working when no phone is around.
- **New apps are blocked by default.** Anything installed (Play Store or otherwise) is held until a parent approves it in the phone app. Turning this off needs the parent password.
- **Pairing needs someone at the TV.** The phone and the TV show the same 6-digit code; a person checks it and presses **Allow** on the TV (focus starts on Don't Allow). Then the parent password.
- **Parent password** is created by the first phone that pairs (at least 6 characters). It's needed for anything that loosens a rule: allowing or approving apps, switching to Parent, adding time, removing another phone, turning internet control on.
- **Internet control is off by default.** Turning it on needs the password, a phone at home, and Allow on the TV. Off works at once from anywhere. Details: `docs/SECURITY.md`.
- **Bluetooth part:** the phone registers as a Bluetooth HID device (keyboard + consumer control + game pad). Pair once from the TV (Settings → Remotes & Accessories → Add accessory). Then arrows, OK, volume, typing and the game controller work in every app and game, with nothing to install on the TV for it.

## Build

JDK 17+, Android SDK 34, one Gradle build. The release key is read from `keystore.properties` next to this README (never committed; see `.gitignore`).

```bash
./gradlew :protocol:test                                                    # shared protocol + mutual-TLS tests
./gradlew :tv-launcher:assembleRelease :tv-launcher:testReleaseUnitTest    # tv-launcher/build/outputs/apk/release/
./gradlew :companion:assembleRelease :companion:testReleaseUnitTest        # companion/build/outputs/apk/release/
```

`keystore.properties`:
```
storeFile=C:/Projects/app/TV-app/keys/tv-hero-release.jks
storePassword=…
keyAlias=tvhero
keyPassword=…
```

## Install

**TV** (computer on the same Wi-Fi, USB debugging on in the TV's Developer options):
```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
TV_HOST=192.168.1.50 tools/install-tv.sh
```
The script starts tv-hero first, then makes it Home, switches the stock launcher off only once that worked (and puts it back if not), and turns on the guard. After an update Android 9 may need one reboot to start the guard again; the script does it.

**Phone:** install `companion/build/outputs/apk/release/companion-release.apk` (allow installs from your file manager), open **tv-hero**, pick the TV, check that the TV shows the same code as the phone, press **Allow** on the TV with the remote, then enter (or create) the parent password.

**From a computer:** `python3 tools/tvhero_client.py <tv-ip> pair`, then `status`, `volume 30`, `devices`, `forget`.

## Everyday use (on the TV)

- **The TV starts in the Family profile**: YouTube, YouTube · Brave, JioHotstar, Xiaomi TV+, WhatsApp, Browser, Live TV, Miracast. Settings and the Play Store are for Parent.
- **Profile button** (round letter, top right): *Who's watching?* with every profile, Guest, Parent (PIN, or the parent password while no PIN is set) and the **Parent Menu**.
- **Parent Menu** (no phone needed):
  - profiles (add, rename, apps, daily limit, bedtime, remove)
  - **Apps for Everyone** (on in every profile; out of the box: Browser, WhatsApp, YouTube · Brave)
  - which profile the TV starts in
  - Parent PIN
  - who can control the TV
  - internet control, debugging, Guest mode
- **YouTube · Brave** opens m.youtube.com in Brave (ad and tracker blocking) and reuses one tab. Brave 1.80.124 is the last release for Android 9 (Chromium 138), so it no longer gets security updates: use it for YouTube, and the **Browser** (TV Bro, made for the remote) for everything else.
- **WhatsApp** is the phone app. On first start, link it to your phone (⋮ → *Link as companion device*, scan the code with WhatsApp on your phone). Type with the phone's Bluetooth keyboard (Remote tab).
- A blocked Settings or Play Store screen has **Switch to Parent**.

## Everyday use (phone app)

| Tab | |
|---|---|
| **Apps** | Waiting for approval (Approve / Block), every app with an Allowed switch, "Hold new apps for approval". |
| **Home screen** | Order of the home row, apps per row, greeting, apps hidden from Home. Saved straight to the TV. |
| **Remote** | D-pad, OK, Back, Home, Menu, play/pause, volume, typing (Bluetooth); Home/Back and "Open on TV" also over Wi-Fi. |
| **Controller** | Bluetooth set-up, then a game pad: D-pad, A B X Y, L1 R1, Select / Home / Start, two thumbsticks. |
| **Settings** | Change password, phones paired with the TV (remove), recent activity (blocked attempts, installs), forget the TV. |

## Debloat and privacy (already applied on this TV)

28 packages disabled (not uninstalled): Xiaomi and Google telemetry, PatchWall and sponsored rows, partner auto-installers, TV Manager, update service (re-enable to check for firmware), unused apps; stock launcher replaced. Private DNS `family.adguard-dns.com` on the TV only (ads, trackers, adult sites; YouTube Restricted Mode). Details and undo commands: `debloat/README.md`.

## Security notes

Full detail in `docs/SECURITY.md`. In short:
- Commands travel only over **mutual TLS** (TLS 1.2 on the TV, forward-secret AES-GCM). Each device's key lives in its Android Keystore, and certificates are pinned by fingerprint.
- **Who can control this TV** is listed on the phone and in the TV's Parent Menu, with last use and whether it was at home or over the internet. Removing a phone takes effect at the next connection.
- ADB (port 5555) has no encryption on Android 9. Keep it off unless you're working on the TV; switch it off from the phone, the TV's Parent Menu, or check with `tools/adb-closed.py <tv ip>`.
- Known limits: pairing relies on someone comparing six digits before pressing Allow; a child with the remote in the Parent profile could reach Accessibility settings.

## Licence

MIT, see `LICENSE`. The Outfit font in the TV app is under the SIL Open Font License (`tv-launcher/FONT-LICENSE-OFL.txt`).
