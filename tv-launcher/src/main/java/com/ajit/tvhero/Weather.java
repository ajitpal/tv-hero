package com.ajit.tvhero;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Weather from Open-Meteo (2.11), the TV's one outside connection when a parent sets a city. Plain Java, so
 * the parsing, the sky for each WMO code and the refresh schedule are unit-tested. Only the city's latitude
 * and longitude are sent; Open-Meteo is free for non-commercial use, needs no account, and its data is
 * CC BY 4.0, credited on screen as {@link #CREDIT}.
 */
final class Weather {
    static final String HOST = "api.open-meteo.com";
    static final String CREDIT = "Weather data by Open-Meteo.com";
    /** Refresh at most this often while the screen is on (~15 calls a day). */
    static final long REFRESH_MS = 60 * 60_000L;
    /** After a failed fetch, try again after this, doubling up to {@link #MAX_BACKOFF_MS}. */
    static final long FIRST_BACKOFF_MS = 15 * 60_000L, MAX_BACKOFF_MS = 2 * 60 * 60_000L;
    /** A reading older than this isn't shown. */
    static final long STALE_MS = 6 * 60 * 60_000L;

    enum Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    /** One reading: now, today and tomorrow. Temperatures in whole °C. */
    static final class Reading {
        final String place;
        final int temp, code, todayMax, todayMin, tomorrowMax, tomorrowMin, tomorrowCode;
        final boolean day;
        final long fetchedAt;

        Reading(String place, int temp, int code, boolean day, int todayMax, int todayMin,
                int tomorrowMax, int tomorrowMin, int tomorrowCode, long fetchedAt) {
            this.place = place;
            this.temp = temp;
            this.code = code;
            this.day = day;
            this.todayMax = todayMax;
            this.todayMin = todayMin;
            this.tomorrowMax = tomorrowMax;
            this.tomorrowMin = tomorrowMin;
            this.tomorrowCode = tomorrowCode;
            this.fetchedAt = fetchedAt;
        }

        JSONObject json() throws JSONException {
            return new JSONObject().put("place", place).put("temp", temp).put("code", code).put("day", day)
                    .put("today_max", todayMax).put("today_min", todayMin).put("tomorrow_max", tomorrowMax)
                    .put("tomorrow_min", tomorrowMin).put("tomorrow_code", tomorrowCode).put("fetched_at", fetchedAt)
                    .put("text", describe(code)).put("tomorrow_text", describe(tomorrowCode));
        }

        static Reading from(JSONObject o) {
            if (o == null || !o.has("temp")) return null;
            return new Reading(o.optString("place"), o.optInt("temp"), o.optInt("code"), o.optBoolean("day", true),
                    o.optInt("today_max"), o.optInt("today_min"), o.optInt("tomorrow_max"), o.optInt("tomorrow_min"),
                    o.optInt("tomorrow_code"), o.optLong("fetched_at"));
        }

        /** "Today 33° / 24° · Tomorrow 31° / 23°, rain" for the Home info line. */
        String outlook() {
            return "Today " + todayMax + "° / " + todayMin + "°  ·  Tomorrow " + tomorrowMax + "° / "
                    + tomorrowMin + "°, " + describe(tomorrowCode).toLowerCase(Locale.ROOT);
        }
    }

    private Weather() { }

    /** The forecast address: the city's position only, rounded to 2 decimals (about 1 km). */
    static String url(double lat, double lon) {
        return String.format(Locale.ROOT, "https://%s/v1/forecast?latitude=%.2f&longitude=%.2f"
                + "&current=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=2", HOST, lat, lon);
    }

    static boolean validPosition(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    /** Open-Meteo's answer as a reading, or null if it isn't one. */
    static Reading parse(String place, String body, long now) {
        try {
            JSONObject o = new JSONObject(body);
            JSONObject cur = o.getJSONObject("current");
            JSONObject daily = o.getJSONObject("daily");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray max = daily.getJSONArray("temperature_2m_max");
            JSONArray min = daily.getJSONArray("temperature_2m_min");
            int tomorrow = codes.length() > 1 ? 1 : 0;
            return new Reading(place, round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"),
                    cur.optInt("is_day", 1) != 0, round(max.getDouble(0)), round(min.getDouble(0)),
                    round(max.getDouble(tomorrow)), round(min.getDouble(tomorrow)), codes.getInt(tomorrow), now);
        } catch (JSONException | RuntimeException e) {
            return null;
        }
    }

    private static int round(double v) { return (int) Math.round(v); }

    /** WMO weather interpretation codes (as Open-Meteo documents them) grouped into skies we can draw. */
    static Sky sky(int code) {
        if (code <= 1) return Sky.CLEAR;
        if (code == 2) return Sky.PARTLY;
        if (code == 3) return Sky.CLOUDY;
        if (code == 45 || code == 48) return Sky.FOG;
        if (code >= 51 && code <= 57) return Sky.DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return Sky.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Sky.SNOW;
        if (code >= 95) return Sky.STORM;
        return Sky.CLOUDY;
    }

    static String describe(int code) {
        switch (sky(code)) {
            case CLEAR: return code == 0 ? "Clear" : "Mostly clear";
            case PARTLY: return "Partly cloudy";
            case FOG: return "Fog";
            case DRIZZLE: return "Drizzle";
            case RAIN: return code >= 80 ? "Showers" : "Rain";
            case SNOW: return "Snow";
            case STORM: return "Thunderstorm";
            default: return "Cloudy";
        }
    }

    static boolean stale(Reading r, long now) {
        return r == null || now - r.fetchedAt > STALE_MS || r.fetchedAt > now + 60_000;
    }

    /** Is a fetch due? [failures] in a row since the last good one. */
    static boolean due(long now, long lastAttempt, long lastSuccess, int failures) {
        if (lastAttempt <= 0) return true;
        if (failures > 0) return now - lastAttempt >= backoff(failures);
        return now - Math.max(lastSuccess, lastAttempt) >= REFRESH_MS;
    }

    static long backoff(int failures) {
        long b = FIRST_BACKOFF_MS;
        for (int i = 1; i < failures && b < MAX_BACKOFF_MS; i++) b *= 2;
        return Math.min(b, MAX_BACKOFF_MS);
    }
}
