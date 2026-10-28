package com.ajit.tvhero.protocol;

import java.util.Base64;

/** Hex and base64 without android.util, so this module stays plain Java. */
public final class Bytes {
    private Bytes() { }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String hex(byte[] b) {
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[i * 2] = HEX[(b[i] >> 4) & 0xf];
            out[i * 2 + 1] = HEX[b[i] & 0xf];
        }
        return new String(out);
    }

    public static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    /** Null for anything that isn't valid base64. */
    public static byte[] unb64(String s) {
        if (s == null) return null;
        try {
            return Base64.getDecoder().decode(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
