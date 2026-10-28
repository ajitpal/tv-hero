package com.ajit.tvhero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BedtimeTest {
    private static Profiles.Kid kid(int start, int end) {
        Profiles.Kid k = new Profiles.Kid();
        k.bedtimeOn = true;
        k.bedStart = start;
        k.bedEnd = end;
        return k;
    }

    @Test public void windowPastMidnight() {
        Profiles.Kid k = kid(21 * 60, 7 * 60);
        assertTrue(k.inBedtime(21 * 60));
        assertTrue(k.inBedtime(23 * 60 + 59));
        assertTrue(k.inBedtime(0));
        assertTrue(k.inBedtime(6 * 60 + 59));
        assertFalse(k.inBedtime(7 * 60));
        assertFalse(k.inBedtime(20 * 60 + 59));
    }

    @Test public void windowSameDay() {
        Profiles.Kid k = kid(13 * 60, 15 * 60);
        assertTrue(k.inBedtime(14 * 60));
        assertFalse(k.inBedtime(15 * 60));
        assertFalse(k.inBedtime(12 * 60));
    }

    @Test public void minutesToBedtime() {
        Profiles.Kid k = kid(21 * 60, 7 * 60);
        assertEquals(5, k.minutesToBedtime(20 * 60 + 55));
        assertEquals(-1, k.minutesToBedtime(22 * 60));
        assertEquals(14 * 60, k.minutesToBedtime(7 * 60));
    }

    @Test public void offMeansNever() {
        Profiles.Kid k = kid(21 * 60, 7 * 60);
        k.bedtimeOn = false;
        assertFalse(k.inBedtime(22 * 60));
        assertEquals(-1, k.minutesToBedtime(20 * 60));
    }
}
