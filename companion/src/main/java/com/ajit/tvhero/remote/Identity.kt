package com.ajit.tvhero.remote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.ajit.tvhero.protocol.Tls
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Calendar
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

/**
 * This phone's identity towards every TV: an EC P-256 key made inside the Android Keystore (it
 * can't be exported) and the self-signed certificate the Keystore issues for it. A TV pins this
 * certificate's fingerprint when you press Allow; the phone pins the TV's.
 */
object Identity {
    private const val ALIAS = "tvhero-phone-tls-v1"

    class Keys(val key: PrivateKey, val cert: X509Certificate) {
        val der: ByteArray get() = cert.encoded
        val fingerprint: String get() = Tls.fingerprint(cert)
    }

    @Volatile private var cached: Keys? = null

    @Synchronized
    fun get(): Keys {
        cached?.let { return it }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val to = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setCertificateSubject(X500Principal("CN=tv-hero phone"))
                    .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                    .setCertificateNotBefore(from.time)
                    .setCertificateNotAfter(to.time)
                    .build())
                generateKeyPair()
            }
        }
        return Keys(ks.getKey(ALIAS, null) as PrivateKey, ks.getCertificate(ALIAS) as X509Certificate).also { cached = it }
    }

    /** TLS towards one TV: present our key, accept only the TV certificate pinned at pairing. */
    fun contextFor(tvFingerprint: String): SSLContext {
        val k = get()
        return Tls.context(k.key, k.cert) { it == tvFingerprint }
    }
}
