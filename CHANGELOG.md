# Changelog

Each release has a TV app and a phone app with the same version number. Notes for a release are the section under its heading.

## 2.9.0

Fair time.

- **Screen time is exact.** The volume bar, the keyboard, a notification or tv-hero's own corner notes used to stop the count while the video played on behind them. Now only a full-screen app changes what's in front, time is counted by the second, and every app, profile, screen or lock-screen change closes the running total, so no seconds go to the wrong app or profile. Where the phone sets the TV up (Set Up a New TV, or updating the TV app from the phone), the TV also uses Android's own app-usage record to check what's in front.
- **Today, app by app.** Each profile's page on the phone (and Guest's) shows where today's minutes went.
- **Time-left corner.** A small "⏱ 12 min left" in the top-left corner over any app, for profiles with a daily limit: Last 5 Minutes (default), Always or Off per profile, on the phone or in the TV's Parent Menu.
- **On-Screen Notes.** One place (phone → Settings → On-Screen Notes, or Parent Menu → On-Screen Notes) to switch off the time-left corner, the tools' corner status, the Home info line, or the 5- and 1-minute warnings. Limits, bedtime and lock screens always apply.

## 2.8.1

- **Tools fit the screen.** On TVs about 540 dp tall (most of them, including 720p sets), Timer, Stopwatch and Workout pushed their buttons below the edge of the screen, so Pause and Stop couldn't be reached. The dial and digits now shrink to fit, and every button shows.
- **PIN titles no longer cut off.** "Parent PIN: 30 more minutes" and similar titles stay on one line.
- **No preset family name.** A new TV greets with just "Good evening" until you name your family under Settings → Home Screen.
- **A gallery of every screen** on the website and in the README, with filters for kids, parents, time and bedtime, safety, tools and setup, and a full-size viewer.

## 2.8.0

- **Open source.** tv-hero's code, releases and website now live together at github.com/ajitpal/tv-hero, under the MIT licence.
- **Set Up a New TV from the phone.** Switch on debugging at the TV, and the phone finds it, installs tv-hero, makes it Home where the TV allows, switches on its guard and media titles, switches debugging off and goes straight to pairing. No computer needed.
- **Start Over.** Settings → About → TV App → Start Over on This TV removes tv-hero and puts the TV's own Home back, so the first-time setup can be tried again.
- **A new look.** A logo (a shield for safety, play for fun, a spark for time earned), new app icons and TV banner, brand guidelines, and a redesigned website.
- **A friendlier welcome** in the phone app: what tv-hero is for, and Set Up a New TV next to pairing.
- **Updates from the project's own releases**, with the old releases repo as a fallback.

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

## 2.3.2

- Versions shown on the phone (Settings → About) and in the TV's Parent Menu.

## 2.3.1

- Earn Time questions are offered even after a parent ends the day's time; Let … Earn Again Today on the phone.

## 2.3.0

- Time Today: give or take minutes, or end today's time now. One free profile switch a day, then Ask a Parent or the PIN. Questions can still earn time after End Now.

## 2.2.1

- Lock screens are fully opaque and always close the app behind them; the OK press that opens a screen can't also answer it.

## 2.2.0

- Time's up, Bedtime and Paused lock the TV and stop playback. Earn Time: built-in questions (Maths, English, General knowledge, Hindi, Puzzles) earn minutes. Guest Time.

## 2.1.3

- Parent password and PIN can be reset only from a computer over the TV's debugging connection; the start-up profile is applied once per boot.

## 2.1.1

- Family start-up profile, Apps for Everyone, Parent Menu on the TV, Browser, WhatsApp and YouTube in Brave. Phone app 2.1: fixes for rows that acted on their own, Profiles, Apps for Everyone and Start-up Profile.

## 2.0.0

- Protocol 2: mutual TLS with pinned keys, pairing by comparing codes and Allow on the TV, internet control off by default, revocation.
