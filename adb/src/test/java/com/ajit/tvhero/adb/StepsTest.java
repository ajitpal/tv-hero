package com.ajit.tvhero.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StepsTest {
    @Test public void componentCheckRejectsShellTricks() {
        assertTrue(Steps.safeComponent("com.ajit.tvhero/.GuardService"));
        assertTrue(Steps.safeComponent("com.example.a11y/com.example.a11y.Svc$Inner"));
        assertFalse(Steps.safeComponent("x/y'; reboot; '"));
        assertFalse(Steps.safeComponent("x y/z"));
    }

    private static Set<String> set(String... s) { return new HashSet<>(Arrays.asList(s)); }

    /** 2.11.2: the TV must always keep a Home of its own to fall back to (the black-screen bug). */
    @Test public void fallbackHomeIsSwitchedBackOn() {
        Set<String> installed = set("com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.mitv.tvhome.atv", "com.ajit.tvhero");
        // A TV set up before 2.11.2: the stock launcher and PatchWall are off. Google's comes back first.
        assertEquals(Arrays.asList("com.google.android.tvlauncher", "com.mitv.tvhome.atv"),
                Steps.fallbackCandidates(installed, set("com.google.android.tvlauncher", "com.mitv.tvhome.atv"), Collections.emptySet()));
        // One is already on and is a Home: nothing to switch on.
        assertEquals(Collections.emptyList(),
                Steps.fallbackCandidates(installed, set("com.mitv.tvhome.atv"), set("com.google.android.tvlauncher")));
        // Nothing installed to fall back to.
        assertEquals(Collections.emptyList(), Steps.fallbackCandidates(set("com.ajit.tvhero"), set(), Collections.emptySet()));
    }

    @Test public void readsPackageLists() {
        assertEquals(set("com.a", "com.b.c"), Steps.packages("package:com.a\r\npackage:com.b.c\n\nnot a line"));
    }
}
