package com.ajit.tvhero.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Vectors produced by tools/tvhero_client.py, the client already proven against the real TV. */
class TvProtocolTest {
    private val secret = ByteArray(32) { it.toByte() }

    @Test fun signsPostLikeTheTv() {
        assertEquals("wSysIg3xXHudyCveb17YEqwN2SGqEyoVWg+M3fIUgl0=",
            TvProtocol.sign(secret, "POST", "/v1/apps/block", "1700000000000", """{"package":"com.example"}""".toByteArray()))
    }

    @Test fun signsGetWithQuery() {
        assertEquals("KLVonO4lyoykTF8HacSohqC8x76UsJ4gyYAo8y1fTXY=",
            TvProtocol.sign(secret, "GET", "/v1/apps/icon?package=in.startv.hotstar", "1700000000001", ByteArray(0)))
    }

    @Test fun opensPairingBox() {
        val out = TvProtocol.pairingSecret("123456", "parent-pw", "AAAAAAAAAAAAAAAAAAAAAA==",
            "BwcHBwcHBwcHBwcH5qHVNueNdxjsBAtrnTk/NUPkJBIJRAhOG7U5CpQMiTOreddmQhUn6z78ttljeQxp")
        assertArrayEquals(secret, out)
    }

    @Test fun mapsTextToKeyboardUsages() {
        assertEquals(0x04 to false, HidController.charUsage('a'))
        assertEquals(0x04 to true, HidController.charUsage('A'))
        assertEquals(0x27 to false, HidController.charUsage('0'))
        assertEquals(0x1F to true, HidController.charUsage('@'))
    }

    @Test fun descriptorDeclaresThreeReports() {
        val d = HidController.DESCRIPTOR
        val ids = (0 until d.size - 1).filter { d[it] == 0x85.toByte() }.map { d[it + 1].toInt() }
        assertEquals(listOf(1, 2, 3), ids)
    }
}
