package com.ajit.tvhero.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.atomic.AtomicReference;

public class AdbClientTest {
    private static AdbKeys.Signer signer() throws Exception {
        KeyPair kp = AdbKeysTest.key();
        return AdbKeys.software(kp.getPrivate(), (RSAPublicKey) kp.getPublic());
    }

    @Test public void newKeyWaitsForAllowThenSignsNextTime() throws Exception {
        try (FakeAdbd tv = new FakeAdbd()) {
            AdbKeys.Signer k = signer();
            AtomicReference<String> asked = new AtomicReference<>();
            try (AdbClient c = AdbClient.connect("127.0.0.1", tv.port(), k, "test", asked::set, 5000)) {
                assertEquals(AdbKeys.fingerprint(k.publicKey()), asked.get());
                assertTrue(c.banner().contains("Fake"));
            }
            asked.set(null);
            try (AdbClient c = AdbClient.connect("127.0.0.1", tv.port(), k, "test", asked::set, 5000)) {
                assertNull("a known key must not ask again", asked.get());
                assertEquals("ran: getprop ro.product.model\n", c.shell("getprop ro.product.model"));
                assertEquals("ran: id\n", c.shell("id"));   // a second stream on the same connection
            }
        }
    }

    @Test public void refusedKeyFails() throws Exception {
        try (FakeAdbd tv = new FakeAdbd()) {
            tv.allowNewKey = false;
            try {
                AdbClient.connect("127.0.0.1", tv.port(), signer(), "test", null, 2000).close();
                fail();
            } catch (java.io.IOException expected) { }
        }
    }

    @Test public void streamsAnInstall() throws Exception {
        try (FakeAdbd tv = new FakeAdbd()) {
            byte[] apk = new byte[300_000];
            new java.util.Random(3).nextBytes(apk);
            long[] last = {0};
            try (AdbClient c = AdbClient.connect("127.0.0.1", tv.port(), signer(), "test", null, 5000)) {
                assertEquals("Success", c.install(new ByteArrayInputStream(apk), apk.length, (s, t) -> last[0] = s));
            }
            assertArrayEquals(apk, tv.lastInstall);
            assertEquals(apk.length, last[0]);
        }
    }

    @Test public void neverConnectsOutsideTheHomeNetwork() throws Exception {
        for (String host : new String[]{"8.8.8.8", "100.101.12.7"}) {   // internet, Tailscale
            try {
                AdbClient.connect(host, 5555, signer(), "test", null, 1000).close();
                fail(host);
            } catch (AdbClient.AdbException e) {
                assertTrue(e.getMessage(), e.getMessage().contains("home network"));
            }
        }
    }

    @Test public void wirelessDebuggingTlsIsReportedClearly() throws Exception {
        try (FakeAdbd tv = new FakeAdbd()) {
            tv.stls = true;
            try {
                AdbClient.connect("127.0.0.1", tv.port(), signer(), "test", null, 1000).close();
                fail();
            } catch (AdbClient.AdbException e) {
                assertTrue(e.getMessage().contains("pairing code"));
            }
        }
    }

    @Test public void refusesOddCommands() throws Exception {
        try (FakeAdbd tv = new FakeAdbd();
             AdbClient c = AdbClient.connect("127.0.0.1", tv.port(), signer(), "test", null, 5000)) {
            try { c.shell("ls\nreboot"); fail(); } catch (IllegalArgumentException expected) { }
        }
    }
}
