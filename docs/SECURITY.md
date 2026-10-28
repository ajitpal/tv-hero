# Security

tv-hero lets a phone control a family TV and its parental rules, so the threats are real but domestic: a child who wants more screen time, a guest or neighbour on the Wi-Fi, a lost phone, and the internet once remote control is on. Security is part of the product. Every way in shows who is using it, and every way in can be revoked.

## What is protected, and how

| Asset | Protection |
|---|---|
| Control of the TV | Only phones whose certificate the TV pinned. Pinning needs a person to compare a code and press **Allow on the TV**, then the parent password. |
| Commands in transit | Mutual TLS (TLS 1.2 on the TV, ECDHE + AES-GCM, forward secrecy), certificates pinned by SHA-256 on both sides. |
| Device keys | EC P-256 keys generated inside each device's Android Keystore. They can't be exported, even by tv-hero. |
| Parent password | Stored on the TV only as PBKDF2-HMAC-SHA1 (20 000 rounds, random salt). It travels only inside the TLS channel. 5 wrong tries lock checks for 5 minutes. Never logged. |
| Loosening a rule | Allowing or approving an app, resuming a paused TV, switching to Parent, adding time, removing another phone, turning internet control on: each needs the password. Tightening (block, pause, sleep, internet off) never does. |
| Remote (internet) control | **Off by default.** Turning it on needs the password, a phone on the home network, **and Allow on the TV**. It can't be turned on from afar or by accident. Off works at once, from anywhere. |
| The app on the phone | App lock: fingerprint, or the phone's screen lock as a fallback, when the app opens and after 30 s in the background. |

## Pairing

Pairing compares codes, as Bluetooth's numeric comparison does (details in `PROTOCOL.md`). The TV commits to its random nonce **before** it sees the phone's, so someone in the middle who swaps certificates can only make the two screens show the same code by luck: 1 in 1 000 000 per attempt. Each attempt needs a person at the TV to press Allow while the codes differ.

The TV prompt opens with focus on **Don't Allow**, so a stray OK press refuses. It names the phone and says "If the codes differ, or you didn't start this, choose Don't Allow."

Pairing works only from the home network, one at a time, and must finish within 3 minutes.

## Visibility and revocation

| Question | Where to answer it |
|---|---|
| Who can control this TV right now? | Phone: Settings → **Who Can Control This TV** (name, last used, at home or over the internet, "Active now"). TV: **Parent Menu** (Parent PIN) → Who Can Control This TV. |
| Who did what? | The TV's event log records the phone (or "TV") behind each change: pairing allowed or refused, apps allowed, internet on/off, phones removed, kids' requests. |
| Is someone controlling it from outside? | The TV shows "*phone* is controlling this TV over the internet" whenever that starts (at most every 10 minutes per phone). |
| Lost phone | Remove it from any other paired phone (password) or from the TV's Parent Menu. It is refused from the next connection: the TV checks the fingerprint on every handshake and keeps no TLS sessions. |
| This phone | Settings → Unpair This Phone: no password needed, takes effect on the TV at once. |
| Everything off | Turn internet control off (any phone, no password), then remove phones. On the TV, Parent Menu → Debugging (ADB) off. |

## The TV itself

- **ADB.** Android 9 TVs speak ADB over plain TCP (5555). It's used only for setup and debloat and should stay off; `tools/adb-closed.py` checks. The phone app and the TV's Parent Menu both show it and can switch it off; switching it on needs the password.
- **Debloat** only disables packages (`pm disable-user`), never uninstalls system apps. Every list has a restore script (`debloat/`, `tools/restore.sh`).
- **Guard.** An accessibility service. It reads only which app is in front (window-state events), never screen content, and it draws the lock/prompt overlays.
- **DNS filtering** (AdGuard Family) is set on the TV only.

## Known limits (be honest about them)

- **No PAKE.** Pairing's protection relies on a person comparing six digits and pressing Allow. A careless Allow on mismatched codes can let an attacker who is already on the Wi-Fi in. The wording on the TV pushes towards Don't Allow.
- **The pairing port is plain HTTP** (8765). It carries public certificates, nonces and the pairing state, and nothing that controls the TV. Anyone on the home Wi-Fi can make a pairing prompt appear on the TV, one at a time.
- **TLS 1.2 on the TV** (Android 9 has no TLS 1.3). Only forward-secret AEAD suites are enabled. Bundling Conscrypt would add TLS 1.3 at the cost of a larger app; it was declined on purpose.
- **A child with physical access to TV Settings** could switch the guard off in Accessibility. Kid profiles block Settings, but the Parent profile doesn't.
- **Internet control relies on Tailscale** (WireGuard) to reach the TV. Our mutual TLS runs inside it, so Tailscale's coordination servers can't read or forge commands. Tailscale's account security still decides who can reach the TV's port at all.
- **Phone app lock** uses the phone's own biometrics or screen lock. On Android 9-10 that may include weaker (class 2) biometrics.

## Reporting

This is a personal project. Fix issues in the repo and record them in `LEARNINGS.md`.
