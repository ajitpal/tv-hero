# Changelog

Each release has a TV app and a phone app with the same version number. Notes for a release are the section under its heading.

## 2.7.0

- **Home tells you about what's focused.** Above an app tile: what this profile watched there lately (titles from the app itself), when it was last opened and the minutes today. Under a top-bar button: what it does.
- **Tools you can leave and come back to.** Back asks: keep it running, or stop and close. Stop closes. A running timer, stopwatch or workout stays in the corner over other apps (YouTube, Hotstar…), with its phase colour, so a Tabata started before a video stays in view.
- **Premium timers.** Progress rings, a beat on the last three seconds, colours that fade between workout phases; the watch screensaver's seconds hand sweeps smoothly.
- **Software updates from GitHub.** Settings → About → Software Update on the phone checks tv-hero's releases once a day (can be switched off), updates the phone app and installs the TV app over the home Wi-Fi. Every download is checked: HTTPS from GitHub only, size, SHA-256, package, version and tv-hero's release signature.
- **Releases from CI.** Pushing a version tag builds, tests, signs and publishes both apps with `update.json`, and updates the website.

## 2.6.0

- Tools on the TV: Timer, Stopwatch and Workout (HIIT presets, beeps that turn music down briefly). Phone TV tab → Tools controls them and builds your own workouts.

## 2.5.0

- Update the TV app from the phone (Settings → About → TV App), without a computer. ADB client and capability probe for setting up other TVs later.

## 2.4.1

- Screen time counts only while an app is open: not Home, the screensaver or Settings.

## 2.4.0

- Watch screensaver: analogue watch with the time and date on a time-of-day gradient; the TV's screensaver, after 5 idle minutes on Home, a watch button on Home, and from the phone.

## 2.3.0

- Time Today: give or take minutes, or end today's time now. One free profile switch a day, then Ask a Parent or the PIN. Questions can still earn time after End Now.

## 2.2.0

- Time's up, Bedtime and Paused lock the TV and stop playback. Earn Time: built-in questions (Maths, English, General knowledge, Hindi, Puzzles) earn minutes. Guest Time.

## 2.1.0

- Family start-up profile, Apps for Everyone, Parent Menu on the TV, Browser, WhatsApp and YouTube in Brave.

## 2.0.0

- Protocol 2: mutual TLS with pinned keys, pairing by comparing codes and Allow on the TV, internet control off by default, revocation.
