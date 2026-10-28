package com.ajit.tvhero.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Pairing by numeric comparison, the same idea as Bluetooth's: each side has a long-term certificate,
 * and a person checks that the TV and the phone show the same 6-digit code before pressing Allow on the TV.
 *
 * <pre>
 *   phone -> TV   start   { phone certificate }
 *   TV -> phone           { TV certificate, commitment = H("commit", tvCert, phoneCert, Nt) }
 *   phone -> TV   reveal  { Np }                 the TV now shows the code with Allow / Don't Allow
 *   TV -> phone           { Nt }                 the phone checks the commitment and shows the code
 *   code = H("sas", phoneCert, tvCert, Np, Nt) mod 1 000 000
 * </pre>
 *
 * The TV commits to its nonce before it sees the phone's, so someone in the middle who swaps the
 * certificates can make the two screens match only by luck (1 in a million per attempt), and each
 * attempt needs a fresh press of Allow on the TV.
 */
public final class Pairing {
    private Pairing() { }

    public static final int NONCE_BYTES = 32;
    private static final SecureRandom RNG = new SecureRandom();

    public static byte[] nonce() {
        byte[] n = new byte[NONCE_BYTES];
        RNG.nextBytes(n);
        return n;
    }

    public static byte[] commitment(byte[] tvCert, byte[] phoneCert, byte[] tvNonce) {
        return hash("tvhero-commit-v1", tvCert, phoneCert, tvNonce);
    }

    public static boolean checkCommitment(byte[] commitment, byte[] tvCert, byte[] phoneCert, byte[] tvNonce) {
        return commitment != null && tvNonce != null && tvNonce.length == NONCE_BYTES
                && MessageDigest.isEqual(commitment, commitment(tvCert, phoneCert, tvNonce));
    }

    /** The 6-digit code both screens show, e.g. "042917". */
    public static String code(byte[] phoneCert, byte[] tvCert, byte[] phoneNonce, byte[] tvNonce) {
        byte[] h = hash("tvhero-sas-v1", phoneCert, tvCert, phoneNonce, tvNonce);
        long v = ((h[0] & 0xffL) << 24) | ((h[1] & 0xffL) << 16) | ((h[2] & 0xffL) << 8) | (h[3] & 0xffL);
        return String.format(Locale.ROOT, "%06d", v % 1_000_000L);
    }

    /** "042 917": easier to compare at a glance. */
    public static String display(String code) {
        return code.length() == 6 ? code.substring(0, 3) + " " + code.substring(3) : code;
    }

    /** SHA-256 of a certificate's DER bytes, lowercase hex: how each side names and pins the other. */
    public static String fingerprint(byte[] certDer) {
        return Bytes.hex(sha256(certDer));
    }

    /** Domain-separated hash with each part length-prefixed, so parts can't be shifted into each other. */
    static byte[] hash(String label, byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, label.getBytes(StandardCharsets.UTF_8));
        for (byte[] p : parts) writePart(out, p);
        return sha256(out.toByteArray());
    }

    private static void writePart(ByteArrayOutputStream out, byte[] p) {
        int n = p.length;
        out.write(n >>> 24); out.write(n >>> 16); out.write(n >>> 8); out.write(n);
        out.write(p, 0, n);
    }

    static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
