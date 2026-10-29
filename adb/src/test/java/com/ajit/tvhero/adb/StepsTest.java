package com.ajit.tvhero.adb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StepsTest {
    @Test public void componentCheckRejectsShellTricks() {
        assertTrue(Steps.safeComponent("com.ajit.tvhero/.GuardService"));
        assertTrue(Steps.safeComponent("com.example.a11y/com.example.a11y.Svc$Inner"));
        assertFalse(Steps.safeComponent("x/y'; reboot; '"));
        assertFalse(Steps.safeComponent("x y/z"));
    }
}
