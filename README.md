# tv-hero

A clean, kid-safe Android TV for one TV: a Xiaomi Mi TV 4A Pro (MiTV-AXSO0, Android TV 9), controlled from your phone.

| Part | Folder | What it does |
|---|---|---|
| **TV app** | `tv/` | Apple TV-style home screen (clock, greeting, one row of apps, no ads), plus the **guard**: blocks apps, holds every newly installed app until a parent approves it, and serves the phone app on the home Wi-Fi. Package `com.ajit.tvhero`. |
| **Phone app** | `phone/` | Pair with the TV, block/unblock apps, approve new installs (parent password), edit the home screen, remote control, type on the TV, and **use the phone as a game controller**. Package `com.ajit.tvhero.remote`. |
| **Tools** | `tools/` | ADB over Wi-Fi from a computer: connect, screenshots, debloat/restore, install the TV app, a test client for the TV's API. |
| **Debloat lists** | `debloat/` | What was switched off on this TV (telemetry, ads, Xiaomi background services, unused apps), each list undoable. |
| **Docs** | `docs/` | Spec with the TV's measured hardware, plan, research, the phone↔TV protocol, troubleshooting. |

## How it works

```
 Phone (tv-hero app)                              TV (tv-hero app, 1 GB RAM, Android 9)
 ┌──────────────────────────┐  Wi-Fi, signed   ┌─────────────────────────────────────────┐
 │ Apps: block / approve    │ ───────────────▶ │ Guard (accessibility service)           │
 │ Home screen editor       │   HTTP :8765     │  • blocks apps, shows "blocked" screen  │
 │ Remote: Home, Back, open │   HMAC-SHA256    │  • holds new installs for approval      │
 │ Settings, activity log   │                  │  • parent password (PBKDF2 hash)        │
 │                          │  Bluetooth HID   │ Home screen (launcher)                  │
 │ Remote arrows, typing,   │ ───────────────▶ │  sees the phone as keyboard + remote +  │
 │ volume, GAME CONTROLLER  │                  │  game pad, like any Bluetooth device    │
 └──────────────────────────┘                  └─────────────────────────────────────────┘
```

- **Rules live on the TV.** They keep working when no phone is around.
- **New apps are blocked by default.** Anything installed (Play Store or otherwise) is held until a parent approves it in the phone app. Turning this off needs the parent password.
- **Parent password** is created by the first phone that pairs. It's needed to unblock or approve apps, turn off approvals, change the password or remove a phone. Other parents' phones pair with the code on the TV **and** the password.
- **Bluetooth part:** the phone registers as a Bluetooth HID device (keyboard + consumer control + game pad). Pair once from the TV (Settings → Remotes & Accessories → Add accessory). Then arrows, OK, volume, typing and the game controller work in every app and game, with nothing to install on the TV for it.

## Build

Both apps: JDK 17+, Android SDK 34. The release key is read from `keystore.properties` next to this README (never committed; see `.gitignore`).

```bash
(cd tv && ./gradlew assembleRelease testReleaseUnitTest)        # tv/app/build/outputs/apk/release/app-release.apk
(cd phone && ./gradlew assembleRelease testReleaseUnitTest)     # phone/app/build/outputs/apk/release/app-release.apk
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

**Phone:** install `phone/app/build/outputs/apk/release/app-release.apk` (allow installs from your file manager), open **tv-hero**, pick the TV, enter the code shown on the TV and create the parent password.

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

- The TV listens on port 8765 on the home network only. Every request except pairing is signed with a per-phone secret (HMAC-SHA256, timestamp, replay check). The secret is handed over once at pairing, encrypted with a key derived from the TV's 6-digit code and the parent password. On the phone it is stored encrypted by the Android Keystore.
- Someone on the same Wi-Fi who records the pairing could brute-force the 6-digit code offline, but still needs the parent password. Pair at home, not on shared networks.
- ADB (port 5555) has no encryption on Android 9. Switch USB debugging off when you're not working on the TV (`tools/adb-closed.py <tv ip>` checks it's closed).
- A child with the remote could still reach Settings → Accessibility and turn the guard off. Next step: block Settings in a Kids profile (see `docs/PLAN.md`, Phase 3).

## Licence

MIT, see `LICENSE`. The Outfit font in the TV app is under the SIL Open Font License (`tv/FONT-LICENSE-OFL.txt`).
