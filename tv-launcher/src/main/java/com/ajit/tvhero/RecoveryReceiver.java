package com.ajit.tvhero;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Forgotten parent password / PIN: reset over ADB only.
 *
 *   adb shell am broadcast -n com.ajit.tvhero/.RecoveryReceiver -a com.ajit.tvhero.RESET_PARENT \
 *       --es password <new> [--es pin <4-6 digits>]
 *
 * The manifest requires the sender to hold WRITE_SECURE_SETTINGS, which only the shell (ADB) and the
 * system have, so no app on the TV can use it. Turning ADB on already needs Parent access to TV Settings
 * (or the old password from the phone). The new values never go to the log; the event log records
 * that a reset happened.
 */
public class RecoveryReceiver extends BroadcastReceiver {
    static final String ACTION = "com.ajit.tvhero.RESET_PARENT";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        GuardStore store = GuardStore.get(ctx);
        Profiles profiles = Profiles.get(ctx);
        String pw = intent.getStringExtra("password");
        String pin = intent.getStringExtra("pin");
        StringBuilder done = new StringBuilder();
        if (pw != null) {
            if (pw.length() < 6) { setResultData("password must have at least 6 characters"); return; }
            store.setPassword(pw);
            done.append("password");
        }
        if (pin != null) {
            if (!pin.matches("[0-9]{4,6}")) { setResultData("PIN must be 4 to 6 digits"); return; }
            profiles.setPin(pin);
            done.append(done.length() > 0 ? "+pin" : "pin");
        }
        store.log("parent_reset", done.toString(), "ADB");
        Log.i("TvHeroGuard", "parent reset over ADB: " + done);
        setResultData("reset: " + done);
    }
}
