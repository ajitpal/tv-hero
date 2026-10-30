---
title: Security
---

# Security

tv-hero lets a phone control a family TV and its rules. The threats it guards against are domestic but real: a child who wants more screen time, a guest or neighbour on the Wi-Fi, a lost phone, and the internet once remote control is on.

- **Encrypted, pinned connection.** Phone and TV each keep an EC P-256 key in the Android Keystore that can't be copied off the device. Commands use mutual TLS, and each side trusts only the certificate it pinned when pairing.
- **Pairing a person can check.** The TV commits to its random value before seeing the phone's. Both screens then show the same six-digit code, and someone must press **Allow on the TV**. The prompt opens on *Don't Allow*.
- **Loosening needs the password.** Allowing apps, adding time, resuming a paused TV, switching to Parent, removing another phone and turning internet control on all need the parent password. Tightening never does.
- **Internet control is off by default.** Turning it on needs the password, the home network and Allow on the TV.
- **Visible and revocable.** Every paired phone is listed with when it was last used and how it connects. Removing one takes effect at its next connection.
- **Updates are verified.** The phone downloads only over HTTPS from GitHub's release hosts. It checks size and SHA-256, and the APK must be the right package and version and be signed with tv-hero's release key. The TV app is installed over a debugging connection that is used only on the home Wi-Fi, switched on with the password and always switched off at the end.
- **Honest limits.** Android 9 TVs speak TLS 1.2, not 1.3. Pairing relies on a person comparing six digits. A child with physical access to TV Settings could switch the guard off; kid profiles block Settings.
