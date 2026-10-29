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

    /** Debugging off with the normal switch (never the port property: LEARNINGS 2.3.0). Drops this connection. */
    public static void debuggingOff(AdbClient adb) {
        try {
            adb.shell("settings put global adb_enabled 0");
        } catch (IOException expected) {
            // adbd stops while answering; the connection closing is the sign it worked
        }
    }

    static boolean safeComponent(String s) {
        return s.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+");
    }
}
