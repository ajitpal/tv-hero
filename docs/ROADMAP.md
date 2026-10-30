---
title: Roadmap
---

# Roadmap

What's coming to tv-hero, in the order we plan to build it. Each step ships as its own release, with screenshots, tests and a changelog, and is checked on a real TV before the next one starts.

**Have an idea or need something?** [Request a feature](https://github.com/ajitpal/tv-hero/issues/new?template=feature.yml) or add a 👍 to an existing request: the most-wanted ideas move up. Questions and show-and-tell go in [Discussions](https://github.com/ajitpal/tv-hero/discussions).

| Release | Theme | Status |
|---|---|---|
| **2.9** | Fair time: accurate screen time, a time-left corner, overlay settings | **Next** |
| **2.10** | Phone to TV: links, photos, text and search | Planned |
| **2.11** | Live and free TV: weather, free channels | Planned |
| **2.12** | Family extras: Scenes, rewards, weekly summary | Planned |
| **Labs** | Experimental: install apps from the phone | Planned, behind a switch |
| Later | More TVs, more languages, more phones | Ideas |

Rules every item keeps: nothing loosens a rule without the parent password; nothing new leaves the home without being visible and switchable off; it must stay light on a 1 GB TV.

---

## 2.9: Fair time (next)

### Accurate screen time (bug fix, top priority)
Screen time sometimes counts less than was watched, and occasionally attributes time to the wrong moment. Causes found in the code:

- The TV decides which app is in front from window changes. **Any small window replaces it:** the volume bar, the keyboard, a notification, or tv-hero's own corner timer and notes. While that small window "is in front", counting stops, although the video plays on behind it.
- Time is added every 20 seconds to whatever is in front at that moment, so switching apps or profiles mid-interval can put up to 20 seconds in the wrong place.

The fix:

- Only full-screen apps change "what's in front"; overlays, dialogs, the keyboard and tv-hero's own windows never do.
- Where the TV allows it (granted during setup from the phone), use Android's own app-usage record as the source of truth, with the window method as the fallback.
- Count by the second, and close the running total on every app, profile or screen change.
- Tests that replay real sequences of events (volume bar over YouTube, keyboard in a search box, corner timer, screensaver, standby) and check the minutes.
- **Today, app by app** on the phone, so a parent can see where the time went.

### Time-left corner
A small pill in the corner over any app: "⏱ 12 min left". Per profile, set from the phone or the Parent Menu:

- **Last 5 minutes** (default), **Always**, or **Off**.
- A gentle note at 5 and 1 minutes stays, as today.

### Overlay settings
One place (phone → Settings → On-Screen Notes, and the Parent Menu) to switch each on-screen extra on or off: the time-left corner, the tools' corner timer, the Home info line, and the warnings.

### Requests from families
A feature-request form on GitHub and this roadmap on the website, so families can ask and vote.

---

## 2.10: Phone to TV

- **Open a link on the TV.** Share any link from the phone ("Share → tv-hero"), and it opens on the TV in the chosen browser, or in YouTube for YouTube links. In a child's profile, only into apps that child has; a parent can allow it once with the password. Every link sent is in the TV's event log.
- **Photos on the TV, never saved there.** Pick photos on the phone, and they show on the TV as a slideshow or as the screensaver. They are resized on the phone, sent over the encrypted connection and **kept in the TV's memory only**: gone at standby, at "Stop showing" or after a few hours, and never written to the TV's storage.
- **Type and paste from the phone.** Send text into the search box on the TV (partly there today in the remote).
- **Search from the phone, with a default per profile.** Type a search on the phone and choose where it runs: YouTube, or the browser with the search engine you set per profile (for example SafeSearch on for kids, DuckDuckGo for parents).
  - *Honest limit:* tv-hero can't change another app's own settings (Brave's default search engine, for example). It can open Brave with a search address it chooses, and a family filter DNS (like AdGuard Family, in the user guide) can force SafeSearch in every browser.
- **Casting stays with the TV.** Chromecast built-in and Miracast already work on most Android TVs; tv-hero points to them rather than rebuilding them.

---

## 2.11: Live and free TV

- **Weather.** On Home and as a weather screensaver: now, today and tomorrow for a city you set on the phone (no location tracking). From Open-Meteo: free for non-commercial use, no account, credited on screen.
- **Free TV.** An optional "Free TV" row with:
  - official free apps and channels a parent picks, for example the public broadcaster's own app (in India, Prasar Bharati's WAVES), news and kids' channels on YouTube;
  - **playlists a parent adds** (an M3U address), opened in a player installed on the TV.
  - tv-hero ships **no channel lists of its own** and never adds paid channels from unofficial sources. In a child's profile, each channel needs a parent's OK.
- **Live tiles.** Weather, the date, and the day's festival on Home, light enough for a 1 GB TV.

---

## 2.12: Family extras

- **Scenes.** One tap on the phone: *Homework* (only learning apps, a timer on screen), *Movie night* (no limit until 10 pm, volume capped), *Bedtime now*.
- **Rewards.** A parent gives minutes for a chore or homework with a reason ("+15: tidied the room"), which the child sees on the TV. Streaks and badges for Earn Time.
- **Weekly summary** on the phone: minutes per child and per app, questions answered, requests. Computed on the TV, never sent anywhere else.

---

## Labs (experimental, off by default)

- **Install an app from the phone.** Pick an APK on the phone. tv-hero shows its name, package, signer, permissions and size, asks for the parent password, installs it over the home Wi-Fi and switches TV debugging off again. The new app starts as **waiting for approval**, like any other new app. Labs is off until a parent switches it on.

---

## Later

- More TVs: a device matrix (Google TV, Sony, TCL, Mi Box) with emulator tests, and fixes per brand.
- Several TVs in one phone app.
- More languages on the TV and the phone; more Earn Time subjects and languages.
- An iPhone app.

## Not planned, and why

- **Using the phone's memory (RAM) for the TV.** Memory can't be shared over Wi-Fi. What helps instead: screen mirroring makes the phone do the heavy work and the TV only shows video, and tv-hero keeps the TV light (switching off unused apps, a small Home screen).
- **Unofficial or paid channels for free.** tv-hero won't bundle or point to them.
- **Changing other apps' settings** (Brave's search engine, YouTube's settings). Android doesn't allow it; tv-hero works around it with its own search and a family DNS.
- **Analytics or accounts.** tv-hero stays private by design.
