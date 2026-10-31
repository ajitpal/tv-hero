package com.ajit.tvhero;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The day's festival on Home (2.11): India's gazetted holidays for central government offices in Delhi, as
 * the Department of Personnel and Training lists them (2026 and 2027, checked 31 Oct 2026 against the lists
 * published by dfe.gov.in and the DoPT memorandum for 2027). Dates of Id-ul-Fitr, Id-ul-Zuha, Muharram and
 * Milad-un-Nabi depend on the moon and may move by a day, as the official lists note. Plain Java, tested.
 */
final class Festivals {

    /** How many days ahead an upcoming festival shows ("Diwali in 8 days"). */
    static final int AHEAD_DAYS = 10;

    static final class Day {
        final LocalDate date;
        final String name;
        /** The line on the day: "Happy Diwali", or just the name for days of remembrance. */
        final String onTheDay;
        /** A Festival skin to switch on for the day ("diwali", "dussehra"), or "". */
        final String skin;
        /** Shown ahead of time (festivals), or only on the day (days of remembrance). */
        final boolean ahead;

        Day(String date, String name, String onTheDay, String skin, boolean ahead) {
            this.date = LocalDate.parse(date);
            this.name = name;
            this.onTheDay = onTheDay;
            this.skin = skin;
            this.ahead = ahead;
        }
    }

    private static Day fest(String date, String name) { return new Day(date, name, "Happy " + name, "", true); }

    private static Day fest(String date, String name, String skin) { return new Day(date, name, "Happy " + name, skin, true); }

    private static Day day(String date, String name) { return new Day(date, name, name, "", true); }

    private static Day quiet(String date, String name) { return new Day(date, name, name, "", false); }

    static final List<Day> INDIA;

    static {
        List<Day> d = new ArrayList<>();
        // 2026
        d.add(day("2026-01-26", "Republic Day"));
        d.add(fest("2026-03-04", "Holi"));
        d.add(fest("2026-03-21", "Id-ul-Fitr"));
        d.add(fest("2026-03-26", "Ram Navami"));
        d.add(fest("2026-03-31", "Mahavir Jayanti"));
        d.add(quiet("2026-04-03", "Good Friday"));
        d.add(fest("2026-05-01", "Buddha Purnima"));
        d.add(fest("2026-05-27", "Id-ul-Zuha (Bakrid)"));
        d.add(quiet("2026-06-26", "Muharram"));
        d.add(day("2026-08-15", "Independence Day"));
        d.add(fest("2026-08-26", "Milad-un-Nabi"));
        d.add(fest("2026-09-04", "Janmashtami"));
        d.add(day("2026-10-02", "Gandhi Jayanti"));
        d.add(fest("2026-10-20", "Dussehra", "dussehra"));
        d.add(fest("2026-11-08", "Diwali", "diwali"));
        d.add(fest("2026-11-24", "Guru Nanak Jayanti"));
        d.add(new Day("2026-12-25", "Christmas", "Merry Christmas", "", true));
        // 2027
        d.add(day("2027-01-26", "Republic Day"));
        d.add(fest("2027-03-10", "Id-ul-Fitr"));
        d.add(fest("2027-03-23", "Holi"));
        d.add(quiet("2027-03-26", "Good Friday"));
        d.add(fest("2027-04-15", "Ram Navami"));
        d.add(fest("2027-04-19", "Mahavir Jayanti"));
        d.add(fest("2027-05-17", "Id-ul-Zuha (Bakrid)"));
        d.add(fest("2027-05-20", "Buddha Purnima"));
        d.add(quiet("2027-06-16", "Muharram"));
        d.add(day("2027-08-15", "Independence Day"));
        d.add(fest("2027-08-15", "Milad-un-Nabi"));
        d.add(fest("2027-08-25", "Janmashtami"));
        d.add(day("2027-10-02", "Gandhi Jayanti"));
        d.add(fest("2027-10-09", "Dussehra", "dussehra"));
        d.add(fest("2027-10-29", "Diwali", "diwali"));
        d.add(fest("2027-11-14", "Guru Nanak Jayanti"));
        d.add(new Day("2027-12-25", "Christmas", "Merry Christmas", "", true));
        INDIA = Collections.unmodifiableList(d);
    }

    private Festivals() { }

    /** Days on [today], in table order (two can share a day). */
    static List<Day> on(LocalDate today) {
        List<Day> out = new ArrayList<>();
        for (Day d : INDIA) if (d.date.equals(today)) out.add(d);
        return out;
    }

    /** The first festival after [today] within {@link #AHEAD_DAYS}, or null. */
    static Day next(LocalDate today) {
        for (Day d : INDIA) {
            long n = ChronoUnit.DAYS.between(today, d.date);
            if (n >= 1 && n <= AHEAD_DAYS && d.ahead) return d;
        }
        return null;
    }

    /** The Home line for [today]: "Happy Diwali", "Tomorrow: Diwali", "Diwali in 8 days", or "" for none. */
    static String line(LocalDate today) {
        List<Day> now = on(today);
        if (!now.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (Day d : now) {
                if (b.length() > 0) b.append("  ·  ");
                b.append(d.onTheDay);
            }
            return b.toString();
        }
        Day n = next(today);
        if (n == null) return "";
        long days = ChronoUnit.DAYS.between(today, n.date);
        return days == 1 ? "Tomorrow: " + n.name : n.name + " in " + days + " days";
    }

    /** The festive skin for [today] ("diwali"), or "". */
    static String skin(LocalDate today) {
        for (Day d : on(today)) if (!d.skin.isEmpty()) return d.skin;
        return "";
    }

    /** The phone's switch (Settings → Festivals), on by default where the TV's clock is set to India. */
    static boolean enabled(android.content.Context c) {
        return c.getSharedPreferences("festivals", android.content.Context.MODE_PRIVATE)
                .getBoolean("on", defaultOn(java.util.TimeZone.getDefault().getID()));
    }

    static void setEnabled(android.content.Context c, boolean on) {
        c.getSharedPreferences("festivals", android.content.Context.MODE_PRIVATE).edit().putBoolean("on", on).apply();
    }

    /** On by default where the TV's clock is set to India. */
    static boolean defaultOn(String timeZoneId) {
        return "Asia/Kolkata".equals(timeZoneId) || "Asia/Calcutta".equals(timeZoneId);
    }

    /** The last day the table covers: after it the line stays empty until tv-hero is updated. */
    static LocalDate coveredUntil() { return INDIA.get(INDIA.size() - 1).date; }
}
