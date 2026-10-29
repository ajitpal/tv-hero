package com.ajit.tvhero.adb;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 3 capability probe: what this TV is and allows, read without changing anything. One shell
 * command with section markers, so it's a single round trip. The result picks the setup level and
 * which recipe steps apply.
 */
public final class Probe {
    public static final String TV_PACKAGE = "com.ajit.tvhero";
    public static final String GUARD = TV_PACKAGE + "/.GuardService";
    static final String GOOGLE_TV_HOME = "com.google.android.apps.tv.launcherx";

    public enum Level { FULL, GUARD_ONLY, REMOTE_ONLY, UNSUPPORTED }

    /** Read-only commands, each after an "@@name" marker. */
    static final String[][] SECTIONS = {
            {"props", "getprop"},
            {"home", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"},
            {"gtv", "pm list packages " + GOOGLE_TV_HOME},
            {"tvhero", "dumpsys package " + TV_PACKAGE + " | grep -E 'versionName=|versionCode=|WRITE_SECURE_SETTINGS'"},
            {"a11y", "settings get secure enabled_accessibility_services"},
            {"saver", "settings get secure screensaver_components"},
            {"idle", "settings get system screen_off_timeout"},
            {"adb", "settings get global adb_enabled"},
            {"mem", "grep MemTotal /proc/meminfo"},
            {"disk", "df /data | tail -1"},
    };

    public static String command() {
        StringBuilder s = new StringBuilder();
        for (String[] sec : SECTIONS) {
            if (s.length() > 0) s.append("; ");
            s.append("echo @@").append(sec[0]).append("; ").append(sec[1]).append(" 2>/dev/null");
        }
        return s.toString();
    }

    public static Result run(AdbClient adb) throws IOException {
        return parse(adb.shell(command()));
    }

    public static final class Result {
        public final Map<String, String> props = new LinkedHashMap<>();
        public int sdk;
        public String release = "", brand = "", manufacturer = "", model = "", device = "", build = "", abi = "";
        public boolean googleTv, lowRam;
        public String home = "";
        public String tvHeroVersion = "";      // empty: not installed
        public int tvHeroVersionCode;
        public boolean secureSettingsGranted, guardEnabled;
        public String screensaver = "";
        public int idleMinutes = -1;
        public boolean adbEnabled;
        public long ramMb, freeStorageMb = -1;

        public boolean tvHeroIsHome() { return home.startsWith(TV_PACKAGE + "/"); }

        /** The setup level this TV can reach, as far as reading can tell. */
        public Level level() {
            if (sdk < 28) return Level.UNSUPPORTED;
            if (googleTv && !tvHeroIsHome()) return Level.GUARD_ONLY;   // Full on Google TV only once proven per device
            return Level.FULL;
        }

        public String describe() {
            String name = (brand.isEmpty() ? manufacturer : brand) + " " + model;
            return name.trim() + " · Android " + release + " (API " + sdk + ")" + (googleTv ? " · Google TV" : "");
        }

        @Override public String toString() {
            return describe() + "\nlevel=" + level() + " home=" + home + " tv-hero=" + (tvHeroVersion.isEmpty() ? "no" : tvHeroVersion)
                    + " guard=" + guardEnabled + " secureSettings=" + secureSettingsGranted + " screensaver=" + screensaver
                    + " idleMin=" + idleMinutes + " adb=" + adbEnabled + " ramMb=" + ramMb + (lowRam ? " (low-RAM)" : "")
                    + " freeMb=" + freeStorageMb + " abi=" + abi + " build=" + build;
        }
    }

    public static Result parse(String out) {
        Map<String, StringBuilder> sec = new LinkedHashMap<>();
        StringBuilder cur = null;
        for (String line : out.replace("\r", "").split("\n")) {
            if (line.startsWith("@@")) { cur = new StringBuilder(); sec.put(line.substring(2).trim(), cur); continue; }
            if (cur != null) cur.append(line).append('\n');
        }
        Result r = new Result();
        for (String line : get(sec, "props").split("\n")) {
            // [ro.build.version.sdk]: [28]
            int a = line.indexOf("]: [");
            if (line.startsWith("[") && a > 0 && line.endsWith("]")) r.props.put(line.substring(1, a), line.substring(a + 4, line.length() - 1));
        }
        r.sdk = parseInt(r.props.get("ro.build.version.sdk"), 0);
        r.release = val(r.props, "ro.build.version.release");
        r.brand = val(r.props, "ro.product.brand");
        r.manufacturer = val(r.props, "ro.product.manufacturer");
        r.model = val(r.props, "ro.product.model");
        r.device = val(r.props, "ro.product.device");
        r.build = val(r.props, "ro.build.display.id");
        r.abi = val(r.props, "ro.product.cpu.abi");
        r.lowRam = "true".equals(r.props.get("ro.config.low_ram"));

        String[] home = get(sec, "home").trim().split("\n");
        String last = home.length == 0 ? "" : home[home.length - 1].trim();
        r.home = last.contains("/") ? last : "";
        r.googleTv = get(sec, "gtv").contains("package:" + GOOGLE_TV_HOME);

        for (String line : get(sec, "tvhero").split("\n")) {
            String t = line.trim();
            if (t.startsWith("versionName=")) r.tvHeroVersion = t.substring("versionName=".length()).trim();
            if (t.startsWith("versionCode=")) r.tvHeroVersionCode = parseInt(t.substring("versionCode=".length()).split(" ")[0], 0);
            if (t.contains("WRITE_SECURE_SETTINGS") && t.contains("granted=true")) r.secureSettingsGranted = true;
        }
        String a11y = get(sec, "a11y").trim();
        r.guardEnabled = a11y.contains(GUARD) || a11y.contains(TV_PACKAGE + "/" + TV_PACKAGE + ".GuardService");
        r.screensaver = clean(get(sec, "saver"));
        int ms = parseInt(clean(get(sec, "idle")), -1);
        r.idleMinutes = ms < 0 ? -1 : ms / 60_000;
        r.adbEnabled = "1".equals(clean(get(sec, "adb")));
        String mem = get(sec, "mem").replaceAll("[^0-9]", "");
        r.ramMb = mem.isEmpty() ? 0 : Long.parseLong(mem) / 1024;
        String[] df = get(sec, "disk").trim().split("\\s+");
        // Filesystem 1K-blocks Used Available Use% Mounted on (toybox prints 1K blocks)
        if (df.length >= 4) r.freeStorageMb = parseLong(df[3], -1) / 1024;
        return r;
    }

    private static String get(Map<String, StringBuilder> m, String k) {
        StringBuilder b = m.get(k);
        return b == null ? "" : b.toString();
    }

    private static String val(Map<String, String> m, String k) {
        String v = m.get(k);
        return v == null ? "" : v;
    }

    private static String clean(String s) {
        String t = s.trim();
        return "null".equals(t) ? "" : t;
    }

    private static int parseInt(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (RuntimeException e) { return dflt; }
    }

    private static long parseLong(String s, long dflt) {
        try { return Long.parseLong(s.trim()); } catch (RuntimeException e) { return dflt; }
    }

    static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
