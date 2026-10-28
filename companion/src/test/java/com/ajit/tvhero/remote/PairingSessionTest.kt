package com.ajit.tvhero.remote

import com.ajit.tvhero.protocol.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The phone's half of pairing: it must refuse to show a code when the TV broke its commitment. */
class PairingSessionTest {
    private val phone = "phone-cert".toByteArray()
    private val tv = "tv-cert".toByteArray()
    private val np = ByteArray(32) { 1 }
    private val nt = ByteArray(32) { 2 }

    @Test fun showsTheSharedVectorCode() {
        val s = PairingSession("p", phone, tv, Pairing.commitment(tv, phone, nt), passwordSet = true, myNonce = np)
        assertEquals("569378", s.code(nt))   // same vector as protocol/PairingTest and tools/tvhero_client.py
        assertEquals(Pairing.fingerprint(tv), s.tvFingerprint)
    }

    @Test fun refusesATvThatChangedItsNonce() {
        val s = PairingSession("p", phone, tv, Pairing.commitment(tv, phone, nt), passwordSet = true, myNonce = np)
        try {
            s.code(ByteArray(32) { 9 })
            fail("a TV that changes its nonce after seeing ours must be refused")
        } catch (e: TvException) {
            assertTrue(e.message!!.contains("didn't match"))
        }
    }

    @Test fun refusesASwappedCertificate() {
        // Someone in the middle answers with their own certificate but the real TV's commitment.
        val s = PairingSession("p", phone, "evil-cert".toByteArray(), Pairing.commitment(tv, phone, nt), passwordSet = true, myNonce = np)
        try { s.code(nt); fail("swapped certificate must be refused") } catch (_: TvException) { }
    }
}
