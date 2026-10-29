package com.ajit.tvhero.remote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.ajit.tvhero.adb.AdbKeys
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec
import javax.security.auth.x500.X500Principal

/**
 * This phone's ADB key (Phase 3): RSA-2048 made inside the Android Keystore, so it can't be copied off
 * the phone. Separate from the TLS identity on purpose: it's only used while setting up or updating a TV,
 * and forgetting it doesn't affect pairing.
 */
object AdbIdentity {
    private const val ALIAS = "tvhero-adb-v1"

    @Synchronized
    fun signer(): AdbKeys.Signer {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
                    .setDigests(KeyProperties.DIGEST_NONE)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(X500Principal("CN=tv-hero phone adb"))
                    .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                    .build())
                generateKeyPair()
            }
        }
        val key = ks.getKey(ALIAS, null) as PrivateKey
        val pub = ks.getCertificate(ALIAS).publicKey as RSAPublicKey
        return object : AdbKeys.Signer {
            override fun publicKey(): RSAPublicKey = pub
            override fun sign(token: ByteArray): ByteArray = Signature.getInstance("NONEwithRSA").run {
                initSign(key)
                update(AdbKeys.toBeSigned(token))
                sign()
            }
        }
    }

    /** "Forget This TV" and app reset: the TV will ask to Allow a new key next time. */
    fun forget() {
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }
}
