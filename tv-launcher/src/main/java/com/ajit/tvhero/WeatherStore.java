package com.ajit.tvhero;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The city set from the phone, the last weather reading, and the fetch (2.11). Nothing is fetched until a
 * parent sets a city; "Turn Off Weather" stops it. The fetch runs off the main thread, at most hourly while
 * the screen is on, with a timeout and a backoff, and sends only the city's latitude and longitude.
 */
final class WeatherStore {
    static final String ACTION_WEATHER = "com.ajit.tvhero.WEATHER";
    private static final String TAG = "TvHeroWeather";
    private static volatile boolean fetching;

    private final Context ctx;
    private final SharedPreferences prefs;

    private WeatherStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        prefs = this.ctx.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    static WeatherStore get(Context ctx) { return new WeatherStore(ctx); }

    boolean on() { return prefs.getBoolean("on", false) && !city().isEmpty(); }

    String city() { return prefs.getString("city", ""); }

    double lat() { return Double.longBitsToDouble(prefs.getLong("lat", Double.doubleToLongBits(Double.NaN))); }

    double lon() { return Double.longBitsToDouble(prefs.getLong("lon", Double.doubleToLongBits(Double.NaN))); }

    boolean screensaver() { return prefs.getBoolean("screensaver", true); }

    void setScreensaver(boolean on) { prefs.edit().putBoolean("screensaver", on).apply(); }

    void setCity(String name, double lat, double lon) {
        prefs.edit().putBoolean("on", true).putString("city", name).putLong("lat", Double.doubleToLongBits(lat))
                .putLong("lon", Double.doubleToLongBits(lon)).remove("reading").putLong("attempt", 0)
                .putInt("failures", 0).remove("error").apply();
        changed();
    }

    void turnOff() {
        prefs.edit().putBoolean("on", false).remove("reading").remove("error").apply();
        changed();
    }

    /** The last reading if it's fresh enough to show, else null. */
    Weather.Reading reading() {
        if (!on()) return null;
        Weather.Reading r;
        try { r = Weather.Reading.from(new JSONObject(prefs.getString("reading", "{}"))); } catch (JSONException e) { r = null; }
        return Weather.stale(r, System.currentTimeMillis()) ? null : r;
    }

    JSONObject json() throws JSONException {
        Weather.Reading r = reading();
        JSONObject o = new JSONObject().put("on", on()).put("screensaver", screensaver())
                .put("host", Weather.HOST).put("credit", Weather.CREDIT)
                .put("last_attempt", prefs.getLong("attempt", 0)).put("error", prefs.getString("error", ""));
        if (!city().isEmpty()) o.put("city", new JSONObject().put("name", city()).put("lat", lat()).put("lon", lon()));
        if (r != null) o.put("reading", r.json());
        return o;
    }

    /** Called from the guard's tick: fetches in the background when one is due. */
    void maybeFetch(boolean screenOn, boolean force) {
        if (!on() || fetching || (!screenOn && !force)) return;
        long now = System.currentTimeMillis();
        if (!force && !Weather.due(now, prefs.getLong("attempt", 0), prefs.getLong("success", 0), prefs.getInt("failures", 0))) return;
        fetching = true;
        prefs.edit().putLong("attempt", now).apply();
        String place = city();
        String url = Weather.url(lat(), lon());
        new Thread(() -> {
            try {
                Weather.Reading r = Weather.parse(place, get(url), System.currentTimeMillis());
                if (r == null) throw new java.io.IOException("unexpected answer");
                prefs.edit().putString("reading", r.json().toString()).putLong("success", r.fetchedAt)
                        .putInt("failures", 0).remove("error").apply();
                changed();
            } catch (Exception e) {
                Log.w(TAG, "fetch failed: " + e.getMessage());
                prefs.edit().putInt("failures", prefs.getInt("failures", 0) + 1)
                        .putString("error", e.getMessage() == null ? "no connection" : e.getMessage()).apply();
            } finally {
                fetching = false;
            }
        }, "weather").start();
    }

    private static String get(String address) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "tv-hero (github.com/ajitpal/tv-hero)");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("Open-Meteo answered " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 64 * 1024) throw new java.io.IOException("answer too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private void changed() {
        ctx.sendBroadcast(new Intent(ACTION_WEATHER).setPackage(ctx.getPackageName()));
    }
}
