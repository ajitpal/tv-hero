package com.ajit.tvhero.protocol;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * Mutual TLS with pinned self-signed certificates. No certificate authorities: each side trusts
 * exactly the certificates it pinned at pairing, by SHA-256 fingerprint. The private keys live in
 * each device's Android Keystore and never leave it; this class only needs a handle to them.
 */
public final class Tls {
    private Tls() { }

    /** An SSLContext that presents [key]/[cert] and accepts a peer only when [trusted] says yes to its fingerprint. */
    public static SSLContext context(PrivateKey key, X509Certificate cert, Predicate<String> trusted) throws GeneralSecurityException {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(new KeyManager[]{new FixedKeyManager(key, cert)}, new TrustManager[]{new PinnedTrustManager(trusted)}, null);
        return ctx;
    }

    /** Only the protocol versions and cipher suites in {@link Protocol}, and only those this platform has. */
    public static void restrict(SSLSocket s) {
        s.setEnabledProtocols(pick(s.getSupportedProtocols(), Protocol.TLS_PROTOCOLS));
        s.setEnabledCipherSuites(pick(s.getSupportedCipherSuites(), Protocol.TLS_CIPHERS));
    }

    public static void restrict(SSLParameters p, String[] supportedProtocols, String[] supportedCiphers) {
        p.setProtocols(pick(supportedProtocols, Protocol.TLS_PROTOCOLS));
        p.setCipherSuites(pick(supportedCiphers, Protocol.TLS_CIPHERS));
    }

    static String[] pick(String[] supported, String[] wanted) {
        List<String> s = Arrays.asList(supported), out = new ArrayList<>();
        for (String w : wanted) if (s.contains(w)) out.add(w);
        if (out.isEmpty()) throw new IllegalStateException("no common TLS settings: " + Arrays.toString(supported));
        return out.toArray(new String[0]);
    }

    public static String fingerprint(X509Certificate c) {
        try {
            return Pairing.fingerprint(c.getEncoded());
        } catch (CertificateEncodingException e) {
            return "";
        }
    }

    /** Trusts a peer by the fingerprint of its leaf certificate, nothing else. Checked live, so a removal is instant. */
    public static final class PinnedTrustManager implements X509TrustManager {
        private final Predicate<String> trusted;

        public PinnedTrustManager(Predicate<String> trusted) { this.trusted = trusted; }

        private void check(X509Certificate[] chain) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("no certificate");
            chain[0].checkValidity();
            if (!trusted.test(fingerprint(chain[0]))) throw new CertificateException("certificate not paired");
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { check(chain); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { check(chain); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    /** Always presents the one key and certificate it was given (on Android: a Keystore key). */
    public static final class FixedKeyManager extends X509ExtendedKeyManager {
        private static final String ALIAS = "tvhero";
        private final PrivateKey key;
        private final X509Certificate cert;

        public FixedKeyManager(PrivateKey key, X509Certificate cert) { this.key = key; this.cert = cert; }

        @Override public String[] getClientAliases(String keyType, Principal[] issuers) { return new String[]{ALIAS}; }
        @Override public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) { return ALIAS; }
        @Override public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) { return ALIAS; }
        @Override public String[] getServerAliases(String keyType, Principal[] issuers) { return new String[]{ALIAS}; }
        @Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) { return ALIAS; }
        @Override public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) { return ALIAS; }
        @Override public X509Certificate[] getCertificateChain(String alias) { return new X509Certificate[]{cert}; }
        @Override public PrivateKey getPrivateKey(String alias) { return key; }
    }
}
