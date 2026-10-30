#!/usr/bin/env bash
# Install (or update) the tv-hero TV app and make it Home, then switch on its guard service.
#
#   TV_HOST=192.168.1.50 tools/install-tv.sh [path/to/tv-hero.apk]
#
# Order matters and every step is checked: the new Home is started before the stock launcher is
# disabled, and if Home does not end up on tv-hero the stock launcher is put back.
# Optional: OLD_LAUNCHER=<package> uninstalls a previous launcher once tv-hero owns Home.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to the TV IP address}"
PKG=com.ajit.tvhero
APK="${1:-tv-launcher/build/outputs/apk/release/tv-launcher-release.apk}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
[ -f "$APK" ] || { echo "no APK at $APK: build it with ./gradlew :tv-launcher:assembleRelease" >&2; exit 1; }
tv() { "$PY" -I tools/tv.py "$@"; }
export MSYS_NO_PATHCONV=1

SDK=$(tv 'getprop ro.build.version.sdk' | tr -dc '0-9')
[ "${SDK:-0}" -ge 26 ] || { echo "this TV runs SDK $SDK; tv-hero needs 26 or newer" >&2; exit 1; }

HOME_NOW=$(tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1 | tr -d '\r')
STOCK="${STOCK_LAUNCHER:-}"
if [ -z "$STOCK" ]; then
  case "$HOME_NOW" in
    com.google.android.tvlauncher/*|com.google.android.apps.tv.launcherx/*|com.mitv.tvhome*/*) STOCK=${HOME_NOW%%/*} ;;
    *) STOCK=none ;;
  esac
fi
echo "Home now: $HOME_NOW (stock launcher to disable: $STOCK)"

"$PY" -I tools/b64push.py "$APK" /data/local/tmp/tvhero.apk
OUT=$(tv 'pm install -r /data/local/tmp/tvhero.apk; rm -f /data/local/tmp/tvhero.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "install failed; nothing else was changed" >&2; exit 1 ;; esac

START=$(tv "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $PKG/.MainActivity")
echo "$START"
printf '%s' "$START" | grep -qF 'Status: ok' || { echo "tv-hero did not start; Home left as it was" >&2; exit 1; }

printf '%s\n' \
  "pm grant $PKG android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted" \
  "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS && echo secure-settings-granted" \
  "appops set $PKG GET_USAGE_STATS allow && echo usage-access-granted" \
  "cmd package compile -m speed -f $PKG" \
  | tv --batch
[ "$STOCK" = none ] || tv "pm disable-user --user 0 $STOCK"
tv "cmd package set-home-activity --user 0 $PKG/.MainActivity"
HOME_AFTER=$(tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1 | tr -d '\r')
case "$HOME_AFTER" in
  "$PKG"/*) echo "Home now resolves to $HOME_AFTER" ;;
  *)
    echo "ERROR: Home resolves to '$HOME_AFTER', not tv-hero. Putting $STOCK back." >&2
    if [ "$STOCK" != none ]; then
      tv "pm enable --user 0 $STOCK" || true
      tv "cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $STOCK | tail -n 1)" || true
    fi
    exit 1 ;;
esac

# Guard service: add it to the enabled accessibility services without dropping others. Android
# turns a service off when its app is reinstalled, so clear and rewrite the list to bind it again.
SVC=$PKG/.GuardService
tv "s=\$(settings get secure enabled_accessibility_services); case \"\$s\" in null|'') n=$SVC ;; *$SVC*) n=\$s; settings put secure enabled_accessibility_services ''; sleep 1 ;; *) n=\$s:$SVC ;; esac; settings put secure enabled_accessibility_services \$n; settings put secure accessibility_enabled 1; echo guard-enabled"
# Media titles ("what's playing") come from media sessions, which need notification access.
tv "cmd notification allow_listener $PKG/$PKG.MediaListener && echo media-titles-enabled" || true
sleep 3
if tv "dumpsys accessibility | grep -c 'tv-hero guard'" | grep -q '[1-9]'; then
  echo "guard bound"
else
  # Seen on this Xiaomi (Android 9) after an update: the service stays enabled but unbound until a reboot.
  echo "Guard not bound yet after the update; rebooting the TV once (Android 9 binds it at boot)."
  tv sync >/dev/null
  tv --reboot
  echo "When the TV is back, check: tools/tv.py 'dumpsys accessibility | grep \"tv-hero guard\"'"
fi

if [ -n "${OLD_LAUNCHER:-}" ] && [ "$OLD_LAUNCHER" != "$PKG" ]; then
  tv "pm uninstall --user 0 $OLD_LAUNCHER" || true
fi
echo "Done. Pair the phone app with this TV: it finds the TV on the same Wi-Fi."
