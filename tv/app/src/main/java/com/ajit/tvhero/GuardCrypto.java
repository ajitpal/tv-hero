package com.ajit.tvhero;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Small crypto helpers shared by pairing and request signing. The phone app implements the
 * same functions (docs/PROTOCOL.md); test vectors live in tools/tvhero_client.py.
 */
final class GuardCrypto {
    static final SecureRandom RNG = new SecureRandom();
    /** Kept modest: the TV's CPU is slow and the password check must stay under a second. */
    static final int PBKDF2_ROUNDS = 20000;

    private GuardCrypto() { }

    static byte[] random(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    static byte[] pbkdf2(String password, byte[] salt) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ROUNDS, 256);
            return f.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] hmac(byte[] key, String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256Hex(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** AES-256-GCM, 12-byte IV prepended to the output. */
    static byte[] seal(byte[] key, byte[] plain) {
        try {
            byte[] iv = random(12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean equal(byte[] a, byte[] b) {
        return a != null && b != null && MessageDigest.isEqual(a, b);
    }

    static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    static byte[] unb64(String s) {
        try {
            return Base64.decode(s, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }
}
