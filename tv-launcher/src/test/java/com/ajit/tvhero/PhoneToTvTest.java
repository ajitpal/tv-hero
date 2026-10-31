package com.ajit.tvhero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 2.10 Phone to TV: which links open, where they go, safe search, the keyboard switch and photo sizes. */
public class PhoneToTvTest {

    @Test
    public void onlyWebLinksOpen() {
        assertEquals("https://example.com/a?b=1", LinkRules.webLink("https://example.com/a?b=1"));
        assertEquals("http://example.com", LinkRules.webLink("http://example.com"));
        assertNull(LinkRules.webLink("intent://scan/#Intent;scheme=zxing;end"));
        assertNull(LinkRules.webLink("file:///sdcard/secret.txt"));
        assertNull(LinkRules.webLink("javascript:alert(1)"));
        assertNull(LinkRules.webLink("content://contacts/people"));
        assertNull(LinkRules.webLink("just some words"));
        assertNull(LinkRules.webLink(null));
        assertNull(LinkRules.webLink("https://" + "a".repeat(LinkRules.MAX_URL)));
    }

    @Test
    public void linkIsFoundInsideSharedText() {
        assertEquals("https://youtu.be/abc123", LinkRules.webLink("Watch this! https://youtu.be/abc123."));
        assertEquals("https://example.com/x", LinkRules.webLink("(see https://example.com/x)"));
        assertEquals("https://a.com", LinkRules.webLink("first https://a.com then https://b.com"));
    }

    @Test
    public void youTubeLinksGoToYouTube() {
        assertTrue(LinkRules.isYouTube("https://www.youtube.com/watch?v=x"));
        assertTrue(LinkRules.isYouTube("https://youtu.be/x"));
        assertTrue(LinkRules.isYouTube("https://m.youtube.com/watch?v=x"));
        assertFalse(LinkRules.isYouTube("https://notyoutube.com/watch"));
        assertFalse(LinkRules.isYouTube("https://youtube.com.evil.example/"));
        assertEquals("example.com", LinkRules.host("https://WWW.Example.com/path"));
    }

    @Test
    public void searchesKeepSafeSearchOn() {
        assertEquals("https://duckduckgo.com/?q=lion+cubs&kp=1", LinkRules.searchUrl(LinkRules.DUCKDUCKGO, "lion cubs", true));
        assertEquals("https://duckduckgo.com/?q=lion+cubs&kp=-1", LinkRules.searchUrl(LinkRules.DUCKDUCKGO, "lion cubs", false));
        assertTrue(LinkRules.searchUrl(LinkRules.GOOGLE, "x", true).endsWith("&safe=active"));
        assertTrue(LinkRules.searchUrl(LinkRules.GOOGLE, "x", false).endsWith("&safe=active"));
        assertTrue(LinkRules.searchUrl(LinkRules.BING, "x", true).endsWith("&adlt=strict"));
        // An unknown engine falls back to DuckDuckGo, never to an unfiltered search.
        assertEquals("https://duckduckgo.com/?q=x&kp=1", LinkRules.searchUrl("something", "x", true));
        assertEquals("https://www.youtube.com/results?search_query=dino+songs", LinkRules.youTubeSearchUrl("  dino songs "));
        assertEquals("https://duckduckgo.com/?q=a%26b%3Dc&kp=1", LinkRules.searchUrl(LinkRules.DUCKDUCKGO, "a&b=c", true));
        assertEquals(LinkRules.MAX_QUERY, LinkRules.trimQuery("x".repeat(500)).length());
    }

    @Test
    public void keyboardIsAddedOnceToTheEnabledList() {
        String ours = "com.ajit.tvhero/.PhoneKeyboard";
        assertEquals(ours, PhoneTyping.withMethod(null, ours));
        assertEquals(ours, PhoneTyping.withMethod("", ours));
        assertEquals("com.google.android.leanback.ime/.LeanbackImeService:" + ours,
                PhoneTyping.withMethod("com.google.android.leanback.ime/.LeanbackImeService", ours));
        String already = "a/.B;123:" + ours + ";456";
        assertEquals(already, PhoneTyping.withMethod(already, ours));
    }

    @Test
    public void photosAreDecodedAtScreenSize() {
        assertEquals(1, PhotoActivity.sampleSize(1280, 720));
        assertEquals(1, PhotoActivity.sampleSize(1920, 1080));   // larger than the screen, but halving would go below it
        assertEquals(2, PhotoActivity.sampleSize(4000, 3000));
        assertEquals(1, PhotoActivity.sampleSize(720, 1280));     // portrait
    }
}
