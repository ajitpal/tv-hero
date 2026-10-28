package com.ajit.tvhero;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import com.ajit.tvhero.protocol.Tls;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Calendar;
import java.util.function.Predicate;

import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;

/**
 * The TV's long-term identity: an EC P-256 key made inside the Android Keystore (it can't be read
 * out, not even by this app) with the self-signed certificate the Keystore creates for it. Phones pin
 * this certificate's fingerprint at pairing.
 */
final class TlsIdentity {
    private static final String TAG = "tvhero.tls";
    private static final String ALIAS = "tvhero-tls-v1";

    final PrivateKey key;
    final X509Certificate cert;
    final String fingerprint;

    private TlsIdentity(PrivateKey key, X509Certificate cert) {
        this.key = key;
        this.cert = cert;
        this.fingerprint = Tls.fingerprint(cert);
    }

    private static TlsIdentity instance;

    static synchronized TlsIdentity get() throws Exception {
        if (instance != null) return instance;
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            Calendar from = Calendar.getInstance(), to = Calendar.getInstance();
            from.add(Calendar.DAY_OF_YEAR, -1);   // the TV's clock may be a little behind the phone's
            to.add(Calendar.YEAR, 25);
            KeyPairGenerator g = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            g.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    // TLS signs either a ready-made hash (NONE) or the handshake with SHA-2, depending on the stack.
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setCertificateSubject(new X500Principal("CN=tv-hero TV"))
                    .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                    .setCertificateNotBefore(from.getTime())
                    .setCertificateNotAfter(to.getTime())
                    .build());
            g.generateKeyPair();
            Log.i(TAG, "created the TV's TLS key");
        }
        PrivateKey key = (PrivateKey) ks.getKey(ALIAS, null);
        X509Certificate cert = (X509Certificate) ks.getCertificate(ALIAS);
        instance = new TlsIdentity(key, cert);
        return instance;
    }

    /** Server context: presents the TV's certificate, accepts a phone only if [paired] knows its fingerprint. */
    SSLContext serverContext(Predicate<String> paired) throws Exception {
        SSLContext ctx = Tls.context(key, cert, paired);
        // No session resumption: every connection runs a full handshake, so a removed phone is refused at once.
        ctx.getServerSessionContext().setSessionCacheSize(1);
        ctx.getServerSessionContext().setSessionTimeout(1);
        return ctx;
    }
}
