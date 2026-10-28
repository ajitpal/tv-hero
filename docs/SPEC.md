# tv-hero: specification

Name: **tv-hero** (home screen + Guardian on the TV, control app on the phone). Slug: `tv-app`, repo `tv-hero`. Status: Phase 1 (debloat, home screen) done 28 Oct 2026; guard and phone app v1 built.

## 1. The TV (measured over ADB, 28 Oct 2026)

Raw output: `baseline/2026-10-28/`. Photo of Settings → About: `docs/device-about.jpg`.

| Field | Value (measured) | Source |
|---|---|---|
| Model | **MiTV-AXSO0**, device `dangal` (sold as Xiaomi Mi TV 4A Pro / Mi TV 4C Pro 2019) | `ro.product.model`, `ro.product.device` |
| OS | **Android TV 9**, SDK 28, stock launcher `com.google.android.tvlauncher` + PatchWall | `getprop`, HOME resolver |
| Build | PI.6036 release-keys (`Xiaomi/dangal/dangal:9/PI/6036:user/release-keys`) | `ro.build.fingerprint` |
| Security patch | **2021-06-05** (unpatched since) | `ro.build.version.security_patch` |
| Kernel | 4.9.113 (15 Jul 2021) | About screen |
| Chip | **Amlogic `txlx`**, 4 × ARM Cortex-A53 (part 0xd03), max **1.416 GHz** | `/proc/cpuinfo`, `ro.board.platform`, cpufreq |
| CPU ABI | **32-bit only**: `armeabi-v7a, armeabi` | `ro.product.cpu.abilist` |
| GPU | **ARM Mali-450 MP, OpenGL ES 2.0** | SurfaceFlinger |
| RAM | **1 GB** (MemTotal 1,014,724 kB); only ~200 MB available while YouTube plays; 256 MB zram swap, 174 MB used | `/proc/meminfo`, `top` |
| Low-RAM device | **yes** (`ro.config.low_ram=true`); app heap limit 80 MB | `getprop` |
| Storage | `/data` 4.4 GB (2.8 GB used, **1.4 GB free**); system 1.7 GB (82 % full) | `df -h` |
| UI resolution | **1280 × 720 at 213 dpi** (the UI renders at 720p; panel resolution not exposed) | `wm size`, `wm density` |
| Network | Wi-Fi (no USB Ethernet); ADB on port 5555, `ro.adb.secure=1` (needs authorisation) | `dumpsys connectivity`, `getprop` |
| Animations | already 0.5× (window, transition, animator) | `settings list global` |
| Packages | 114 total, 14 user-installed, 0 disabled before this work | `pm list packages` |

**Heaviest processes at the time (PSS):** YouTube 159 MB · stock launcher 85 MB · Play services 80 MB · PatchWall 54 MB · system 50 MB · System UI 34 MB · Google Assistant 30 MB · Mi update service 27 MB · Chromecast 26 MB · TV recommendations 24 MB · Jio on-demand 13 MB · Mi analytics 9 MB. Load average ≈ 4.4 on 4 cores. Removing PatchWall, the stock launcher and recommendations alone frees roughly 160 MB of RAM in use.

**Differs from the public lists for this model:**
- Netflix and Prime Video are **not installed** on this TV.
- Two partner auto-installers: `android.autoinstalls.config.xioami.dangal` (system) **and** `android.autoinstalls.config.xiaomi.amelie` (installed in /data).
- Glance, Sensara, FanCode, Sun NXT, Eros, discovery+, Prasar Bharati and Al Jazeera are **not present**.
- Already user-installed: Projectivy Launcher, AT4K launcher, Aerial Views (screensaver), TV Bro browser, FX file explorer, Send Files to TV, a game (`com.vectorunit.purple.googleplay`), Hotstar, SonyLIV, Airtel Xstream, Jio on-demand, Google TV (`com.google.android.tv`).
- Xiaomi's own `com.mitv.tvlock` (TV lock) is present; check whether it already offers a PIN lock.

**What this means for design**
- 1 GB, low-RAM flag, Mali-450 / GLES 2.0, 720p UI: the home screen must be very light. Java/Views + RecyclerView (minSdk 26 ≤ 28 ✓), no blur, no video, no polling.
- 32-bit only: any native library must ship `armeabi-v7a` (Dynamic Island shipped `arm64-v8a` only, L53).
- Only 1.4 GB free storage: keep APKs small; nothing heavy like on-device AI on this TV.
- Android 9: no wireless-debugging pairing (that arrived in Android 11). ADB over the network is the old unencrypted port 5555, which needs extra care (see `PLAN.md` §4).
- Firmware is old and unpatched: keep the TV's attack surface small (ADB closed when not in use, few apps, DNS filtering).

## 2. Goals (in Ajit's words, organised)

1. **Clean, Apple TV-like home screen**: only essential apps, no ads, no sponsored rows, no clutter.
2. **Remove bloatware and anything that tracks usage** from the TV (Xiaomi and partner telemetry, ad and recommendation services, auto-installers).
3. **Better performance** on the existing hardware.
4. **Full control from the phone** when it is on the same Wi-Fi:
   - remote control and typing;
   - share content to the TV (links, YouTube videos, photos);
   - install apps; pause, disable or block apps;
5. **Kids protection**:
   - lock apps or the whole TV after a time limit or at bedtime;
   - stop Shorts and harmful content;
   - see what they watched and for how long, and decide what they can and can't open.
6. A clean, useful interface on both the TV and the phone.

## 3. Requirements

### 3.1 TV: Home screen
- R1. Replaces PatchWall and the stock launcher as the default home. Survives reboots, including the firmware re-enabling `com.google.android.leanbacklauncher` at boot.
- R2. Layout inspired by tvOS: one hero area at the top (the focused app, or what's playing / continue watching), one row of essential apps, a small top bar (clock, inputs, Wi-Fi, settings). Nothing else by default.
- R3. Two modes: **Parent** (all allowed apps) and **Kids** (only the apps allowed for kids). Switching to Parent needs a PIN or approval from the phone.
- R4. Fully usable with the D-pad remote: clear focus state, focus never lost, big type readable from the sofa.
- R5. No internet permission in the launcher process itself, no ads, no analytics.
- R6. Opens in under a second on this TV (measure in Phase 0 and after).

### 3.2 TV: Guardian (enforcement, runs in the background)
- R7. Knows which app is in front (accessibility service and usage stats), all the time, at low cost.
- R8. Enforces per-app daily limits, a whole-TV daily limit, allowed hours and bedtime. Warns at 5 minutes and 1 minute, then shows a full-screen "Time's up" lock.
- R9. Blocks chosen apps outright (Kids mode, or always): Settings, Play Store, browsers, the official YouTube app, etc. Blocked = a lock screen appears instead.
- R10. Logs activity locally: app, start, end, and **what was playing** where the app shares it (media session title/artist). Keeps 30 days (configurable).
- R11. Tamper resistance: can't be uninstalled or turned off from the TV without the parent PIN; tells the phone if its protection was switched off or the TV was offline during allowed hours.
- R12. Works offline. Rules live on the TV; the phone only edits them.

### 3.3 Phone: Control app
- R13. Finds the TV on the same Wi-Fi and pairs once with a code shown on the TV. Only paired phones can control it.
- R14. Remote: D-pad, back, home, volume, play/pause, keyboard input.
- R15. Share: send a link, a YouTube video or a photo from the phone's share sheet to the TV.
- R16. Apps: list installed apps; install an APK; enable, disable, hide or block an app; clear an app's data.
- R17. Kids: edit limits, hours and allowed apps; **Lock now**, **Unlock for 30 min**, **Bedtime now**; see remaining time.
- R18. Activity: today and last 7 days per app, a timeline with titles where available, alerts (protection off, limit reached, blocked app attempts).
- R19. Debloat and privacy: show the current state of the bloat list and re-apply it after firmware updates (some apps come back).

### 3.4 Debloat, privacy, performance
- R20. Every change is reversible and recorded (backup of package states first; restore script).
- R21. Disable rather than uninstall (`pm disable-user`), in small batches, checking the TV between batches.
- R22. Never touch: Play Store and Play Services, Gboard, HDMI/Live TV inputs, remote/Bluetooth, system UI. Netflix, Prime Video and YouTube are system apps on this TV; they stay installed but can be hidden or blocked.
- R23. DNS-level blocking of ads, trackers and adult content for the TV (and optionally the whole home network).
- R24. Measure before and after: free RAM, boot-to-home time, home open time, number of running processes.

### 3.5 Honest limits (things the plan can't promise)
- Apps that protect their screen (Netflix, Prime Video, Live TV/HDMI) give **black screenshots**, so "what they see" comes from app names and media titles, not pictures.
- Not every app reports what's playing. Coverage per app must be checked on the TV.
- Shorts can be **removed by app choice** (YouTube Kids, or a client that hides Shorts) much more reliably than by detecting Shorts inside the official YouTube app.
- A factory reset by someone at the TV would remove everything. The Guardian can only report that the TV went missing.
- The TV is on a 2021 security patch. Debloating reduces risk but can't patch the firmware.

## 4. Decisions (Ajit, 28 Oct 2026)
1. **Name:** tv-hero.
2. **Essential apps:** Ajit picks from the installed list (checklist asked during Phase 1).
3. **Profiles:** up to **10** profiles; bedtime defined by Ajit per profile.
4. **YouTube:** both YouTube Kids and a Shorts-free YouTube client (SmartTube only from a verified safe release).
5. **Voice assistant and casting:** **keep** (Google Assistant, speech, Chromecast, Mi Cast, SmartShare, Miracast stay enabled).
6. **DNS filtering:** on the **TV only** (Private DNS), not the router.
7. **Phones:** Ajit's phone **and another parent's phone** can pair and control the TV.
8. **Method:** work on the real TV over ADB, back up first, disable in small batches and verify each with screenshots.

Requirement updates from these answers: R3 becomes "Parent + up to 10 kid profiles, each with its own allowed apps, limits and bedtime"; R13 pairs several parent phones (each revocable); R23 is Private DNS on the TV.
