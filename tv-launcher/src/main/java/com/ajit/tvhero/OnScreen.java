package com.ajit.tvhero;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * On-screen notes (2.9): one place to switch each extra that tv-hero draws on the TV on or off. Set from the
 * phone (Settings → On-Screen Notes) or the Parent Menu. These are only notes: switching one off never
 * loosens a rule (limits, bedtime and lock screens still apply), so no password is needed.
 */
final class OnScreen {
    static final String TIME_LEFT = "time_left", TOOLS_CORNER = "tools_corner", HOME_INFO = "home_info", WARNINGS = "warnings";
    static final String[] KEYS = {TIME_LEFT, TOOLS_CORNER, HOME_INFO, WARNINGS};

    private final SharedPreferences prefs;

    private OnScreen(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("onscreen", Context.MODE_PRIVATE);
    }

    static OnScreen get(Context ctx) { return new OnScreen(ctx); }

    boolean on(String key) { return prefs.getBoolean(key, true); }

    void set(String key, boolean on) {
        for (String k : KEYS) if (k.equals(key)) { prefs.edit().putBoolean(key, on).apply(); return; }
    }

    JSONObject json() {
        JSONObject o = new JSONObject();
        try { for (String k : KEYS) o.put(k, on(k)); } catch (JSONException ignored) { }
        return o;
    }

    /** Applies the keys present in [b]; unknown keys are ignored. */
    void apply(JSONObject b) {
        for (String k : KEYS) if (b.has(k)) set(k, b.optBoolean(k, true));
    }

    /**
     * The 5- and 1-minute notes can be switched off only for a profile whose time-left corner already shows
     * all the time, so a child is never left without warning. Bedtime notes always show.
     */
    static boolean timeWarnings(boolean warningsOn, boolean cornerOn, String profileCorner) {
        return warningsOn || !(cornerOn && Profiles.CORNER_ALWAYS.equals(profileCorner));
    }

    static String label(String key) {
        switch (key) {
            case TIME_LEFT: return "Time-Left Corner";
            case TOOLS_CORNER: return "Tools Corner Status";
            case HOME_INFO: return "Home Info Line";
            default: return "Time Warnings";
        }
    }
}
