# TV app: implementation plan (for review)

Goes with `SPEC.md` (what) and `RESEARCH.md` (why). Phases are ordered so each one leaves the TV better and fully reversible. Bug fixes always jump the queue.

## 1. Architecture

```
 Phone (same Wi-Fi)                         TV: MiTV-AXSO0, Android 9, ~1 GB, 32-bit
 ┌────────────────────────┐   paired, TLS   ┌──────────────────────────────────────────┐
 │ TV Control (phone app) │ ◀────────────▶ │ TV Home app (one APK, small)              │
 │  Remote · Share · Apps │   local only    │  • Home screen (launcher, Parent/Kids)    │
 │  Kids rules · Activity │                 │  • Guardian: accessibility service,       │
 │  Debloat state         │                 │    usage stats, media-session listener,   │
 │                        │   ADB (5555),   │    device admin, rules engine, lock screen│
 │  Admin channel ────────┼──only when ────▶│  • Control server (LAN only, paired)      │
 └────────────────────────┘   switched on   │  • Activity log (SQLite, 30 days)         │
                                            └──────────────────────────────────────────┘
                     DNS filtering (TV Private DNS or home router): ads, trackers, adult sites
```

**Why one TV app instead of three:** with ~1 GB RAM every process counts. Launcher, Guardian and the control server share one process and one small database. The launcher UI never touches the network; only the control server's socket does (Android needs the INTERNET permission even for LAN sockets, which will be disclosed; L52).

**Two ways the phone talks to the TV**
1. **Control channel** (everyday): the TV app's own server, found with Android NSD (`_tvhome._tcp`). Pairing once: the TV shows a 6-digit code, the phone enters it, both sides exchange keys and pin each other's certificates (trust in both directions, L47). Carries: remote keys, text input, share, rules, lock/unlock, activity.
2. **Admin channel** (rare, powerful): ADB over Wi-Fi from the phone using an ADB client library (e.g. `dadb`, **verify** licence and Android 9 support). Used for: install APK, disable/enable apps, debloat, granting permissions. Because Android 9 ADB on port 5555 is unencrypted, it stays **off** by default: the Guardian holds `WRITE_SECURE_SETTINGS` (granted once over ADB) and switches ADB on only when a paired phone asks, then off again after the job or 10 minutes (**verify** that turning `adb_enabled` off closes port 5555 on this firmware).

**Enforcement on the TV, not the phone:** rules are stored and enforced on the TV, so limits still work when the phone is away (R12).

## 2. Tech choices
- Kotlin, `minSdk 28`, `targetSdk` per the current Play rules for sideloaded TV apps (**verify**), JDK 17, Gradle multi-module: `tv/`, `phone/`, `shared/` (protocol, rules model; pure Kotlin with JVM tests, L43).
- TV UI: plain Views + RecyclerView with D-pad focus handling, no Leanback rows, no Compose on the TV (memory). Phone UI: Compose, following the HIG-style kit from Dynamic Island (L74).
- No native libraries planned. If one appears, ship `armeabi-v7a` (the TV is 32-bit).
- Own release keystore from day one, backed up (L04–L06). Separate signing for TV and phone apps is not needed; one key, two packages.
- Builds checked with lint and unit tests in the cloud box; everything else tested on the real TV over ADB (L28).

## 3. Phases

### Phase 0: Baseline and safety net (one session, no app code)
- Turn on Developer options and USB debugging on the TV; connect from the PC over Wi-Fi (`adb connect <tv-ip>:5555`).
- Record real specs: `getprop`, `/proc/cpuinfo`, `/proc/meminfo`, `df -h`, `wm size`, `pm list packages -s/-3/-d`. Update `SPEC.md`.
- Back up package states and key settings to `TV-app/baseline/` (restore must be one command).
- Measure: free RAM after boot, boot-to-home seconds, home open time, running processes (`dumpsys activity processes`, `dumpsys meminfo`).
- Check on this firmware: restricted profile exists? Private DNS settable? `cmd package set-home-activity` works? accessibility and notification-listener settings reachable or ADB-only? YouTube Kids in Play Store?
- **Exit:** baseline file + list of what works, reviewed with Ajit.

### Phase 1: Debloat, privacy, performance (scripts, reversible)
- `tools/tv.ps1` (Windows) + `tools/tv.sh`: connect, backup, apply list, restore, check (re-apply after firmware updates). Debloat lists verified package by package on this TV.
- Apply in batches: telemetry → sponsored/ads → Xiaomi background → preloaded apps → personal choices agreed with Ajit. Check the TV after each batch.
- Privacy settings: Google usage & diagnostics off, ad personalisation off, Xiaomi user-experience/analytics options off (**verify** names on this firmware).
- DNS filtering (TV Private DNS or router).
- Performance: animation scales to 0.5, background-run restrictions (`cmd appops set <pkg> RUN_IN_BACKGROUND ignore`) for apps that must stay installed but shouldn't run in the background, TV Manager kept disabled.
- **Interim launcher** until Phase 2: FLauncher or Projectivy, so the TV is clean straight away.
- **Exit:** before/after numbers; ADB closed again (`adb-closed` check); restore tested once.

### Phase 2: TV Home launcher v1
- Home: top bar (clock, inputs, Wi-Fi, settings), hero area, one row of essential apps; Parent/Kids picker on wake; long-press to reorder/hide.
- Set as default home and survive the leanback launcher coming back at boot.
- Diagnostics screen from v1 (L17) and a demo mode for every state (L22).
- **Exit:** opens fast on the TV, works only with the remote, Ajit happy with the look (screenshots reviewed).

### Phase 3: Guardian v1 (on the TV, no phone yet)
- Accessibility service watching only window-state changes (foreground package), usage stats as a cross-check, media-session listener for titles (L20).
- Rules engine (pure Kotlin, unit-tested): per-app and whole-TV daily limits, allowed hours, bedtime, block lists, Kids-mode allow list.
- Warnings at 5 and 1 minutes; full-screen lock; parent PIN to override.
- Block Settings, Play Store and Developer options in Kids mode, so protection can't be switched off from the TV; device admin to prevent uninstall.
- Activity log, 30 days, on the TV.
- **Exit:** a test day with real usage; limits fire on time; the log matches what was watched.

### Phase 4: Phone Control app v1
- NSD discovery, pairing, remote (D-pad, keyboard), Lock now / Unlock 30 min / Bedtime now, rules editor, today's activity.
- Alerts: protection off, limit reached, blocked-app attempts, TV unreachable during allowed hours.
- **Exit:** everything in Phase 3 manageable from the phone without touching the TV.

### Phase 5: Admin from the phone
- Admin channel over ADB, switched on and off by the Guardian.
- Apps screen: install APK (from phone storage or a link), enable/disable, hide, block, clear data; debloat state with "re-apply".
- **Exit:** install and disable work end to end; port 5555 closed afterwards.

### Phase 6: Sharing and content safety
- Share sheet target on the phone: links, YouTube videos, photos to the TV.
- YouTube strategy agreed with Ajit: YouTube Kids for kids, optionally a Shorts-free client (only a verified safe SmartTube release, given the Dec 2025 breach).
- 7-day activity report, simple charts.

## 4. Security and privacy model
- Pairing with a code shown on the TV; pinned keys both ways; every request authenticated; LAN-only listener.
- ADB off except during admin jobs; never left open.
- Activity data stays on the TV and the paired phone. No cloud, no analytics, no ads.
- Disclose every permission and why (README permissions table, L33).
- Kids' data: only app names, times and titles. Nothing else is collected.

## 5. Learnings applied (from `../LEARNINGS.md`)
L01–L02 (sideload hardening, disclosure), L04–L07 (signing, versionCode), L11 (sizes from system metrics, TV overscan), L17 (Diagnostics from v1), L20 (media sessions for "what's playing"), L21–L22 (one state store, demo states), L27–L28 (lint for API 28, real-device testing), L41/L47 (two-way trust → phone/TV pairing), L43 (rules in pure Kotlin), L52 (merged-manifest permissions), L53 (32-bit ABI here), L67 (gradlew from Windows), L81 (every wait has an exit: pairing and ADB timeouts).

## 6. Decisions needed before Phase 0
See `SPEC.md` §4. Phase 0 and Phase 1 can start as soon as the essential-apps list and the voice/casting choice are agreed.
