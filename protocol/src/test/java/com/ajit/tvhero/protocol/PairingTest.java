package com.ajit.tvhero.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class PairingTest {
    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static byte[] filled(int v) {
        byte[] n = new byte[Pairing.NONCE_BYTES];
        java.util.Arrays.fill(n, (byte) v);
        return n;
    }

    /** Fixed vectors, also checked by tools/tvhero_client.py --self-test, so the Python client can't drift. */
    @Test public void vectors() {
        byte[] tv = b("tv-cert"), phone = b("phone-cert"), np = filled(1), nt = filled(2);
        assertEquals(VECTOR_COMMIT, Bytes.hex(Pairing.commitment(tv, phone, nt)));
        assertEquals(VECTOR_CODE, Pairing.code(phone, tv, np, nt));
    }

    // Computed independently in Python (tools/tvhero_client.py --self-test checks the same values).
    static final String VECTOR_COMMIT = "659fd388420840209fabe277ddbf358a44ad050367704cd77fc78285cbcd99a6";
    static final String VECTOR_CODE = "569378";

    @Test public void commitmentBindsEverything() {
        byte[] tv = b("tv"), phone = b("phone"), nt = filled(7);
        byte[] c = Pairing.commitment(tv, phone, nt);
        assertTrue(Pairing.checkCommitment(c, tv, phone, nt));
        assertFalse(Pairing.checkCommitment(c, b("evil-tv"), phone, nt));
        assertFalse(Pairing.checkCommitment(c, tv, b("evil-phone"), nt));
        assertFalse(Pairing.checkCommitment(c, tv, phone, filled(8)));
        assertFalse(Pairing.checkCommitment(c, tv, phone, new byte[3]));
    }

    @Test public void codeIsSixDigitsAndChangesWithAnyInput() {
        String c = Pairing.code(b("p"), b("t"), filled(1), filled(2));
        assertTrue(c.matches("[0-9]{6}"));
        assertNotEquals(c, Pairing.code(b("p2"), b("t"), filled(1), filled(2)));
        assertNotEquals(c, Pairing.code(b("p"), b("t"), filled(3), filled(2)));
        assertEquals("042 917", Pairing.display("042917"));
    }

    /** Length prefixes: moving bytes from one part to the next must change the hash. */
    @Test public void partsCannotBeShifted() {
        assertNotEquals(Bytes.hex(Pairing.hash("x", b("ab"), b("c"))), Bytes.hex(Pairing.hash("x", b("a"), b("bc"))));
    }

    @Test public void networkClassification() {
        assertEquals(Net.Via.HOME, Net.classify("192.168.0.42"));
        assertEquals(Net.Via.HOME, Net.classify("::ffff:192.168.0.42"));
        assertEquals(Net.Via.HOME, Net.classify("10.1.2.3"));
        assertEquals(Net.Via.HOME, Net.classify("172.20.0.1"));
        assertEquals(Net.Via.HOME, Net.classify("fe80::1%wlan0"));
        assertEquals(Net.Via.INTERNET, Net.classify("172.32.0.1"));
        assertEquals(Net.Via.INTERNET, Net.classify("100.101.12.7"));
        assertEquals(Net.Via.INTERNET, Net.classify("8.8.8.8"));
        assertEquals(Net.Via.INTERNET, Net.classify("2001:db8::1"));
        assertEquals(Net.Via.INTERNET, Net.classify(""));
        assertTrue(Net.isTailscale("100.101.12.7"));
        assertFalse(Net.isTailscale("100.128.0.1"));
    }
}
