package com.ajit.tvhero.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;

public class AdbKeysTest {
    static KeyPair key() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    @Test public void encodedKeyHasAndroidLayout() throws Exception {
        RSAPublicKey pub = (RSAPublicKey) key().getPublic();
        byte[] e = AdbKeys.encode(pub);
        assertEquals(4 + 4 + 256 + 256 + 4, e.length);
        ByteBuffer b = ByteBuffer.wrap(e).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(64, b.getInt());
        long n0inv = b.getInt() & 0xffffffffL;
        byte[] le = new byte[256];
        b.get(le);
        byte[] be = new byte[257];
        for (int i = 0; i < 256; i++) be[256 - i] = le[i];
        BigInteger n = new BigInteger(be);
        assertEquals(pub.getModulus(), n);
        // n0inv * n ≡ -1 (mod 2^32)
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        assertEquals(r32.subtract(BigInteger.ONE), BigInteger.valueOf(n0inv).multiply(n).mod(r32));
        b.position(4 + 4 + 256 + 256);
        assertEquals(65537, b.getInt());
    }

    @Test public void signatureVerifiesAsSha1OfToken() throws Exception {
        KeyPair kp = key();
        AdbKeys.Signer s = AdbKeys.software(kp.getPrivate(), (RSAPublicKey) kp.getPublic());
        byte[] token = new byte[20];
        new java.util.Random(1).nextBytes(token);
        byte[] sig = s.sign(token);
        Signature v = Signature.getInstance("NONEwithRSA");
        v.initVerify(kp.getPublic());
        v.update(AdbKeys.toBeSigned(token));
        assertTrue(v.verify(sig));
    }

    @Test public void fingerprintLooksLikeAndroids() throws Exception {
        String fp = AdbKeys.fingerprint((RSAPublicKey) key().getPublic());
        assertTrue(fp, fp.matches("([0-9A-F]{2}:){15}[0-9A-F]{2}"));
    }

    /** With TVHERO_ADBKEY=<path to adbkey>, our encoding must equal the adbkey.pub that adb itself wrote. */
    @Test public void matchesAdbsOwnPublicKeyFile() throws Exception {
        String path = System.getenv("TVHERO_ADBKEY");
        Assume.assumeTrue(path != null && new File(path + ".pub").exists());
        AdbKeys.Signer s = AdbKeys.fromPem(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.US_ASCII));
        String pub = new String(Files.readAllBytes(new File(path + ".pub").toPath()), StandardCharsets.US_ASCII).trim();
        assertArrayEquals(pub.split(" ")[0].getBytes(StandardCharsets.US_ASCII),
                java.util.Base64.getEncoder().encodeToString(AdbKeys.encode(s.publicKey())).getBytes(StandardCharsets.US_ASCII));
    }
}
