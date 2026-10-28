# Phone ↔ TV protocol (v1)

The TV app (`com.ajit.tvhero`, `GuardService` + `Api`) listens on **TCP 8765** and advertises **`_tvhero._tcp`** over DNS-SD. JSON over HTTP/1.1, one request per connection, bodies ≤ 64 KB.

## Pairing (unsigned)

1. `POST /v1/pair/start` `{"device_name": "Ajit's Poco"}` → `{"salt": b64(16 bytes), "password_set": bool, "expires_in": 120}`. The TV shows a 6-digit code for 2 minutes.
2. `POST /v1/pair/finish` `{"device_name", "code", "password"}`
   - If no password is set yet, this one becomes the parent password (≥ 4 characters).
   - Otherwise it must match: 5 wrong tries lock it for 5 minutes (HTTP 423). 5 wrong codes end the pairing.
   - → `{"device_id": hex(8 bytes), "box": b64(iv(12) ‖ AES-256-GCM(secret(32)))}`, with key = PBKDF2-HMAC-SHA1(`code + ":" + password`, salt, 20 000 rounds, 32 bytes).

`GET /v1/info` (unsigned) → `{"app":"tv-hero","api":1,"version","model","android","password_set"}`.

## Signed requests

Headers:
- `X-Device-Id`: from pairing
- `X-Timestamp`: ms since epoch, within ±2 minutes of the TV's clock
- `X-Signature`: base64(HMAC-SHA256(secret, `METHOD + "\n" + PATH[?QUERY] + "\n" + TIMESTAMP + "\n" + hex(SHA-256(body))`))

A signature is accepted once (replay check over the last 500).

| Method & path | Body | Password | Result |
|---|---|---|---|
| `GET /v1/apps` | | | `{"apps":[{package,label,state: allowed\|blocked\|pending, system, protected}], "pending_count", "block_new_installs"}` |
| `GET /v1/apps/icon?package=` | | | PNG 96×96 |
| `POST /v1/apps/block` | `{package}` | no | blocked (protected system apps refuse with 400) |
| `POST /v1/apps/unblock` · `/v1/apps/approve` | `{package, password}` | **yes** | allowed |
| `GET /v1/settings` · `POST /v1/settings` | `{block_new_installs[, password]}` | to turn **off** | |
| `GET /v1/home` · `POST /v1/home` | `{dock_apps[], hidden_apps[], columns 4–7, greeting_name, show_greeting}` | no | written to the launcher's `config.json`; Home rebuilds at once |
| `POST /v1/launch` | `{package}` | no | opens the app (403 if blocked) |
| `POST /v1/key` | `{action: home\|back\|recents\|notifications\|quick_settings}` | no | accessibility global action |
| `POST /v1/password` | `{password, new_password}` | **yes** | |
| `GET /v1/devices` · `POST /v1/devices/remove` | `{device_id, password}` | **yes** to remove | |
| `GET /v1/log` | | | last 200 events: `paired`, `blocked`, `allowed`, `installed`, `installed_pending`, `opened_blocked`, `opened_pending`, `removed` |

Errors: `{"error": "human-readable message"}` with 400 / 401 / 403 / 404 / 409 / 423 / 500.

Reference implementations: `tv/.../Api.java` (server), `phone/.../TvProtocol.kt` + `TvClient.kt` (phone), `tools/tvhero_client.py` (computer, for testing). The phone's unit tests use vectors produced by the Python client.

## Bluetooth HID (phone → TV, no TV software involved)

Report descriptor in `phone/.../Hid.kt`:

| Report | Contents | Used for |
|---|---|---|
| 1 | Keyboard: modifiers, reserved, 6 keys | Arrows → D-pad, Enter → OK, typing |
| 2 | Consumer control: one 16-bit usage | AC Home 0x223, AC Back 0x224, Menu 0x40, Volume 0xE9/0xEA, Mute 0xE2, Play/Pause 0xCD |
| 3 | Game pad: 16 buttons, hat (4 bits + 4 padding), X Y Z Rz (signed bytes) | Button 1 A, 2 B, 4 X, 5 Y, 7 L1, 8 R1, 11 Select, 12 Start, 13 Mode; hat → D-pad; sticks |
