---
title: Install tv-hero
---

# Install tv-hero

You need an Android TV or Google TV (Android 9 or later) and an Android phone (Android 9 or later) on the same Wi-Fi.

## 1. The phone app

Download **tv-hero-phone** from the [latest release](https://github.com/ajitpal/tv-hero-releases/releases/latest) and open it. Android asks to allow installing apps from your browser or file manager the first time.

## 2. The TV app

**From the phone (no computer):** on the TV, turn on Developer options and network debugging. Then, in the phone app, open Settings → About → TV App and tap **Update the TV App**. The TV asks "Allow debugging?". Check that the code matches the one on the phone, then press Allow. The phone installs tv-hero, starts its guard and switches debugging off again.

**From a computer:** `adb install tv-hero-tv-<version>.apk`, then make tv-hero the Home app and enable *tv-hero guard* under Accessibility.

## 3. Pair

Open the phone app, pick the TV, compare the six-digit code on both screens, press **Allow** on the TV, and create the parent password.

## Updates

The phone app checks for new releases once a day (Settings → About → Software Update). It updates itself after you confirm, and installs the TV app over your home Wi-Fi. Every download is checked: HTTPS from GitHub only, its size, its SHA-256, its package and version, and tv-hero's release signature.
