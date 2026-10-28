package com.ajit.tvhero.protocol;

/**
 * The tv-hero control protocol, version 2 (docs/PROTOCOL.md).
 *
 * <p>Two ports on the TV:
 * <ul>
 *   <li>{@link #PAIRING_PORT} (plain HTTP, home network only): {@code GET /v1/info} and the pairing
 *       endpoints. Nothing on this port can control the TV.</li>
 *   <li>{@link #SECURE_PORT} (mutual TLS, both certificates pinned at pairing): every command.</li>
 * </ul>
 */
public final class Protocol {
    private Protocol() { }

    public static final int API_VERSION = 2;
    public static final int PAIRING_PORT = 8765;
    public static final int SECURE_PORT = 8766;
    public static final String NSD_TYPE = "_tvhero._tcp.";

    /** TLS settings both ends use. TLS 1.3 where the platform has it (phones), TLS 1.2 on Android 9 (the TV). */
    public static final String[] TLS_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};
    public static final String[] TLS_CIPHERS = {
            "TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
    };

    /** Endpoints. Open = pairing port; everything else needs the secure port and a paired certificate. */
    public static final class Path {
        private Path() { }
        // open (pairing port)
        public static final String INFO = "/v1/info";
        public static final String PAIR_START = "/v1/pair/start";
        public static final String PAIR_REVEAL = "/v1/pair/reveal";
        public static final String PAIR_STATUS = "/v1/pair/status";
        // secure port; a phone that was just allowed may call only this one until it succeeds
        public static final String PAIR_COMPLETE = "/v1/pair/complete";
        // Phase 1: the control loop
        public static final String STATUS = "/v1/status";
        public static final String POWER = "/v1/power";
        public static final String VOLUME = "/v1/volume";
        public static final String KEY = "/v1/key";
        public static final String LAUNCH = "/v1/launch";
        public static final String NOW = "/v1/now";
        // security
        public static final String DEVICES = "/v1/devices";
        public static final String DEVICES_REMOVE = "/v1/devices/remove";
        public static final String INTERNET = "/v1/internet";
        public static final String LOG = "/v1/log";
        public static final String PASSWORD = "/v1/password";
    }

    /** Pairing states reported by {@code GET /v1/pair/status}. */
    public static final class PairState {
        private PairState() { }
        public static final String NONE = "none";
        public static final String WAITING_TV = "waiting_tv";   // the TV shows Allow / Don't Allow and the code
        public static final String ALLOWED = "allowed";         // connect to the secure port and complete
        public static final String DENIED = "denied";
        public static final String EXPIRED = "expired";
    }
}
