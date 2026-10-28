# Research: clean TV UI, debloating, kids control (28 Oct 2026)

Facts below come from the linked sources and haven't been tested on this TV. Anything marked **verify** needs checking on the device in Phase 0. Things change quickly in this area, so re-check before relying on a source.

## 1. Options for a clean TV experience

| Option | Clean UI | Ads / tracking | Kids control | Phone control | Fit for this TV |
|---|---|---|---|---|---|
| **Apple TV 4K** (new box on HDMI) | Cleanest off the shelf. tvOS 26 adds the Liquid Glass look, profiles on wake, content-first playback controls ([Apple](https://www.apple.com/newsroom/2025/06/apple-tv-brings-a-beautiful-redesign-and-enhanced-home-entertainment-experience)) | No ad rows on the home screen | Built-in Screen Time / restrictions (not researched in detail here) | iPhone remote; no Android control app | Buy new hardware; works with any TV. Can't be customised by us |
| **Google TV** (stock on newer TVs/streamers) | Busy home screen with promotions ([Notebookcheck](https://www.notebookcheck.net/The-Google-TV-launcher-is-mess-of-adverts-but-this-replacement-is-a-clean-beautiful-ad-free-alternative.1018849.0.html), [Android Authority](https://www.androidauthority.com/projectivy-launcher-android-tv-3684205/)) | Ads and recommendations | Kids profiles (not researched here) | Google TV remote in the Google app | New hardware |
| **Stock PatchWall** (what's on the TV now) | Cluttered | Xiaomi analytics, Mi TV+, Glance lock-screen ads, Sensara, partner auto-installs | Android TV restricted profile only | Mi remote apps | Current state |
| **Projectivy Launcher** | Very customisable, only chosen apps on home; free, Premium unlocks extras ([Android Authority](https://www.androidauthority.com/projectivy-launcher-android-tv-3684205/), [Make Tech Easier](https://maketecheasier.com/use-projectivy-launcher-android-tv)) | No recommendation rows (the article doesn't say if the launcher itself is ad-free) | None | None | Quick stopgap; closed source |
| **FLauncher** | Simple, open source | None known | None | None | Quick stopgap |
| **Our own TV Home + Guardian + phone app** | Designed to Ajit's taste | None | Full: limits, bedtime, block lists, activity | Full, on the same Wi-Fi | Only option that combines all goals |

**Conclusion:** Apple TV is the cleanest product you can buy, but it doesn't give phone control and custom kid rules on this TV without new hardware. tv-hero builds its own: a light Apple TV-style home, a guard for kids' rules and a phone app, with design cues from tvOS (content first, one row, overlays that don't hide playback).

## 2. Debloat for MiTV-AXSO0 (firmware PI.6036)

Package lists were researched from public debloat lists for this model and then checked one by one on the TV (`dumpsys package`), since the TV differed from them (see SPEC §1). Everything is **disabled, not uninstalled**.

| Group | Packages | Notes |
|---|---|---|
| Telemetry | `com.miui.tv.analytics`, `com.xiaomi.statistic`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender` | Low risk |
| Ads / sponsored rows | `com.mitv.tvhome.atv` (PatchWall, *caution*), `com.xm.webcontent`, `com.xiaomi.floatingframe`, `com.google.android.tvrecommendations`, `com.google.android.leanbacklauncher.recommendations`, `android.autoinstalls.config.xioami.dangal` (partner auto-installs; spelling as published), `com.glance.tv`, `com.mitv.tvhome.mitvplus`, `co.sensara.tv.mitv` | Disable PatchWall only after the new home is working |
| Xiaomi background | `com.xiaomi.mitv.tvmanager`, `com.xiaomi.mitv.handbook`, `com.xiaomi.mitv.updateservice` (*caution*: re-enable to check firmware updates), `com.xiaomo.tv.milegal` | TV Manager's "Boost" deletes launcher theme files: keep it disabled |
| Voice (choice) | `com.google.android.katniss`, `com.google.android.speech.pumpkin` | Disabling stops the remote's mic button |
| Casting (choice) | `com.google.android.apps.mediashell`, `com.mitv.milinkservice`, `com.xiaomi.mitv.smartshare`, `com.xiaomi.wfdsinkhelperservice` | Disabling stops Chromecast / Mi Cast / Miracast |
| Preloaded apps | `com.xiaomi.mimusic2`, `com.google.android.play.games`, `com.google.android.videos`, `com.mitv.gallery` | Media Player still opens USB photos |
| Streaming (choice) | `com.fancode.tv`, `com.suntv.sunnxt.mitv`, `com.eros.mitv`, `com.discoveryplus.mitv`, `com.ddnational.prasarbharati`, `net.aljazeera.english` | Keep any that are used |

Device notes from those pages: `com.google.android.leanbacklauncher` is re-enabled by firmware at every boot; `svc power reboot` is killed on this firmware (use adb's reboot); USB debugging also opens ADB on port 5555 on this model; screenshots of Live TV, HDMI and Netflix come out blank; firmware updates can re-enable disabled apps; some TVs cut USB power while debugging is on.

## 3. Kids control on Android TV 9

- **Built in:** Android TV's *restricted profile* (Settings → Security & restrictions → Create restricted profile) limits which apps open behind a PIN. Inside it there is no Play Store and no third-party apps without Google sign-in; a forgotten PIN needs a factory reset ([Google](https://support.google.com/androidtv/answer/6123330?hl=en)). **Verify** it exists on this Xiaomi firmware. Useful as a stopgap, too blunt as the final answer (no timers, no reports).
- **Store apps:** TV parental-control apps exist (e.g. YourHour for Android TV, TVGuard, tvusage), found via [APKMirror](https://www.apkmirror.com/apk/mindefy-labs/yourhour-screen-time-control-android-tv/yourhour-screen-time-control-android-tv-1-2-8-yhtv-release/), [APKCombo](https://apkcombo.com/tvguard-parental-control-tv/com.revaltronics.tvguard.agent/) and [AlternativeTo](https://alternativeto.net/software/tvusage/about). Not evaluated; they are closed source and would add their own tracking, which goes against goal 2.
- **Device owner** (full kiosk control through `dpm set-device-owner`) normally fails once accounts or users exist on the device ([example](https://www.codegenes.net/blog/not-allowed-to-set-the-device-owner-because-there-are-already-several-users-on-the-device/)). The TV has a Google account, so this would mean removing it. Not planned.
- **Approach chosen:** our own Guardian using an accessibility service (foreground app), usage stats, a media-session listener (what's playing, as in Dynamic Island L20) and device admin (uninstall protection, lock). Permissions that the TV's settings may not expose are granted once over ADB.

## 4. YouTube and Shorts

- **SmartTube** (open-source YouTube client for TVs): no ads, SponsorBlock, Android 4.3+ ([overview](https://www.mintlify.com/yuliskov/SmartTube/features/overview)); a **"hide shorts everywhere"** setting is reported by users ([TechEnclave](https://techenclave.com/t/how-to-remove-shorts-completely-from-youtube-app-on-firestick/419032)).
- **Security warning:** in late Nov–Dec 2025 SmartTube's signing keys were stolen and malicious builds (roughly 30.43–30.47) were published; builds from 30.55 use a new key and a new app ID on F-Droid was planned ([BleepingComputer](https://bleepingcomputer.com/news/security/smarttube-youtube-app-for-android-tv-breached-to-push-malicious-update), [AFTVnews](https://www.aftvnews.com/?p=45790)). **Verify** the current safe release and source before installing; don't sign in with the main Google account until then.
- **YouTube Kids** on the TV is the simpler, safer route for younger children (**verify** availability on this TV's Play Store).
- Blocking Shorts *inside* the official YouTube app by reading the screen is possible in theory but fragile; treat as an experiment, not the plan.

## 5. Network-level filtering

- Filtering DNS (ads, trackers, adult sites) protects every app at once. Android 9 introduced Private DNS; on TVs the setting is often hidden but can be set over ADB (`settings put global private_dns_mode hostname`, `private_dns_specifier <host>`): **verify** on this TV.
- Alternatively set filtering DNS on the home router for every device.
- Providers to compare: family-filter DNS services (e.g. AdGuard DNS Family, Cloudflare for Families, NextDNS with a custom list). Not researched in depth; check privacy policies before choosing.

## 6. Design cues from Apple TV
- Content first; overlays don't hide what's playing ([Apple newsroom](https://www.apple.com/newsroom/2025/06/apple-tv-brings-a-beautiful-redesign-and-enhanced-home-entertainment-experience)).
- One hero area ("Top Shelf") above a single row of apps; focused item grows with a soft shadow.
- Profiles shown on wake → our Kids / Parent picker on wake.
- Calm, dark background; few colours; large type; nothing auto-playing with sound.
