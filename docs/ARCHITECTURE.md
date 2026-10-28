# Architecture

One repo, one Gradle build, three modules:

```
tv-hero/
├── protocol/      plain Java library: ports, paths, TLS settings, pairing maths, pinned trust (tests + vectors)
├── tv-launcher/   TV app (Java, Views, minSdk 26): home screen, guard, control server      com.ajit.tvhero
├── companion/     phone app (Kotlin, Compose, minSdk 28): control, family, remote, HID     com.ajit.tvhero.remote
├── tools/         Python: ADB over Wi-Fi, install, debloat/restore, protocol-2 test client
├── debloat/       what is disabled on the TV, and how to undo it
└── docs/          SPEC, PLAN, DESIGN, PROTOCOL, SECURITY, ARCHITECTURE, TROUBLESHOOTING
```

`protocol/` is plain Java on purpose. The TV app has no Kotlin, which keeps it about 600 KB with no Kotlin runtime on a 1 GB, 32-bit TV, yet both apps compile against the same definitions and the same tests.

## Runtime picture

```
 Phone: companion                                     TV: tv-launcher (Android 9, 1 GB)
┌──────────────────────────────┐                     ┌─────────────────────────────────────────────┐
│ Compose UI (HIG style)       │                     │ MainActivity: home screen (Views)            │
│ AppState ── TvClient ────────┼─ mTLS :8766 ───────▶│ GuardService (accessibility)                 │
│   Identity (Keystore EC key) │   (home Wi-Fi, or   │   ├ ControlServer :8765 plain (pairing only)  │
│   PairingSession             │    Tailscale when   │   ├ ControlServer :8766 mutual TLS ─┐         │
│ AppLock (BiometricPrompt)    │    internet control │   ├ Api: routes, rules, gating ◀───┘         │
│ Discovery (NSD)  ◀───────────┼── mDNS ─────────────│   ├ Overlays: prompts, locks, Parent Menu     │
│ Hid (Bluetooth HID device) ──┼── Bluetooth ───────▶│   ├ GuardStore / Profiles (SharedPreferences) │
└──────────────────────────────┘   keyboard, remote, │   └ TlsIdentity (Keystore EC key)             │
                                   game pad          │ MediaListener (titles from media sessions)   │
                                                     └─────────────────────────────────────────────┘
```

### TV (`tv-launcher`)

- **Home screen** (`MainActivity`, `Config`): clock, greeting, a dock of up to 6 apps, an app library. No feeds, no ads. A kid profile shows only that kid's apps.
- **GuardService** is an accessibility service and the only long-running piece. It:
  - reacts to window changes to block apps, hold new installs and enforce profiles, bedtime and time limits
  - runs a 20-second tick for usage, the sleep timer and the night volume cap
  - hosts both control ports and advertises the TV over mDNS
  - draws every prompt as an accessibility overlay. On this TV, starting an Activity from a service is delayed after Home; overlays aren't.
- **Api** is the whole control surface (see `PROTOCOL.md`). It decides home vs internet (`protocol.Net`), checks that internet control is on, records who made each change, and asks the TV for confirmation where needed (pairing, internet on).
- **State:**
  - `GuardStore`: apps, devices with pinned fingerprints, internet control, event log, kids' requests, password hash
  - `Profiles`: kids, PIN, usage, sessions, sleep and quiet settings
  - both are SharedPreferences: small, synchronous, no database on a 1 GB TV

### Phone (`companion`)

- **AppState** holds everything the screens show. It polls `status` and `now` every 5 s while the app is visible and stops in the background.
- **TvClient** has one method per endpoint. It uses plain HTTP for pairing and pinned mutual TLS for the rest, tries the home address first and then the TV's Tailscale address, and remembers which one worked.
- **Identity** is the phone's Keystore key and certificate. **PairingSession** checks the commitment and computes the code; it is pure and unit-tested.
- **Store** keeps paired TVs (address, device id, pinned TV fingerprint). It holds no secrets, since the key stays in the Keystore.
- **UI** is in `ui/`, with HIG components in `ui/hig/`. The tabs are TV, Family, Apps, Remote and Settings. The lock screen and the "removed" screen sit in front of everything.
- **Hid** makes the phone a Bluetooth keyboard, remote and game pad. It works in any app or game on the TV, with nothing on the TV side.

## Data flow examples

- **Allowing an app for a kid.** The kid presses *Ask a Parent* on the TV's blocked screen, which adds a request to `GuardStore`. The parent's phone polls and shows it under **Needs You**. *Allow* asks for the password, which goes to `POST /v1/requests/answer` over TLS. The app is added to the kid's list and the guard re-checks.
- **Turning internet control on.** The phone sends `POST /v1/internet {enabled, password}` from the home Wi-Fi and gets 202 back. The TV shows Allow / Don't Allow, and the phone polls `GET /v1/internet` until `pending` is false.
- **Revoking a lost phone.** Any other phone sends `POST /v1/devices/remove {device_id, password}`. The fingerprint leaves `GuardStore`, and the next TLS handshake from that phone fails.

## Build and test

```bash
./gradlew :protocol:test                                   # pairing vectors, real mutual-TLS handshakes on the JVM
./gradlew :tv-launcher:assembleRelease :tv-launcher:testReleaseUnitTest
./gradlew :companion:assembleRelease :companion:testReleaseUnitTest :companion:lintRelease
python3 tools/tvhero_client.py --self-test                 # Python against the same vectors
./gradlew :companion:testDebugUnitTest -Pscreenshots=record   # phone screenshots (downloads Robolectric's Android)
```

On a real TV: `TV_HOST=<ip> tools/install-tv.sh <apk>`, then `tools/tvhero_client.py <ip> pair` and `status` / `volume` / `devices`.
