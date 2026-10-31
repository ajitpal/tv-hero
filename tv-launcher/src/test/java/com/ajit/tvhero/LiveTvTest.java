package com.ajit.tvhero;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

/** 2.11 Live and free TV: weather from Open-Meteo, the weather sky, and the festival line. */
public class LiveTvTest {
    private static final String ANSWER = "{\"latitude\":28.625,\"longitude\":77.25,\"timezone\":\"Asia/Kolkata\","
            + "\"current\":{\"time\":\"2026-10-31T10:00\",\"temperature_2m\":30.6,\"weather_code\":2,\"is_day\":1},"
            + "\"daily\":{\"time\":[\"2026-10-31\",\"2026-11-01\"],\"weather_code\":[2,61],"
            + "\"temperature_2m_max\":[32.4,29.5],\"temperature_2m_min\":[19.6,18.4]}}";

    @Test
    public void readsOpenMeteo() throws Exception {
        Weather.Reading r = Weather.parse("Delhi", ANSWER, 1000);
        assertEquals("Delhi", r.place);
        assertEquals(31, r.temp);
        assertEquals(2, r.code);
        assertTrue(r.day);
        assertEquals(32, r.todayMax);
        assertEquals(20, r.todayMin);
        assertEquals(30, r.tomorrowMax);   // 29.5 rounds up
        assertEquals(18, r.tomorrowMin);
        assertEquals(61, r.tomorrowCode);
        assertEquals("Today 32° / 20°  ·  Tomorrow 30° / 18°, rain", r.outlook());
        assertNull(Weather.parse("Delhi", "{\"error\":true}", 0));
        assertNull(Weather.parse("Delhi", "not json", 0));
        // Round trip through the saved form.
        Weather.Reading back = Weather.Reading.from(new org.json.JSONObject(r.json().toString()));
        assertEquals(r.tomorrowCode, back.tomorrowCode);
        assertEquals(r.place, back.place);
    }

    @Test
    public void sendsOnlyTheCitysPosition() {
        String u = Weather.url(28.6139, 77.2090);
        assertTrue(u.startsWith("https://api.open-meteo.com/v1/forecast?latitude=28.61&longitude=77.21&"));
        assertFalse(u.contains("apikey"));
        assertTrue(Weather.validPosition(28.6, 77.2));
        assertFalse(Weather.validPosition(91, 0));
        assertFalse(Weather.validPosition(Double.NaN, 0));
    }

    @Test
    public void wmoCodesBecomeSkies() {
        assertEquals(Weather.Sky.CLEAR, Weather.sky(0));
        assertEquals(Weather.Sky.PARTLY, Weather.sky(2));
        assertEquals(Weather.Sky.FOG, Weather.sky(45));
        assertEquals(Weather.Sky.DRIZZLE, Weather.sky(53));
        assertEquals(Weather.Sky.RAIN, Weather.sky(63));
        assertEquals(Weather.Sky.RAIN, Weather.sky(81));
        assertEquals(Weather.Sky.SNOW, Weather.sky(75));
        assertEquals(Weather.Sky.STORM, Weather.sky(95));
        assertEquals("Showers", Weather.describe(80));
    }

    @Test
    public void fetchesHourlyAndBacksOff() {
        long h = 60 * 60_000L;
        assertTrue(Weather.due(10 * h, 0, 0, 0));             // never fetched
        assertFalse(Weather.due(10 * h, (long) (9.5 * h), (long) (9.5 * h), 0));   // fetched 30 min ago
        assertTrue(Weather.due(10 * h, 9 * h, 9 * h, 0));
        assertEquals(15 * 60_000L, Weather.backoff(1));
        assertEquals(30 * 60_000L, Weather.backoff(2));
        assertEquals(2 * h, Weather.backoff(10));
        assertFalse(Weather.due(10 * h, 10 * h - 10 * 60_000L, 0, 1));   // 10 min after a failure: wait
        assertTrue(Weather.due(10 * h, 10 * h - 16 * 60_000L, 0, 1));
        Weather.Reading r = Weather.parse("Delhi", ANSWER, 0);
        assertFalse(Weather.stale(r, 5 * h));
        assertTrue(Weather.stale(r, 7 * h));
        assertTrue(Weather.stale(null, 0));
    }

    @Test
    public void weatherGreysTheSky() {
        int[] day = {0xFF061830, 0xFF0F3F6E, 0xFF13697F};
        assertArrayEquals(day, ClockScene.skyWith(day, null));
        assertArrayEquals(day, ClockScene.skyWith(day, Weather.Sky.CLEAR));
        assertNotEquals(day[1], ClockScene.skyWith(day, Weather.Sky.RAIN)[1]);
        // A storm is darker than rain.
        int rain = ClockScene.skyWith(day, Weather.Sky.RAIN)[2], storm = ClockScene.skyWith(day, Weather.Sky.STORM)[2];
        assertTrue((storm & 0xFF) < (rain & 0xFF));
        assertEquals(0xFF808080, ClockScene.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
    }

    @Test
    public void festivalLine() {
        assertEquals("Diwali in 8 days", Festivals.line(LocalDate.of(2026, 10, 31)));
        assertEquals("Tomorrow: Diwali", Festivals.line(LocalDate.of(2026, 11, 7)));
        assertEquals("Happy Diwali", Festivals.line(LocalDate.of(2026, 11, 8)));
        assertEquals("diwali", Festivals.skin(LocalDate.of(2026, 11, 8)));
        assertEquals("", Festivals.skin(LocalDate.of(2026, 11, 9)));
        assertEquals("Republic Day", Festivals.line(LocalDate.of(2027, 1, 26)));
        assertEquals("Merry Christmas", Festivals.line(LocalDate.of(2026, 12, 25)));
        // Days of remembrance show only on the day, not as a countdown.
        assertEquals("", Festivals.line(LocalDate.of(2026, 6, 20)));
        assertEquals("Muharram", Festivals.line(LocalDate.of(2026, 6, 26)));
        // Two on one day.
        assertEquals("Independence Day  ·  Happy Milad-un-Nabi", Festivals.line(LocalDate.of(2027, 8, 15)));
        assertEquals("", Festivals.line(LocalDate.of(2028, 6, 1)));
        assertTrue(Festivals.defaultOn("Asia/Kolkata"));
        assertFalse(Festivals.defaultOn("Europe/London"));
        assertEquals(LocalDate.of(2027, 12, 25), Festivals.coveredUntil());
    }

    @Test
    public void festivalTableIsInOrder() {
        for (int i = 1; i < Festivals.INDIA.size(); i++) {
            assertFalse(Festivals.INDIA.get(i).date.isBefore(Festivals.INDIA.get(i - 1).date));
        }
        assertEquals(34, Festivals.INDIA.size());   // 17 gazetted holidays a year
    }

    @Test
    public void freeTvItemsAreChecked() throws Exception {
        org.json.JSONObject ok = new org.json.JSONObject().put("id", "abc123").put("kind", "stream").put("title", " DD National ")
                .put("url", "https://example.org/dd.m3u8").put("group", "India");
        FreeTv.Item i = FreeTv.check(ok);
        assertEquals("DD National", i.title);
        assertNull(FreeTv.check(new org.json.JSONObject(ok.toString()).put("url", "rtmp://example.org/x")));
        assertNull(FreeTv.check(new org.json.JSONObject(ok.toString()).put("url", "javascript:alert(1)")));
        assertNull(FreeTv.check(new org.json.JSONObject(ok.toString()).put("kind", "youtube")));   // not a YouTube address
        assertNull(FreeTv.check(new org.json.JSONObject(ok.toString()).put("kind", "app").put("package", "not a package")));
        assertNull(FreeTv.check(new org.json.JSONObject(ok.toString()).put("title", "")));
        assertNull(FreeTv.check(new org.json.JSONObject(ok.toString()).put("kind", "other")));
        assertEquals("com.example.tv", FreeTv.check(new org.json.JSONObject(ok.toString()).put("kind", "app").put("package", "com.example.tv")).pkg);
    }

    @Test
    public void childrenSeeOnlyTickedItems() {
        java.util.List<FreeTv.Item> all = java.util.Arrays.asList(
                new FreeTv.Item("a", "stream", "A", "", "https://x/a", ""), new FreeTv.Item("b", "app", "B", "com.b", "", ""));
        assertEquals(2, FreeTv.visible(all, Profiles.PARENT, java.util.Collections.emptySet()).size());
        assertEquals(0, FreeTv.visible(all, "kid1", java.util.Collections.emptySet()).size());
        assertEquals("b", FreeTv.visible(all, "kid1", java.util.Collections.singleton("b")).get(0).id);
        assertTrue(FreeTv.adds(java.util.Collections.singleton("a"), new java.util.HashSet<>(java.util.Arrays.asList("a", "b"))));
        assertFalse(FreeTv.adds(new java.util.HashSet<>(java.util.Arrays.asList("a", "b")), java.util.Collections.singleton("a")));   // removing needs no password
    }
}
