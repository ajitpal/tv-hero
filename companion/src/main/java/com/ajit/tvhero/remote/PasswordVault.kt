package com.ajit.tvhero.remote

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Fingerprint for the parent password (2.11). The password a TV accepted is kept encrypted with an Android
 * Keystore key that only a strong biometric can unlock (BiometricPrompt with a CryptoObject). The key is
 * invalidated when a new fingerprint is enrolled, which also forgets the password. Nothing leaves the
 * phone; the TV still checks the password every time.
 */
object PasswordVault {
    private const val KEY = "tvhero_parent_password"
    private const val STORE = "AndroidKeyStore"

    private fun prefs(c: Context) = c.getSharedPreferences("vault", Context.MODE_PRIVATE)

    /** Does this phone have a strong biometric (a fingerprint) set up? */
    fun available(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun saved(c: Context, tvId: String): Boolean = prefs(c).contains("ct_$tvId")

    fun forget(c: Context, tvId: String? = null) {
        val e = prefs(c).edit()
        if (tvId == null) e.clear() else e.remove("ct_$tvId").remove("iv_$tvId")
        e.apply()
    }

    private fun key(create: Boolean): SecretKey? {
        val ks = KeyStore.getInstance(STORE).apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val spec = KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply { if (Build.VERSION.SDK_INT >= 30) setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).apply { init(spec) }.generateKey()
    }

    private fun deleteKey() {
        runCatching { KeyStore.getInstance(STORE).apply { load(null) }.deleteEntry(KEY) }
    }

    private fun prompt(a: FragmentActivity, title: String, subtitle: String, negative: String, cipher: Cipher,
                       onCipher: (Cipher) -> Unit, onCancel: () -> Unit) {
        val p = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return onCancel()
                onCipher(c)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancel()
        })
        p.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setDescription(subtitle)
            .setNegativeButtonText(negative).setAllowedAuthenticators(BIOMETRIC_STRONG).build(),
            BiometricPrompt.CryptoObject(cipher))
    }

    /** Saves [password] for [tvId] behind the fingerprint (one prompt). [done] says whether it was saved. */
    fun save(a: FragmentActivity, tvId: String, password: String, done: (Boolean) -> Unit) {
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(create = true)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey(); forget(a)
            runCatching { Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(create = true)) } }.getOrNull()
        } catch (e: Exception) {
            null
        } ?: return done(false)
        prompt(a, "Use Fingerprint for Parent Password", "Save the parent password on this phone", "Cancel", cipher, { c ->
            val ok = runCatching {
                val ct = c.doFinal(password.toByteArray(Charsets.UTF_8))
                prefs(a).edit().putString("ct_$tvId", Base64.encodeToString(ct, Base64.NO_WRAP))
                    .putString("iv_$tvId", Base64.encodeToString(c.iv, Base64.NO_WRAP)).apply()
            }.isSuccess
            done(ok)
        }, { done(false) })
    }

    /**
     * Asks for the fingerprint and gives the saved password to [onPassword]. "Use Password", a failure or a
     * key invalidated by a new fingerprint goes to [onFallback], where the password is typed.
     */
    fun unlock(a: FragmentActivity, tvId: String, reason: String, onPassword: (String) -> Unit, onFallback: () -> Unit) {
        val p = prefs(a)
        val ct = p.getString("ct_$tvId", null)
        val iv = p.getString("iv_$tvId", null)
        if (ct == null || iv == null) return onFallback()
        val cipher = try {
            val k = key(create = false) ?: run { forget(a); return onFallback() }
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteKey(); forget(a)   // a new fingerprint was added: the password must be typed again
            return onFallback()
        } catch (e: Exception) {
            return onFallback()
        }
        prompt(a, "Parent Password", reason, "Use Password", cipher, { c ->
            val pw = runCatching { String(c.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8) }.getOrNull()
            if (pw == null) onFallback() else onPassword(pw)
        }, onFallback)
    }
}
