# Troubleshooting

## Connecting

- `tools/tv.py --probe` times out: the TV is off or asleep, on another network, or USB debugging is off (Settings → Device Preferences → About → press Build 7 times → Developer options → USB debugging). On this TV, USB debugging also opens network ADB on port 5555.
- "Unauthorized": accept "Allow USB debugging?" on the TV (tick *Always allow*). A new copy of `tools/` makes a new key in `tools/adbkey/`, so the TV asks again; copy the old `tools/adbkey/` folder to avoid it.
- The phone app finds no TV: phone and TV must be on the same Wi-Fi (not a guest network). Enter the TV's IP by hand on the first screen.

## Screenshots

Live TV, HDMI inputs, Mi TV+ and DRM video come out black or 0 bytes. That's the TV protecting the picture, not a fault. Check what's in front with `tools/tv.py 'dumpsys activity activities | grep mResumedActivity'`.

## Guard (blocking) not working

- Check it's bound: `tools/tv.py 'dumpsys accessibility | grep "tv-hero guard"'`.
- After installing an update, Android 9 on this TV leaves the service enabled but unbound until a reboot. `tools/install-tv.sh` reboots once when that happens.
- If it's off in Settings → Accessibility, turn it on there, or rerun `tools/install-tv.sh`.

## Home screen

- Home shows the old launcher or a chooser: `tools/tv.py 'cmd package set-home-activity --user 0 com.ajit.tvhero/.MainActivity'`.
- The firmware re-enables `com.google.android.leanbacklauncher` at every boot; that's expected and it doesn't take Home.
- Keep TV Manager (`com.xiaomi.mitv.tvmanager`) disabled: its "Boost" deletes the home screen's settings file.

## Undo

- One app: `tools/tv.py 'pm enable <package>'`, or on the TV: Settings → Apps → See all apps → Show system apps.
- A whole list: `TV_HOST=<ip> tools/restore.sh debloat/<list>.txt` (newest first).
- Back to the stock home: `tools/tv.py 'pm enable com.google.android.tvlauncher'`, set it as Home, then uninstall `com.ajit.tvhero`.

## Disabling refused

If `pm disable-user` is refused for every package, the firmware doesn't allow it. Check with `pm list packages -d` rather than the exit code.
