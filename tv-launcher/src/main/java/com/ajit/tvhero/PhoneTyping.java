package com.ajit.tvhero;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Typing from the phone over the home Wi-Fi (2.10). While the phone's Type screen is open, the TV's keyboard
 * is switched to tv-hero Keyboard (PhoneKeyboard) with WRITE_SECURE_SETTINGS, which tv-hero already holds;
 * it is switched back when typing ends: the phone stops it, 2 minutes pass with no typing, or standby.
 * The keyboard that was in use is kept in preferences, so it is put back even after a crash or reboot.
 */
final class PhoneTyping {
    private static final String TAG = "TvHeroTyping";
    static final long IDLE_MS = 2 * 60_000;

    private static Handler main;

    /** Made on first use, so the class loads in plain JVM unit tests. */
    private static synchronized Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }
    private static boolean active;
    private static long lastAt;

    private PhoneTyping() { }

    static String imeId(Context ctx) { return ctx.getPackageName() + "/.PhoneKeyboard"; }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("typing", Context.MODE_PRIVATE);
    }

    /** [enabled] (the colon-separated setting, entries may carry ";subtypes") with [id] in it. */
    static String withMethod(String enabled, String id) {
        if (enabled == null || enabled.isEmpty()) return id;
        for (String e : enabled.split(":")) {
            int semi = e.indexOf(';');
            if ((semi >= 0 ? e.substring(0, semi) : e).equals(id)) return enabled;
        }
        return enabled + ":" + id;
    }

    /** Switches the TV to tv-hero Keyboard. False if the TV doesn't let tv-hero change its keyboard. */
    static synchronized boolean start(Context ctx, String phone) {
        Context app = ctx.getApplicationContext();
        ContentResolver cr = app.getContentResolver();
        String ours = imeId(app);
        try {
            String current = Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD);
            String enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_INPUT_METHODS);
            SharedPreferences p = prefs(app);
            if (!p.getBoolean("active", false) && !ours.equals(current)) {
                p.edit().putBoolean("active", true).putString("before_default", current == null ? "" : current)
                        .putString("before_enabled", enabled == null ? "" : enabled).commit();
            }
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_INPUT_METHODS, withMethod(enabled, ours));
            Settings.Secure.putString(cr, Settings.Secure.DEFAULT_INPUT_METHOD, ours);
        } catch (RuntimeException e) {
            Log.w(TAG, "can't switch the keyboard: " + e.getMessage());
            return false;
        }
        PhoneKeyboard.phone = phone == null ? "" : phone;
        lastAt = SystemClock.elapsedRealtime();
        if (!active) {
            active = true;
            main().postDelayed(idle, 30_000);
        }
        return true;
    }

    private static final Runnable idle = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            if (SystemClock.elapsedRealtime() - lastAt > IDLE_MS) {
                Context c = GuardService.instance;
                if (c != null) stop(c);
                return;
            }
            main().postDelayed(this, 30_000);
        }
    };

    static synchronized boolean active() { return active; }

    /** Puts the TV's own keyboard back, if tv-hero switched it. Safe to call any time. */
    static synchronized void stop(Context ctx) {
        active = false;
        main().removeCallbacks(idle);
        Context app = ctx.getApplicationContext();
        SharedPreferences p = prefs(app);
        if (!p.getBoolean("active", false)) return;
        ContentResolver cr = app.getContentResolver();
        try {
            String before = p.getString("before_default", "");
            String enabled = p.getString("before_enabled", "");
            if (!enabled.isEmpty()) Settings.Secure.putString(cr, Settings.Secure.ENABLED_INPUT_METHODS, enabled);
            if (!before.isEmpty()) Settings.Secure.putString(cr, Settings.Secure.DEFAULT_INPUT_METHOD, before);
        } catch (RuntimeException e) {
            Log.w(TAG, "can't put the keyboard back: " + e.getMessage());
        }
        p.edit().putBoolean("active", false).apply();
    }

    /** Text, deletions and Enter, run on the keyboard's thread. Null when no box has focus yet. */
    static Boolean send(String text, int backspace, boolean enter) {
        synchronized (PhoneTyping.class) {
            if (!active) return null;
            lastAt = SystemClock.elapsedRealtime();
        }
        PhoneKeyboard k = PhoneKeyboard.instance;
        if (k == null) return null;
        AtomicBoolean done = new AtomicBoolean(false), field = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        main().post(() -> {
            try {
                if (!k.hasField()) return;
                field.set(true);
                boolean ok = true;
                if (backspace > 0) ok = k.backspace(backspace);
                if (text != null && !text.isEmpty()) ok &= k.commit(text);
                if (enter) ok &= k.enter();
                done.set(ok);
            } finally {
                latch.countDown();
            }
        });
        try { latch.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return field.get() ? done.get() : null;
    }

    /** Is tv-hero Keyboard the TV's keyboard now, with a box focused? */
    static boolean ready() {
        PhoneKeyboard k = PhoneKeyboard.instance;
        if (k == null) return false;
        AtomicBoolean r = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        main().post(() -> { try { r.set(k.hasField()); } finally { latch.countDown(); } });
        try { latch.await(1, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return r.get();
    }
}
