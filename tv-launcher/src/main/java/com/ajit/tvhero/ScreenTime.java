package com.ajit.tvhero;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Screen time, counted by the second (2.9 "Fair time"). Plain Java, so tests can replay real sequences of
 * events without a TV.
 *
 * What's "in front" changes only when a full-screen app window opens. Small windows (the volume bar, the
 * keyboard, a notification, a dialog, tv-hero's own corner notes) never change it, so a video playing on
 * behind the volume bar keeps counting. Every change (app, profile, screen on or off, a lock screen, the
 * screensaver) first closes the running stretch, so each second goes to the app and profile it belonged to.
 *
 * The guard feeds it window events and state changes; [Sink] receives whole seconds per profile and app.
 */
final class ScreenTime {

    /** What kind of window an accessibility event came from. */
    enum Window {
        /** An activity: a full-screen app (or a dialog-style activity) that takes the front. */
        APP,
        /** Anything else: the volume bar, the keyboard, a toast, a dialog, an overlay. Never takes the front. */
        SMALL,
        /** A screensaver's window: nobody is watching until it ends. */
        DREAM,
    }

    interface Sink {
        void add(String profile, String pkg, int seconds);
    }

    /**
     * A stretch longer than this without any update means the TV slept or the app was frozen: count at most
     * this much of it. The guard updates at least every 20 seconds while the screen is on.
     */
    static final long MAX_STRETCH_MS = 60_000;

    private final Sink sink;
    /** Is time in this app screen time? (an app with a launcher entry, not Home, Settings or system screens) */
    private final Predicate<String> countsAsWatching;

    private String front = "";
    private String profile = "";
    private boolean screenOn = true, lockScreen, dream;

    // The open stretch: since when, and what it belongs to.
    private long since = -1;
    private String openProfile = "", openPkg = "";
    private boolean openCounting;
    /** Milliseconds under a second, kept per profile and app so nothing is lost by rounding. */
    private final Map<String, Long> carry = new HashMap<>();

    ScreenTime(Sink sink, Predicate<String> countsAsWatching) {
        this.sink = sink;
        this.countsAsWatching = countsAsWatching;
    }

    // ---------- inputs ----------

    /** A window changed. Returns true when the app in front changed. */
    boolean onWindow(long now, String pkg, Window kind) {
        if (pkg == null || pkg.isEmpty()) return false;
        switch (kind) {
            case DREAM:
                if (!dream) { dream = true; changed(now); }
                return false;
            case SMALL:
                return false;
            default:
                boolean was = dream;
                dream = false;
                if (pkg.equals(front)) { if (was) changed(now); return false; }
                front = pkg;
                changed(now);
                return true;
        }
    }

    /** The app in front, from a better source (Android's app-usage record), when it differs. */
    boolean setFront(long now, String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.equals(front)) return false;
        front = pkg;
        changed(now);
        return true;
    }

    void setProfile(long now, String id) {
        if (id == null) id = "";
        if (id.equals(profile)) return;
        profile = id;
        changed(now);
    }

    void setScreenOn(long now, boolean on) {
        if (on == screenOn) return;
        screenOn = on;
        if (!on) dream = false;   // standby ends any screensaver
        changed(now);
    }

    /** A full-screen lock or prompt (Time's up, Bedtime, Paused, Who's watching?, PIN, menus) covers the TV. */
    void setLockScreen(long now, boolean on) {
        if (on == lockScreen) return;
        lockScreen = on;
        changed(now);
    }

    /** Our own screensaver started or ended (it reports this directly, more reliably than its window). */
    void setDream(long now, boolean on) {
        if (on == dream) return;
        dream = on;
        changed(now);
    }

    /** Periodic flush, so the totals (and the limit) stay current while one app plays for a long time. */
    void tick(long now) {
        changed(now);
    }

    /**
     * When the guard should look again: its usual interval, or the second the time runs out if that comes
     * sooner, so Time's up appears on time instead of up to 20 seconds late. Never sooner than a second.
     */
    static long nextCheckMs(long usualMs, boolean counting, int secondsLeft) {
        long d = usualMs;
        if (counting && secondsLeft > 0) d = Math.min(d, secondsLeft * 1000L + 250);
        return Math.max(1000, d);
    }

    // ---------- state ----------

    String front() { return front; }

    boolean dreaming() { return dream; }

    /** Is the current moment counted? */
    boolean counting() {
        return screenOn && !lockScreen && !dream && !front.isEmpty() && countsAsWatching.test(front);
    }

    // ---------- the clock ----------

    /** Closes the open stretch (adding its time where it belonged) and opens a new one with the current state. */
    private void changed(long now) {
        if (since >= 0 && openCounting) {
            long ms = Math.max(0, Math.min(now - since, MAX_STRETCH_MS));
            String key = openProfile + "\u0000" + openPkg;
            Long c = carry.get(key);
            long total = ms + (c == null ? 0 : c);
            int seconds = (int) (total / 1000);
            carry.put(key, total % 1000);
            if (seconds > 0) sink.add(openProfile, openPkg, seconds);
        }
        since = now;
        openProfile = profile;
        openPkg = front;
        openCounting = counting();
    }
}
