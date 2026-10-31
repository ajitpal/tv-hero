package com.ajit.tvhero.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.10 Phone to TV, phone side: live typing sends only what changed; photos are sent at the TV's size. */
class PhoneToTvTest {
    @Test fun typingALetterSendsOneLetter() {
        assertEquals(TypingDiff(0, "o"), TypingDiff.of("hell", "hello"))
        assertEquals(TypingDiff(0, "hello"), TypingDiff.of("", "hello"))
        assertTrue(TypingDiff.of("same", "same").empty)
    }

    @Test fun deletingSendsBackspaces() {
        assertEquals(TypingDiff(1, ""), TypingDiff.of("hello", "hell"))
        assertEquals(TypingDiff(5, ""), TypingDiff.of("hello", ""))
        // A word changed in the middle: delete back to the change, then type the rest.
        assertEquals(TypingDiff(4, "p dogs"), TypingDiff.of("cat cats", "cat p dogs"))
    }

    @Test fun emojiAndHindiCountAsOneCharacterEach() {
        assertEquals(TypingDiff(1, ""), TypingDiff.of("hi 😀", "hi "))         // one emoji, one backspace
        assertEquals(TypingDiff(1, "😁"), TypingDiff.of("😀", "😁"))   // same high surrogate: no half emoji
        assertEquals(TypingDiff(0, "क"), TypingDiff.of("नम", "नमक"))
    }

    @Test fun photosFitTheTvScreen() {
        assertEquals(1280 to 720, fitWithin(4000, 2250))
        assertEquals(960 to 720, fitWithin(4000, 3000))
        assertEquals(405 to 720, fitWithin(3024, 5376))   // portrait
        assertEquals(800 to 600, fitWithin(800, 600))     // never enlarged
        assertEquals(0 to 0, fitWithin(0, 10))
    }

    @Test fun sharedTextGivesTheWebLink() {
        assertEquals("https://youtu.be/abc", ShareActivity.webLink("Look: https://youtu.be/abc!"))
        assertNull(ShareActivity.webLink("no link here"))
        assertNull(ShareActivity.webLink("intent://x#Intent;end"))
        assertFalse(com.ajit.tvhero.remote.ui.isYouTubeLink("https://youtube.com.evil.example/x"))
        assertTrue(com.ajit.tvhero.remote.ui.isYouTubeLink("https://www.youtube.com/watch?v=1"))
    }
}
