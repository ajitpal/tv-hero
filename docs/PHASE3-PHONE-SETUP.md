# Phase 3: set up a TV from the phone alone (plan)

Status: plan, 29 Oct 2026. Nothing here is built yet.

## Goal

Someone with only the tv-hero phone app and a stock Xiaomi Mi TV 4A Pro (MiTV-AXSO0, Android TV 9) can bring the TV to the state this TV is in today. No computer, no scripts.

Today that takes a computer, ADB, `tools/install-tv.sh`, the debloat lists and a manual walk through the Parent Menu. Phase 3 moves all of it into a **Set Up a TV** wizard in the phone app.

"The state this TV is in today" means:
- tv-hero is Home, the guard is on and bound, and media control works
- the debloat lists are applied (disabled, never uninstalled)
- Browser (TV Bro), YouTube · Brave and WhatsApp are installed
- the phone is paired with mutual TLS, and a parent password and PIN are set
- the Family start-up profile, Apps for Everyone, limits, Earn Time, Guest Time and profile switches are configured
- debugging is off at the end

## How it works

The phone becomes the computer: it speaks ADB over the home Wi-Fi to the TV, which is the same thing `tools/` does today.

```
Phone app ──ADB over TCP :5555 (home Wi-Fi, setup only)──▶ TV
   │  1. check   2. disable bloat   3. install tv-hero + apps   4. Home + guard
   │  5. pair (existing mTLS flow)  6. apply family template   7. ADB off
   └─ keeps a baseline of the TV on the phone, so every step can be undone
```

### Pieces to build

1. **ADB client in the phone app.**
   - Option A: an existing Kotlin/Java ADB library. `dadb` (mobile.dev) is the candidate I know of; licence, upkeep and Android 9 behaviour need checking before we rely on it.
   - Option B: our own small client: the CNXN/AUTH/OPEN/WRTE/CLSE messages, shell and sync push, RSA auth. A few hundred lines, testable against this TV.
   - The ADB key is generated on the phone. Keeping it in the Android Keystore (signing ADB's 20-byte token with RSA PKCS#1, no digest) is the aim; it must be tested, otherwise use app-private storage.
2. **Setup recipe.** A versioned JSON list of steps. Each step has *check* (already done?), *apply*, *verify* and *undo*, so the wizard can be re-run safely and stopped at any point. The recipe for MiTV-AXSO0 comes from what was really done to this TV: `debloat/*.txt`, `install-tv.sh`, and the settings in `debloat/README.md`.
3. **Baseline on the phone.** Before changing anything: the package list with enabled state, the home activity, the accessibility and DNS settings. It stays on the phone only, because it holds device identifiers, never in the repo or a cloud service. **Return TV to Stock** replays the undo steps from it.
4. **Family template.** A new password-protected `GET /v1/backup` exports from a working TV:
   - profiles, Apps for Everyone, dock, start-up profile, Earn Time, Guest Time, profile switches
   - never paired phones, the password hash or the PIN

   Setup offers two choices: *Same as my other TV* (import) or *Start simple* (the defaults).
5. **APKs.**
   - The tv-hero TV app (about 600 KB) ships inside the phone app, signed with the same release key, so versions always match.
   - Third-party apps are downloaded during setup from their official sources, each with a pinned SHA-256: TV Bro and Brave 1.80.124 from their GitHub releases, WhatsApp from whatsapp.com. They are never bundled. Licences and terms must be checked first.
6. **Wizard UI.** It follows the phone app's HIG style. Every step shows what it will do, with a progress list (✓ done, ↺ undo), and nothing loosens without saying so.

## The steps

| # | Step | Done by | Notes |
|---|---|---|---|
| 0 | Turn on developer options and network debugging on the TV; press **Allow** on the TV's "Allow debugging?" prompt | the person, guided with pictures | Android 9 has no wireless-debugging pairing. The exact menu path on a factory-fresh MiTV-AXSO0 must be checked: it may need USB debugging switched on, then network ADB. |
| 1 | Find the TV (mDNS / subnet scan on :5555), connect, check model, firmware, free space, Android 9 | phone | Refuse unknown models in v1, or allow "try anyway" with only the safe steps. |
| 2 | Save the baseline | phone | |
| 3 | Disable the debloat lists, one batch at a time, with the input-driver safety check | phone | Same lists and checks as `debloat/`. |
| 4 | Install tv-hero, make it Home, then disable the stock launcher only once Home works | phone | Port of `install-tv.sh`, including putting the stock launcher back on failure. |
| 5 | Enable the guard and the media listener; confirm it's bound (re-set the setting if not, reboot as the last resort) | phone | Uses the learnings from 2.2. |
| 6 | Install TV Bro, Brave, WhatsApp; first-run screens | phone + person | Phone browsers' first-run screens are portrait-only; WhatsApp terms and linking are left to the person. |
| 7 | Pair: the normal code comparison and Allow on the TV; set the parent password and PIN | person | Existing protocol 2 flow, unchanged. |
| 8 | Apply the family template | phone (mTLS API) | |
| 9 | Optional: Tailscale (Play Store install, sign-in on the TV, always-on VPN) and DNS filtering | person + phone | Sign-in can't be automated. Private DNS stays automatic while Tailscale runs. |
| 10 | Turn ADB off and check :5555 is closed | phone | Same check as `tools/adb-closed.py`. |
| 11 | Summary: what changed, the undo button, and anything left for the person | phone | |

## Security

- ADB is plaintext and gives full control. It's used only during setup, only on a private home network (the phone refuses public or cellular networks), and is switched off and checked at the end. If setup is abandoned, the next app start reminds the person and offers to switch it off.
- The phone's ADB key is separate from its TLS identity. **Forget This TV** also forgets the ADB key.
- Downloaded APKs are installed only if their SHA-256 matches the recipe. The recipe is part of the signed phone app, not fetched from a server.
- Nothing is uninstalled; everything is disable/enable and can be undone.
- The baseline and logs never leave the phone.

## Milestones

| M | What | Check |
|---|---|---|
| M1 | ADB client + preflight (read-only) | Phone lists this TV's model, firmware and package states correctly. |
| M2 | Install/update tv-hero from the phone | Replaces `install-tv.sh` for updates: install, Home, guard bound. Useful on its own straight away. |
| M3 | Debloat + Return to Stock | Disable one list and undo it on this TV, with screenshots. |
| M4 | Third-party apps with pinned hashes | Reinstall TV Bro on this TV from the phone. |
| M5 | `GET /v1/backup` + import + templates | Export this TV, import into a test profile set, compare. |
| M6 | End to end on a factory-fresh TV | Needs a factory reset of this TV or a second TV. **Ajit decides.** |
| M7 | Docs and screenshots | README "Set up a TV", SECURITY section on the setup ADB window. |

## Open decisions

1. **Models:** only MiTV-AXSO0 first (safest, matches every learning), or any Android TV 9+ with a reduced "safe steps only" recipe?
2. **Factory reset for the real test (M6):** it erases this TV (apps, sign-ins, WhatsApp link, Tailscale). Alternatively, borrow or buy a second unit.
3. **Third-party apps:** download from official sources during setup (plan above), or leave them to the person?
4. **Tailscale and DNS filtering:** part of the wizard, or a separate "Remote access" step later?

## Risks

- Factory-fresh firmware may show extra first-boot screens or differ from PI.6036. The preflight check and per-step verification catch this, but the first real run needs someone at the TV.
- The TV's ADB authorisation prompt and every first-run screen need the remote. The wizard must say exactly what to press.
- Large APKs over ADB from a phone may be slow; Brave is about 200 MB. Show progress and allow resuming.
- Disabling the wrong package can break input or HDMI. Use only the tested lists, check each batch, and undo at once if the check fails.
