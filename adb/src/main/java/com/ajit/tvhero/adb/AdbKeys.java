package com.ajit.tvhero.adb;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * ADB's RSA authentication: the public key in Android's own format (what `adbkey.pub` holds, and what the
 * TV pins after "Always allow"), the signature over the TV's 20-byte token, and the fingerprint the TV
 * shows in its "Allow debugging?" prompt.
 */
public final class AdbKeys {
    private AdbKeys() { }

    /** Signs ADB tokens. On the phone the key lives in the Android Keystore and can't be read out. */
    public interface Signer {
        RSAPublicKey publicKey();

        /** PKCS#1 v1.5 signature of SHA-1 DigestInfo || token (ADB treats the token as a SHA-1 digest). */
        byte[] sign(byte[] token) throws GeneralSecurityException;
    }

    /** DER prefix of a SHA-1 DigestInfo; ADB's device side verifies with RSA_verify(NID_sha1, token, …). */
    static final byte[] SHA1_DIGEST_INFO = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    /** The bytes a "NONEwithRSA" (PKCS#1) signer must sign for [token]. */
    public static byte[] toBeSigned(byte[] token) {
        byte[] out = new byte[SHA1_DIGEST_INFO.length + token.length];
        System.arraycopy(SHA1_DIGEST_INFO, 0, out, 0, SHA1_DIGEST_INFO.length);
        System.arraycopy(token, 0, out, SHA1_DIGEST_INFO.length, token.length);
        return out;
    }

    /** A signer around a software key (tests, and the computer tool with an existing `adbkey`). */
    public static Signer software(PrivateKey key, RSAPublicKey pub) {
        return new Signer() {
            @Override public RSAPublicKey publicKey() { return pub; }

            @Override public byte[] sign(byte[] token) throws GeneralSecurityException {
                Signature s = Signature.getInstance("NONEwithRSA");
                s.initSign(key);
                s.update(toBeSigned(token));
                return s.sign();
            }
        };
    }

    /** Reads a PEM "PRIVATE KEY" (PKCS#8, as adb writes `adbkey`). */
    public static Signer fromPem(String pem) throws GeneralSecurityException {
        String b64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
        RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
        return software(key, pub);
    }

    private static final int KEY_BYTES = 256;   // RSA-2048, the only size adbd accepts

    /**
     * Android's RSAPublicKey struct (libcrypto_utils android_pubkey): modulus size in 32-bit words, n0inv,
     * modulus and R² mod n as little-endian byte arrays, then the exponent; all little-endian.
     */
    public static byte[] encode(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        if (n.bitLength() != KEY_BYTES * 8) throw new IllegalArgumentException("ADB needs an RSA-2048 key");
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(KEY_BYTES * 8 * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + KEY_BYTES + KEY_BYTES + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(KEY_BYTES / 4);
        b.putInt(n0inv.intValue());
        b.put(littleEndian(n));
        b.put(littleEndian(rr));
        b.putInt(key.getPublicExponent().intValue());
        return b.array();
    }

    /** "base64 name": the line adbkey.pub holds and the AUTH(RSAPUBLICKEY) payload (plus a NUL). */
    public static String publicKeyLine(RSAPublicKey key, String name) {
        return Base64.getEncoder().encodeToString(encode(key)) + " " + name;
    }

    /**
     * The fingerprint Android shows in "Allow debugging?": MD5 of the encoded public key, as AA:BB:…
     * The person compares it with the phone before pressing Allow.
     */
    public static String fingerprint(RSAPublicKey key) {
        try {
            byte[] md5 = MessageDigest.getInstance("MD5").digest(encode(key));
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < md5.length; i++) {
                if (i > 0) s.append(':');
                s.append(String.format("%02X", md5[i] & 0xff));
            }
            return s.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] ascii(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static byte[] littleEndian(BigInteger v) {
        byte[] be = v.toByteArray();
        byte[] le = new byte[KEY_BYTES];
        for (int i = 0; i < KEY_BYTES && i < be.length; i++) le[i] = be[be.length - 1 - i];
        return le;
    }
}
