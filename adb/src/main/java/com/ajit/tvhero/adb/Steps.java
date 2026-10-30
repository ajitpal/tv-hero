package com.ajit.tvhero.adb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The few changes the phone makes over ADB in M2 (updating tv-hero), as fixed, reviewed commands.
 * Every step has a check; nothing here uninstalls or disables anything.
 */
public final class Steps {
    private Steps() { }

    static final String GUARD_LABEL = "tv-hero guard";

    /** True when Android has the guard (accessibility service) running, not only switched on. */
    public static boolean guardBound(AdbClient adb) throws IOException {
        return adb.shell("dumpsys accessibility | grep -c '" + GUARD_LABEL + "'").trim().matches("[1-9][0-9]*");
    }

    /**
     * Android 9 can leave the guard unbound after an update until it's switched off and on again
     * (LEARNINGS 2.2.0). Keeps every other accessibility service the TV has.
     */
    public static void rebindGuard(AdbClient adb) throws IOException {
        String now = adb.shell("settings get secure enabled_accessibility_services").trim();
        List<String> others = new ArrayList<>();
        if (!now.isEmpty() && !now.equals("null")) {
            for (String s : now.split(":")) {
                if (!safeComponent(s)) throw new AdbClient.AdbException("Unexpected accessibility setting on the TV; not changed.");
                if (!s.startsWith(Probe.TV_PACKAGE + "/")) others.add(s);
            }
        }
        List<String> with = new ArrayList<>(others);
        with.add(Probe.GUARD);
        adb.shell("settings put secure enabled_accessibility_services '" + String.join(":", others) + "'");
        adb.shell("sleep 2");
        adb.shell("settings put secure enabled_accessibility_services '" + String.join(":", with) + "'");
        adb.shell("settings put secure accessibility_enabled 1");
    }

    /**
     * Lets tv-hero read Android's own app-usage record, the most accurate source for screen time (2.9).
     * Reversible (appops set ... default); returns whether the TV now reports it allowed. A TV that refuses
     * keeps working: the guard falls back to window changes.
     */
    public static boolean grantUsageAccess(AdbClient adb) throws IOException {
        adb.shell("appops set " + Probe.TV_PACKAGE + " GET_USAGE_STATS allow");
        return Probe.usageAllowed(adb.shell("appops get " + Probe.TV_PACKAGE + " GET_USAGE_STATS"));
    }

    /** Debugging off with the normal switch (never the port property: LEARNINGS 2.3.0). Drops this connection. */
    public static void debuggingOff(AdbClient adb) {
        try {
            adb.shell("settings put global adb_enabled 0");
        } catch (IOException expected) {
            // adbd stops while answering; the connection closing is the sign it worked
        }
    }

    // ---------- setting up a new TV, and starting over (Phase 3) ----------

    /** Launchers tv-hero may switch off when it becomes Home; never Google TV's (it gets Guard only). */
    static final String[] STOCK_HOMES = {"com.google.android.tvlauncher", "com.google.android.leanbacklauncher",
            "com.mitv.tvhome", "com.mitv.tvhome.atv"};

    public interface Report {
        void step(String what);
    }

    /** What a setup did, so the phone can tell the person (and undo it later). */
    public static final class Result {
        public String disabledHome = "";   // the stock launcher switched off, if any
        public boolean home, guard, mediaTitles;
        public String note = "";
    }

    /**
     * Installs tv-hero on a TV that doesn't have it (or updates it) and sets it up the way tools/install-tv.sh
     * does, every step checked: start it, grant its two permissions, make it Home (Full level only, putting the
     * old Home back if that fails), switch on the guard and media titles, and make sure the guard runs.
     */
    public static Result setUp(AdbClient adb, Probe.Result probe, java.io.InputStream apk, long length,
                               AdbClient.Progress progress, Report report) throws IOException {
        Result r = new Result();
        if (probe.sdk < 26) throw new AdbClient.AdbException("This TV runs Android " + probe.release + "; tv-hero needs Android 8 or later.");
        report.step("Installing tv-hero");
        String out = adb.install(apk, length, progress);
        if (!out.startsWith("Success")) throw new AdbClient.AdbException("The TV didn't install tv-hero: " + out);

        report.step("Starting tv-hero");
        String start = adb.shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n " + Probe.TV_PACKAGE + "/.MainActivity");
        if (!start.contains("Status: ok")) throw new AdbClient.AdbException("tv-hero didn't start on the TV. Nothing else was changed.");

        report.step("Giving tv-hero its permissions");
        adb.shell("pm grant " + Probe.TV_PACKAGE + " android.permission.WRITE_SECURE_SETTINGS");
        adb.shell("pm grant " + Probe.TV_PACKAGE + " android.permission.ACCESS_FINE_LOCATION");
        grantUsageAccess(adb);

        if (probe.level() == Probe.Level.FULL && !probe.tvHeroIsHome()) {
            report.step("Making tv-hero the Home screen");
            String stock = "";
            for (String s : STOCK_HOMES) if (probe.home.startsWith(s + "/")) stock = s;
            if (!stock.isEmpty()) adb.shell("pm disable-user --user 0 " + stock);
            adb.shell("cmd package set-home-activity --user 0 " + Probe.TV_PACKAGE + "/.MainActivity");
            String home = lastLine(adb.shell(HOME));
            if (home.startsWith(Probe.TV_PACKAGE + "/")) {
                r.home = true;
                r.disabledHome = stock;
            } else {
                // Put the old Home back: the TV must always have a working Home.
                if (!stock.isEmpty()) adb.shell("pm enable --user 0 " + stock);
                if (safeComponent(probe.home)) adb.shell("cmd package set-home-activity --user 0 " + probe.home);
                r.note = "The TV kept its own Home screen; tv-hero's guard still runs (Guard only).";
            }
        } else if (probe.tvHeroIsHome()) {
            r.home = true;
        } else {
            r.note = "This TV keeps its own Home screen (Google TV): profiles, limits and lock screens still work.";
        }

        report.step("Switching on the guard");
        rebindGuard(adb);
        String titles = adb.shell("cmd notification allow_listener " + Probe.TV_PACKAGE + "/" + Probe.TV_PACKAGE + ".MediaListener");
        r.mediaTitles = !titles.toLowerCase(java.util.Locale.ROOT).contains("error");
        for (int i = 0; i < 4 && !r.guard; i++) {
            adb.shell("sleep 3");
            r.guard = guardBound(adb);
            if (!r.guard) rebindGuard(adb);
        }
        if (!r.guard) r.note = (r.note.isEmpty() ? "" : r.note + " ") + "Restart the TV once so Android starts the guard.";
        return r;
    }

    static final String HOME = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME";

    /**
     * Starting over: puts the TV's own Home back, takes the guard out of the accessibility list and uninstalls
     * tv-hero (its profiles, pairings and settings go with it). Debugging stays on, so the phone can set the
     * TV up again straight away; the person is told to switch it off otherwise. Disabled bloat stays disabled
     * (tools/restore.sh puts it back).
     */
    public static void remove(AdbClient adb, Report report) throws IOException {
        report.step("Putting the TV's own Home screen back");
        java.util.Set<String> disabled = new java.util.HashSet<>();
        for (String line : adb.shell("pm list packages -d").split("\n")) disabled.add(line.trim());
        String stockActivity = "";
        for (String s : STOCK_HOMES) {
            if (disabled.contains("package:" + s)) adb.shell("pm enable --user 0 " + s);
            String a = lastLine(adb.shell(HOME + " " + s));
            if (stockActivity.isEmpty() && a.startsWith(s + "/") && safeComponent(a)) stockActivity = a;
        }
        if (!stockActivity.isEmpty()) adb.shell("cmd package set-home-activity --user 0 " + stockActivity);

        report.step("Switching the guard off");
        String now = adb.shell("settings get secure enabled_accessibility_services").trim();
        List<String> others = new ArrayList<>();
        if (!now.isEmpty() && !now.equals("null")) {
            for (String s : now.split(":")) if (safeComponent(s) && !s.startsWith(Probe.TV_PACKAGE + "/")) others.add(s);
        }
        adb.shell("settings put secure enabled_accessibility_services '" + String.join(":", others) + "'");

        report.step("Removing tv-hero");
        String out = adb.shell("pm uninstall " + Probe.TV_PACKAGE).trim();
        if (!out.startsWith("Success")) throw new AdbClient.AdbException("The TV didn't remove tv-hero: " + out);
        if (!stockActivity.isEmpty()) adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
    }

    static String lastLine(String s) {
        String[] lines = s.trim().split("\n");
        return lines.length == 0 ? "" : lines[lines.length - 1].trim();
    }

    static boolean safeComponent(String s) {
        return s.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+");
    }
}
