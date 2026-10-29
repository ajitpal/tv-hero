# Phone ↔ TV protocol (v2)

Version 2 replaced v1 (signed HTTP with a shared secret) in tv-hero 2.0. Phones paired with v1 are removed by the TV and must pair again.

Shared definitions live in `protocol/` (plain Java, used by both apps): `Protocol.java` (ports, paths, TLS settings), `Pairing.java` (commitment and code), `Tls.java` (pinned trust, Keystore key manager), `Net.java` (home vs internet). `tools/tvhero_client.py` implements the same protocol in Python for testing; `--self-test` checks it against the Java test vectors.

## Two ports

| Port | Transport | What it serves |
|---|---|---|
| **8765** | plain HTTP, **home network only** | `GET /v1/info`, `POST /v1/pair/start`, `POST /v1/pair/reveal`, `GET /v1/pair/status`. Nothing here can control the TV; anything else answers 403. |
| **8766** | **mutual TLS** | every command. The TV presents its certificate; the phone must present a certificate the TV pinned at pairing. |

The TV advertises itself over mDNS as `_tvhero._tcp` (port 8765).

## TLS

- Each side has an EC P-256 key made inside its **Android Keystore** (non-exportable), with the self-signed certificate the Keystore issues. The Python client keeps its key in `~/.tvhero/key.pem` (mode 600).
- No certificate authorities. Each side trusts exactly the certificate whose **SHA-256 fingerprint** it pinned at pairing. Hostnames aren't checked: the pin is the identity.
- Versions and suites (`Protocol.TLS_*`): TLS 1.3 where both ends have it, otherwise TLS 1.2 with `ECDHE-ECDSA` + AES-GCM or ChaCha20-Poly1305. The TV (Android 9) negotiates **TLS 1.2, `ECDHE-ECDSA-AES128-GCM-SHA256`** (measured). Every suite gives forward secrecy.
- The TV checks the phone's fingerprint **live on every handshake** and keeps no session cache, so a removed phone is refused at the next connection.
- The caller's identity for each request is the fingerprint the handshake proved. There are no IDs, timestamps or signatures in headers.

## Pairing: comparing codes

```
phone → TV  POST /v1/pair/start   {device_name, cert: base64 DER}
TV → phone                        {pair_id, tv_cert, commitment, password_set, expires_in: 180}
            commitment = H("tvhero-commit-v1", tv_cert, phone_cert, Nt)

phone → TV  POST /v1/pair/reveal  {pair_id, nonce: Np (32 bytes)}
TV → phone                        {nonce: Nt}
            The phone checks the commitment. Both sides compute
            code = uint32(H("tvhero-sas-v1", phone_cert, tv_cert, Np, Nt)[0..4]) mod 1 000 000
            The TV shows the code with  [Don't Allow]  [Allow]  (focus on Don't Allow).

phone → TV  GET /v1/pair/status?pair_id=…   → {state: waiting_tv | allowed | denied | expired | none}

            After Allow, the TV pins the phone's certificate as "pending".
phone → TV  POST /v1/pair/complete {password}       ← over mutual TLS, port 8766
TV → phone                        {device_id, name}  the phone is now a full member
```

`H(label, parts…)` is SHA-256 over each part, label first, each preceded by its length as a 4-byte big-endian integer. Test vector (all implementations): tv_cert = `"tv-cert"`, phone_cert = `"phone-cert"`, Np = 32 × `0x01`, Nt = 32 × `0x02` → commitment `659fd388…cd99a6`, code `569378`.

Rules on the TV:
- one pairing at a time; 3 minutes to finish
- pairing only from the home network
- the phone certificate must be EC P-256
- an already-paired certificate can't pair again
- a phone that was allowed but never completed is forgotten after 10 minutes
- the first phone creates the parent password (at least 6 characters); later phones must give it
- after 5 wrong passwords, password checks lock for 5 minutes

## Commands (port 8766)

Changes that loosen protection also need `"password"` in the body.

| Endpoint | Body / query | Password | Returns |
|---|---|---|---|
| `GET /v1/info` | | | app, api (2), version, model, password_set, secure_port, addresses, tailscale_ip |
| `GET /v1/status` | | | power (`on`/`standby`), volume, now, profile, internet_control, version |
| `POST /v1/power` | `{action: sleep \| wake}` | | sleep: standby now. wake works only while the TV can still answer the network. |
| `GET /v1/volume` | | | `{level, max, muted, cap}`. `cap` is the night-volume limit right now. |
| `POST /v1/volume` | `{level}` \| `{step: ±1..5}` \| `{mute: bool}` | | volume. Never above `cap`. |
| `POST /v1/key` | `{action: home \| back \| recents \| notifications \| quick_settings \| sleep \| picker \| screensaver}` | | |
| `POST /v1/launch` | `{package}` | | a kid's profile can only open its allowed apps |
| `GET /v1/now` | | | what's on screen: app, title (from media notifications), profile, overlay, minutes_left |
| `GET /v1/apps`, `GET /v1/apps/icon?package=` | | | apps with state `allowed` / `blocked` / `pending`; PNG icon |
| `POST /v1/apps/block` | `{package}` | | |
| `POST /v1/apps/unblock`, `/approve` | `{package}` | ✔ | |
| `GET /v1/packages/state?packages=a,b` | | | installed / enabled per package, for the Privacy dashboard (read-only) |
| `GET/POST /v1/settings` | `{block_new_installs}` | ✔ to turn off | |
| `GET/POST /v1/home` | dock_apps, hidden_apps, columns, greeting_name, show_greeting | | |
| `GET /v1/profiles`, `POST /v1/profiles/save`, `/delete`, `/activate`, `/extra`, `/on_wake` | | ✔ except activating a kid or `guest` | profiles (up to 10 kids), see `Profiles.java` |
| (profile fields) | `quiz: {enabled, minutes, daily_max, level 1-3, topics: [maths, english, gk, hindi, puzzles]}`; read-only `quiz_earned_today`, `quiz_right_today` | | Earn Time. A save without `quiz` keeps what the TV has. |
| (guest) | `GET /v1/profiles` → `guest: {daily_limit, used_today, extra_today, minutes_left}`. Set with `profiles/save {profile: {id: "guest", daily_limit}}`; `/extra` and requests accept `id: "guest"` | ✔ | Guest's daily allowance, shared by all guests that day |
| `POST /v1/profiles/extra` | `{id, minutes: -240..240}` | ✔ to add | today only; taking minutes away needs no password |
| `POST /v1/profiles/end` | `{id}` (a kid or `guest`) | | ends today's time now: Time's up at once if that profile is watching, even without a daily limit. Adding time reopens it. Profiles show `ended_today`. |
| `POST /v1/profiles/quiz_reset` | `{id}` | ✔ | clears today's Earn Time count (earned minutes, answers, break) so the profile can earn its daily max again |
| `POST /v1/profiles/switches` | `{limit: -1..10}` | ✔ to allow more | free profile switches a day on the TV from a kid or Guest (default 1; -1 no limit, 0 always ask). `GET /v1/profiles` has `switch_limit`, `switches_today`. |
| `POST /v1/profiles/common` | `{apps: [package…]}` | ✔ | Apps for Everyone: on in every profile on top of its own (`common_apps` in `GET /v1/profiles`) |
| `GET /v1/requests`, `POST /v1/requests/answer` | `{id, allow}` | ✔ to allow | kids' requests, each with `kind`: `app` (allow the app), `time` ("time:30": more minutes today), `bedtime` ("bedtime:60": stay up later), `switch` ("switch:<profile id>": allowing it switches the TV to that profile) |
| `POST /v1/pin` | `{pin}` (empty clears) | ✔ | parent PIN typed on the TV |
| `POST /v1/lock` | `{locked}` | ✔ to unlock | pause the TV |
| `POST /v1/sleep`, `GET/POST /v1/quiet` | | | sleep timer, night volume cap |
| `GET /v1/activity?since=` | | | watch sessions and minutes used today |
| `GET /v1/devices` | | | `{devices: [{id, name, paired_at, last_seen, last_via, state, fingerprint, active}], you}` |
| `POST /v1/devices/remove` | `{device_id}` (omit = this phone) | ✔ for another phone | revokes at once |
| `GET /v1/internet` | | | `{enabled, since, by, pending, tailscale_ip}` |
| `POST /v1/internet` | `{enabled}` | ✔ to turn on | off: at once, from anywhere. On: only from the home network, answers **202** `pending: true` and the TV asks someone to press Allow. |
| `POST /v1/password` | `{password, new_password}` (≥ 6) | ✔ | |
| `GET /v1/tools` | | | `{state: {tool: none\|timer\|stopwatch\|workout, running, finished, label, elapsed_ms, left_ms, laps, phase, phase_left_ms, …}, presets: [workout…], tools}` |
| `POST /v1/tools/start` | `{tool: timer, minutes \| seconds, label}` \| `{tool: stopwatch}` \| `{tool: workout, workout: {name, prepare, work, rest, rounds, sets, set_rest, exercises}}` \| `{tool: clock}` | | starts it and brings its screen forward (unless a lock screen is up) |
| `POST /v1/tools/control` | `{action: pause \| resume \| toggle \| stop \| add_minute \| lap \| skip}` | | |
| `GET /v1/log` | | | last 200 events `{t, type, package, by}`; `by` names the phone or "TV" |
| `GET /v1/remote`, `POST /v1/adb` | `{enabled}` | ✔ | Tailscale status; network debugging on/off |

**From outside the home network** (any address that isn't private; Tailscale's 100.64.0.0/10 counts as outside), every command answers 403 unless internet control is on. Each time a phone starts controlling over the internet, the TV shows a note, at most once every 10 minutes per phone.

Errors: `{"error": "human-readable message"}` with 400 / 401 (not paired, or pairing not finished) / 403 / 404 / 409 / 423 / 500 / 503.

Reference implementations: `tv-launcher/.../Api.java` (server), `companion/.../TvClient.kt` + `PairingSession.kt` + `Identity.kt` (phone), `tools/tvhero_client.py` (computer).

## Bluetooth HID (phone → TV, no TV software involved)

Report descriptor in `companion/.../Hid.kt`:

| Report | Contents | Used for |
|---|---|---|
| 1 | Keyboard: modifiers, reserved, 6 keys | Arrows → D-pad, Enter → OK, typing |
| 2 | Consumer control: one 16-bit usage | AC Home 0x223, AC Back 0x224, Menu 0x40, Volume 0xE9/0xEA, Mute 0xE2, Play/Pause 0xCD |
| 3 | Game pad: 16 buttons, hat (4 bits + 4 padding), X Y Z Rz (signed bytes) | Button 1 A, 2 B, 4 X, 5 Y, 7 L1, 8 R1, 11 Select, 12 Start, 13 Mode; hat → D-pad; sticks |

Bluetooth pairing is Android's own (done once from the TV's Remotes & Accessories) and carries no tv-hero commands.
