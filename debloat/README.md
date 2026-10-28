# Debloat: Xiaomi MiTV-AXSO0 (PI.6036)

Everything is `pm disable-user --user 0` (disabled, never uninstalled), applied 28 Oct 2026 in batches with screenshots between them.

Undo any list: `TV_HOST=<tv ip> tools/restore.sh debloat/<list>.txt` (newest first). One app from the TV: Settings → Apps → See all apps → Show system apps → app → Enable.

| List | Switched off |
|---|---|
| `01-telemetry.txt` | Xiaomi analytics and statistics, Google feedback, bug-report sender |
| `02-ads-sponsored.txt` | PatchWall, web-content promos, floating banner, TV recommendations. The leanback recommendations service and both partner auto-installers were skipped by the safety check (input-driver test inconclusive on this firmware). |
| `03-xiaomi-background.txt` | TV Manager (its Boost wipes the home screen's settings), handbook, legal screens |
| `03b-update-service.txt` | Xiaomi update service, after "Your system is up to date" on PI.6036. Re-enable it to check for firmware again. |
| `04-unused-apps.txt` | SonyLIV, Airtel Xstream, Jio, Mi Music, Mi Gallery, Mi Video Player, USB media browser, Play Games, Play Movies, two launchers, Aerial Views, TV Bro, FX, Send Files to TV, a game |
| stock launcher | `com.google.android.tvlauncher`, replaced by tv-hero (`tools/install-tv.sh`) |

**Kept:** YouTube, YouTube Kids, Hotstar, Mi TV+, Live TV and HDMI inputs, Google Assistant, Chromecast / Mi Cast / Miracast, Play Store and Play Services, Gboard, Mi screensaver.

**Private DNS (TV only):** `settings put global private_dns_specifier family.adguard-dns.com` + `private_dns_mode hostname`. Blocks ads, trackers and adult sites, and forces YouTube Restricted Mode for everyone on the TV. It doesn't block YouTube's own ads. Undo: `private_dns_mode opportunistic`. Ads-only: `dns.adguard-dns.com`.

**Measured:** memory available after boot went from ~204 MB to ~351 MB. The new home screen uses about 26 MB, against 85 MB for the stock launcher.
