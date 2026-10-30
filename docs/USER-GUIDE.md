# tv-hero user guide

Everything tv-hero does, screen by screen. For installing, see [the website](https://ajitpal.github.io/tv-hero/install.html).

## Everyday use (on the TV)

- **The TV starts in the Family profile**: YouTube, YouTube · Brave, JioHotstar, Xiaomi TV+, WhatsApp, Browser, Live TV, Miracast. Settings and the Play Store are for Parent.
- **Info line on Home:** above a focused app tile, what this profile watched there lately (titles from the app), when, and the minutes today; under a top-bar button, what it does.
- **Clock screensaver**: an analogue watch with the time and date on a gradient that follows the time of day. It's the TV's screensaver, starts after 5 idle minutes on Home, from the **watch button** next to the profile button, or from the phone (TV tab → Show the Clock Screensaver). Any key closes it.
- **Tools** (stopwatch button on Home, or the "tv-hero Tools" app): **Timer** (big countdown, beeps at the end even after Back), **Stopwatch** with laps, **Workout** (HIIT presets: Tabata, 30/30, 45/15, EMOM, circuits; colours per phase, 3-2-1 beeps that turn any music down briefly). Play/Pause on the remote pauses them; a lock screen pauses them too. **Back asks:** keep it running (it stays as a small status in the corner over YouTube and other apps) or stop and close; Stop closes. The phone's TV tab → **Tools** starts and controls them and builds your own workouts.
- **Profile button** (round letter, top right): *Who's watching?* with every profile, Guest, Parent (PIN, or the parent password while no PIN is set) and the **Parent Menu**.
- **Parent Menu** (no phone needed):
  - profiles (add, rename, apps, daily limit, bedtime, Earn Time, Time Today: add, take away, end now, remove)
  - **Apps for Everyone** (on in every profile; out of the box: Browser, WhatsApp, YouTube · Brave)
  - which profile the TV starts in
  - Parent PIN
  - who can control the TV
  - internet control, debugging, Guest mode, **Guest Time** (a daily allowance shared by all guests)
- **What counts as screen time:** only time with an app open (YouTube, Hotstar, Live TV, the browser…), counted by the second. Home, the clock screensaver and Settings don't count; the volume bar, the keyboard or a notification over a video don't stop it. Family → a profile → *Today, App by App* shows where the minutes went.
- **Time-left corner:** "⏱ 12 min left" in the top-left corner over any app, for a profile with a daily limit. Per profile: *Last 5 Minutes* (default), *Always* or *Off* (Family → a profile → Time-Left Corner, or Parent Menu → Profiles → a profile).
- **On-Screen Notes:** Settings → On-Screen Notes (or Parent Menu → On-Screen Notes) switches off, for everyone, the time-left corner, the tools' corner status, the Home info line or the 5- and 1-minute warnings (only for profiles whose corner is set to Always, so nobody goes without a warning). Limits, bedtime and lock screens still apply.
- **Time's up, Bedtime and Paused lock the TV.** Whatever was playing is paused, the app is sent away and nothing else opens behind the screen. All a child can do there is switch profile (the PIN is needed to move to a profile with no daily limit), **Ask for Time** (it shows on the parents' phones under Needs You) or enter the Parent PIN.
- **Profile switches:** a kid or Guest can change profile on the TV once a day (Family → Profile Switches on the phone, or Profile Switches in the Parent Menu). After that, and always for a profile with no daily limit, the TV offers **Ask a Parent** (Needs You on the phone) or the Parent PIN.
- **Earn Time** (Time's up only): questions in Maths, English words, General knowledge, Hindi and Puzzles, at Easy, Medium or Hard. Each right answer adds 5 minutes, up to 15 minutes a day (both set per profile). A wrong answer costs nothing, but three wrong in a row means a 10-minute break, so guessing doesn't pay. The questions are built into the TV app: no internet, no tracking; the TV keeps only today's count.
- **YouTube · Brave** opens m.youtube.com in Brave (ad and tracker blocking) and reuses one tab. Brave 1.80.124 is the last release for Android 9 (Chromium 138), so it no longer gets security updates: use it for YouTube, and the **Browser** (TV Bro, made for the remote) for everything else.
- **WhatsApp** is the phone app. On first start, link it to your phone (⋮ → *Link as companion device*, scan the code with WhatsApp on your phone). Type with the phone's Bluetooth keyboard (Remote tab).
- A blocked Settings or Play Store screen has **Switch to Parent**.

## Everyday use (phone app)

Screenshots of every screen: `docs/screenshots/phone/` (regenerate with `./gradlew :companion:testDebugUnitTest -Pscreenshots=record`).
- **Family** tab: profiles (Family and kids), **Guest** (daily limit, extra time), **Apps for Everyone**, **Start-up Profile**, each profile's apps, limit, bedtime and **Earn Time** (on/off, minutes per right answer, most per day, level, topics, earned today).
- **TV** tab, **Needs You**: kids' requests for an app, more time today, a later bedtime, or another profile switch.
- **Time Today** (TV tab, each profile page, Guest page): −15 / +15 / +30 / +1 hr for today, and **End Today's Time Now**, which shows Time's up on the TV at once, even for a profile without a daily limit. Giving time needs the password; taking it away or ending it doesn't. Earn Time is still offered after End Now, up to the daily max; **Let … Earn Again Today** on the profile page clears today's count (password).

| Tab | |
|---|---|
| **Apps** | Waiting for approval (Approve / Block), every app with an Allowed switch, "Hold new apps for approval". |
| **Home screen** | Order of the home row, apps per row, greeting, apps hidden from Home. Saved straight to the TV. |
| **Remote** | D-pad, OK, Back, Home, Menu, play/pause, volume, typing (Bluetooth); Home/Back and "Open on TV" also over Wi-Fi. |
| **Controller** | Bluetooth set-up, then a game pad: D-pad, A B X Y, L1 R1, Select / Home / Start, two thumbsticks. |
| **Settings** | About (phone and TV app versions; **TV App**: check the TV, update tv-hero on it from the phone; **Software Update**: new releases from GitHub for both apps), change password, phones paired with the TV (remove), recent activity (blocked attempts, installs), forget the TV. |

## Debloat and privacy (the reference TV)

28 packages disabled (not uninstalled): Xiaomi and Google telemetry, PatchWall and sponsored rows, partner auto-installers, TV Manager, update service (re-enable to check for firmware), unused apps; stock launcher replaced. Private DNS `family.adguard-dns.com` on the TV only (ads, trackers, adult sites; YouTube Restricted Mode). Details and undo commands: `debloat/README.md`.

## Control from anywhere (Tailscale)

Put the TV and the phone in the same tailnet (Tailscale account).
- On the TV, Tailscale is the **always-on VPN** (`settings secure always_on_vpn_app=com.tailscale.ipn`, lockdown off) and is exempt from battery optimisation. After a reboot it reconnects by itself (verified: up 2 minutes after boot).
- The TV app reports its Tailscale address (`GET /v1/remote`). The phone app stores it and uses it when the home address doesn't answer.
- **Internet control stays off until you turn it on:** phone app → Settings → Internet Control → Allow Control From Anywhere, then the parent password, then **Allow** on the TV.
- In the Tailscale admin console, **disable key expiry** for the TV, or it drops off the tailnet when its key expires (Tailscale's default is 180 days, as far as I know).
- On the phone, set Tailscale as always-on VPN too (Android Settings → Network → VPN → Tailscale ⚙ → Always-on VPN; leave "Block connections without VPN" off).
- When the TV is in deep standby its Wi-Fi may be off, so nothing can reach it until it wakes.
