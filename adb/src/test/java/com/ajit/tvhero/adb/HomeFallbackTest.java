package com.ajit.tvhero.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 2.11.2, the black-screen bug: a TV whose own launcher was switched off by an older setup gets it back as a
 * fallback, and tv-hero stays Home. Replayed against a fake TV that keeps package state like the real one.
 */
public class HomeFallbackTest {
    private static final String HERO = "com.ajit.tvhero/.MainActivity", STOCK = "com.google.android.tvlauncher";

    /** A TV: which packages are installed and disabled, who is the chosen Home, and whether the stock one grabs it. */
    private static final class Tv {
        final Set<String> installed = new HashSet<>(List.of("com.ajit.tvhero", STOCK, "com.google.android.leanbacklauncher", "com.mitv.tvhome.atv"));
        final Set<String> disabled = new HashSet<>();
        final Set<String> homes = new HashSet<>(List.of("com.ajit.tvhero", STOCK, "com.mitv.tvhome.atv"));   // leanback isn't a Home here
        String chosen = HERO;
        boolean stockGrabsHome;
        final List<String> log = Collections.synchronizedList(new ArrayList<>());

        synchronized String run(String cmd) {
            log.add(cmd);
            if (cmd.equals("pm list packages")) return list(installed);
            if (cmd.equals("pm list packages -d")) return list(disabled);
            if (cmd.startsWith("pm enable --user 0 ")) { disabled.remove(cmd.substring(19)); return "enabled\n"; }
            if (cmd.startsWith("pm disable-user --user 0 ")) { disabled.add(cmd.substring(25)); return "disabled\n"; }
            if (cmd.startsWith("cmd package set-home-activity --user 0 ")) { chosen = cmd.substring(39); return "Success\n"; }
            if (cmd.startsWith(Steps.HOME + " ")) {
                String pkg = cmd.substring(Steps.HOME.length() + 1);
                return homes.contains(pkg) && !disabled.contains(pkg) ? pkg + "/.Main\n" : "No activity found\n";
            }
            if (cmd.equals(Steps.HOME)) {
                if (stockGrabsHome && !disabled.contains(STOCK)) return STOCK + "/.Main\n";
                return chosen + "\n";
            }
            return "\n";
        }

        private static String list(Set<String> s) {
            StringBuilder b = new StringBuilder();
            for (String p : s) b.append("package:").append(p).append('\n');
            return b.toString();
        }
    }

    private static Steps.Fallback runOn(Tv tv) throws Exception {
        try (FakeAdbd d = new FakeAdbd()) {
            d.shell = tv::run;
            KeyPair kp = AdbKeysTest.key();
            AdbKeys.Signer k = AdbKeys.software(kp.getPrivate(), (RSAPublicKey) kp.getPublic());
            try (AdbClient c = AdbClient.connect("127.0.0.1", d.port(), k, "test", null, 5000)) {
                return Steps.keepFallbackHome(c);
            }
        }
    }

    @Test public void oldSetupGetsItsLauncherBack() throws Exception {
        Tv tv = new Tv();
        tv.disabled.add(STOCK);
        tv.disabled.add("com.mitv.tvhome.atv");
        Steps.Fallback f = runOn(tv);
        assertEquals(STOCK, f.enabled);
        assertTrue(f.safe);
        assertFalse(tv.disabled.contains(STOCK));             // Google's launcher is back on
        assertTrue(tv.disabled.contains("com.mitv.tvhome.atv")); // PatchWall (switched off as ads) stays off
        assertEquals(HERO, tv.chosen);                         // and tv-hero is still Home
    }

    @Test public void nothingToDoWhenALauncherIsOn() throws Exception {
        Tv tv = new Tv();
        Steps.Fallback f = runOn(tv);
        assertTrue(f.safe);
        assertEquals("", f.enabled);
        assertFalse(tv.log.stream().anyMatch(c -> c.startsWith("pm enable") || c.startsWith("pm disable")));
    }

    @Test public void firmwareThatGrabsHomeKeepsTvHeroAndSaysSo() throws Exception {
        Tv tv = new Tv();
        tv.disabled.add(STOCK);
        tv.disabled.add("com.mitv.tvhome.atv");
        tv.stockGrabsHome = true;
        Steps.Fallback f = runOn(tv);
        assertFalse(f.safe);
        assertTrue(tv.disabled.contains(STOCK));               // switched off again, so tv-hero stays Home
        assertTrue(f.note.contains("Start Over"));
    }

    @Test public void aLauncherThatIsntAHomeIsLeftAsItWas() throws Exception {
        Tv tv = new Tv();
        tv.installed.remove(STOCK);
        tv.installed.remove("com.mitv.tvhome.atv");
        tv.disabled.add("com.google.android.leanbacklauncher");
        Steps.Fallback f = runOn(tv);
        assertFalse(f.safe);
        assertTrue(tv.disabled.contains("com.google.android.leanbacklauncher"));
    }
}
