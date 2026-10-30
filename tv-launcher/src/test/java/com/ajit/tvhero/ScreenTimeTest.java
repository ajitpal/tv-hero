package com.ajit.tvhero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Replays real sequences of window events and checks the seconds each profile and app gets (2.9). */
public class ScreenTimeTest {
    private static final String YT = "com.google.android.youtube.tv", HOTSTAR = "in.startv.hotstar",
            HOME = "com.ajit.tvhero", SYSTEMUI = "com.android.systemui", IME = "com.google.android.inputmethod.latin",
            SETTINGS = "com.android.tv.settings", DREAMS = "com.android.dreams.basic";
    private static final Set<String> APPS = Set.of(YT, HOTSTAR);

    private final Map<String, Integer> got = new HashMap<>();
    private ScreenTime t;

    @Before
    public void setUp() {
        got.clear();
        t = new ScreenTime((p, pkg, s) -> got.merge(p + "/" + pkg, s, Integer::sum), APPS::contains);
        t.setProfile(0, "kid");
    }

    private int sec(String profile, String pkg) { return got.getOrDefault(profile + "/" + pkg, 0); }

    private static long s(double seconds) { return (long) (seconds * 1000); }

    /** Ticks every 20 s from [from] to [to], like the guard. */
    private void play(long from, long to) {
        for (long x = from + 20_000; x < to; x += 20_000) t.tick(x);
        t.tick(to);
    }

    @Test
    public void volumeBarOverYouTubeKeepsCounting() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        play(0, s(30));
        t.onWindow(s(30), SYSTEMUI, ScreenTime.Window.SMALL);   // volume bar
        play(s(30), s(90));
        assertEquals(YT, t.front());
        assertEquals(90, sec("kid", YT));
        assertEquals(0, sec("kid", SYSTEMUI));
    }

    @Test
    public void keyboardInSearchBoxKeepsCounting() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        t.onWindow(s(5), IME, ScreenTime.Window.SMALL);
        play(0, s(65));
        assertEquals(65, sec("kid", YT));
    }

    @Test
    public void ourOwnCornerTimerDoesNotStealTheFront() {
        t.onWindow(0, HOTSTAR, ScreenTime.Window.APP);
        t.onWindow(s(10), HOME, ScreenTime.Window.SMALL);   // tools' corner status / time-left pill / toast
        play(0, s(120));
        assertEquals(HOTSTAR, t.front());
        assertEquals(120, sec("kid", HOTSTAR));
    }

    @Test
    public void homeAndSettingsNeverCount() {
        t.onWindow(0, HOME, ScreenTime.Window.APP);
        play(0, s(60));
        t.onWindow(s(60), SETTINGS, ScreenTime.Window.APP);
        play(s(60), s(120));
        assertTrue(got.isEmpty());
    }

    @Test
    public void switchingAppsMidIntervalSplitsBySecond() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        t.tick(s(20));
        t.onWindow(s(27), HOTSTAR, ScreenTime.Window.APP);   // 7 s into the next 20-s interval
        t.tick(s(40));
        assertEquals(27, sec("kid", YT));
        assertEquals(13, sec("kid", HOTSTAR));
    }

    @Test
    public void profileSwitchMidIntervalGoesToTheRightProfile() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        t.setProfile(s(12), "parent");
        t.tick(s(20));
        assertEquals(12, sec("kid", YT));
        assertEquals(8, sec("parent", YT));
    }

    @Test
    public void screensaverStopsAndResumes() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        t.setDream(s(30), true);   // our clock screensaver
        play(s(30), s(330));
        assertFalse(t.counting());
        t.setDream(s(330), false);
        play(s(330), s(360));
        assertEquals(60, sec("kid", YT));
    }

    @Test
    public void anotherScreensaverWindowStopsUntilAnAppComesBack() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        t.onWindow(s(40), DREAMS, ScreenTime.Window.DREAM);
        play(s(40), s(200));
        t.onWindow(s(200), YT, ScreenTime.Window.APP);   // woken: the app's window again
        play(s(200), s(220));
        assertEquals(60, sec("kid", YT));
        assertEquals(YT, t.front());
    }

    @Test
    public void standbyStopsCounting() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        t.tick(s(20));
        t.setScreenOn(s(25), false);
        t.tick(s(600));
        t.setScreenOn(s(900), true);
        t.tick(s(920));
        assertEquals(45, sec("kid", YT));
    }

    @Test
    public void lockScreenStopsCounting() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        t.setLockScreen(s(10), true);   // Time's up
        play(s(10), s(100));
        assertEquals(10, sec("kid", YT));
    }

    @Test
    public void subSecondPiecesAddUp() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        for (int i = 1; i <= 10; i++) t.tick(i * 700L);   // 7 s in 0.7-s steps
        assertEquals(7, sec("kid", YT));
    }

    @Test
    public void aFrozenGuardCountsAtMostAMinute() {
        t.onWindow(0, YT, ScreenTime.Window.APP);
        t.tick(s(3600));   // no update for an hour: the TV slept without telling us
        assertEquals(60, sec("kid", YT));
    }

    @Test
    public void usageRecordCorrectsTheFront() {
        t.onWindow(0, HOME, ScreenTime.Window.APP);
        play(0, s(10));
        assertTrue(t.setFront(s(10), YT));   // the window event was missed (guard reconnected), usage stats saw it
        play(s(10), s(70));
        assertEquals(60, sec("kid", YT));
    }

    @Test
    public void timesUpComesOnTheSecond() {
        assertEquals(20_000, ScreenTime.nextCheckMs(20_000, true, -1));      // no limit
        assertEquals(20_000, ScreenTime.nextCheckMs(20_000, true, 300));     // plenty left
        assertEquals(7_250, ScreenTime.nextCheckMs(20_000, true, 7));        // runs out in 7 s
        assertEquals(20_000, ScreenTime.nextCheckMs(20_000, false, 7));      // not counting: nothing runs out
        assertEquals(20_000, ScreenTime.nextCheckMs(20_000, true, 0));       // already up: the usual check shows it
        assertEquals(5_000, ScreenTime.nextCheckMs(5_000, true, 60));        // corner showing
    }

    @Test
    public void timeWarningsOffOnlyWhereTheCornerAlwaysShows() {
        assertTrue(OnScreen.timeWarnings(true, true, Profiles.CORNER_ALWAYS));
        assertFalse(OnScreen.timeWarnings(false, true, Profiles.CORNER_ALWAYS));
        assertTrue(OnScreen.timeWarnings(false, true, Profiles.CORNER_LAST5));
        assertTrue(OnScreen.timeWarnings(false, true, Profiles.CORNER_OFF));
        assertTrue(OnScreen.timeWarnings(false, false, Profiles.CORNER_ALWAYS));   // corner switched off for everyone
    }
}
