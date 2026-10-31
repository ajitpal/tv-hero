package com.ajit.tvhero.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.11 on the phone: reading playlists (names, groups, http(s) only) and Free TV item ids. */
class LiveTvPhoneTest {
    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="dd.in" tvg-name="DD National" group-title="General, India",DD National
        https://example.org/dd/index.m3u8
        #EXTINF:-1 group-title="Kids",Toon Time
        #EXTVLCOPT:http-user-agent=Something
        http://example.org/toon.m3u8
        #EXTINF:-1,Bad scheme
        rtmp://example.org/live
        #EXTINF:-1 tvg-name="From Attr",
        https://example.org/attr.m3u8
        #EXTGRP:News
        https://example.org/news.m3u8
    """.trimIndent()

    @Test fun readsChannels() {
        val ch = M3u.parse(playlist)
        assertEquals(4, ch.size)                                   // the rtmp:// one is skipped
        assertEquals(M3u.Channel("DD National", "General, India", "https://example.org/dd/index.m3u8"), ch[0])
        assertEquals("Kids", ch[1].group)
        assertEquals("From Attr", ch[2].name)                      // no name after the comma: tvg-name
        assertEquals("News", ch[3].group)
        assertEquals("news.m3u8", ch[3].name)                      // no #EXTINF: the file name
        assertEquals(2, M3u.parse(playlist, max = 2).size)
        assertTrue(M3u.parse("not a playlist").isEmpty())
    }

    @Test fun sameThingSameId() {
        val a = FreeTvItem.stream(M3u.Channel("A", "", "https://x/a.m3u8"))
        val b = FreeTvItem.stream(M3u.Channel("A renamed", "", "https://x/a.m3u8"))
        assertEquals(a.id, b.id)
        assertNotEquals(a.id, FreeTvItem.app("com.x", "X").id)
        assertEquals(16, a.id.length)
    }

    @Test fun citySearchResults() {
        val c = Geocoding.parse("""{"results":[{"name":"Delhi","admin1":"Delhi","country":"India","latitude":28.65,"longitude":77.23},
            {"name":"Pune","admin1":"Maharashtra","country":"India","latitude":18.52,"longitude":73.86},{"name":""}]}""")
        assertEquals(2, c.size)
        assertEquals("Delhi, India", c[0].label)
        assertEquals("Pune, Maharashtra, India", c[1].label)
        assertTrue(Geocoding.parse("{}").isEmpty())
    }
}
