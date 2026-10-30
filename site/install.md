---
title: Install tv-hero
---

# Install tv-hero

You need an Android TV or Google TV (Android 9 or later) and an Android phone (Android 9 or later), on the same Wi-Fi.

## 1. The phone app

Download **tv-hero-phone** from the [latest release](https://github.com/ajitpal/tv-hero/releases/latest) and open it. The first time, Android asks you to allow installing apps from your browser or file manager.

## 2. Set up the TV from the phone

The phone app installs tv-hero on the TV itself. No computer and no cables.

1. **On the TV**, open Developer options: Settings → Device Preferences → About → select **Build** seven times. On Google TV it's Settings → System → About → **Android TV OS build**.
2. In **Developer options**, turn on **USB debugging** (and **Network debugging**, if your TV lists it).
3. **On the phone**, open tv-hero → **Set Up a New TV** → **Find TVs With Debugging On**, then pick the TV.
4. The TV asks **"Allow debugging?"**. Check that the code ends the same as the one on the phone, tick *Always allow*, and press **Allow**.
5. The phone installs tv-hero, makes it the Home screen (where the TV allows it), switches on its guard and media titles, then switches debugging off again.
6. Tap **Pair This Phone With the TV**: both screens show the same six-digit code. Press **Allow** on the TV, then create the parent password.

Newer TVs that offer only **Wireless debugging** with a pairing code aren't supported by the phone yet. For those, install once from a computer: `adb install tv-hero-tv-<version>.apk`, make tv-hero the Home app and enable *tv-hero guard* under Accessibility.

## 3. Add your family

In the phone app's **Family** tab, add a profile for each child. Choose their apps, daily limit, bedtime and **Earn Time** questions. Set a Parent PIN for the TV in Settings.

## Start over

To try the first-time setup again on the same TV, use phone app → Settings → About → TV App → **Start Over on This TV**. This removes tv-hero (and its profiles and pairings) and puts the TV's own Home screen back. TV debugging stays on, so **Set Up a New TV** can go again right away.

## Updates

The phone app checks for new releases once a day (Settings → About → Software Update). It updates itself after you confirm, and installs the TV app over your home Wi-Fi. Every download is checked: HTTPS from GitHub only, its size, its SHA-256, its package and version, and tv-hero's release signature.
