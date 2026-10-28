package com.ajit.tvhero.remote

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * App lock: fingerprint (or the phone's own screen lock as a fallback) when the app opens, and
 * again after 30 seconds in the background. Anyone holding an unlocked phone still can't control the TV.
 */
object AppLock {
    const val RELOCK_AFTER_MS = 30_000L

    // Android 9-10 can't combine "strong biometric" with the screen-lock fallback; "weak" is the widest it allows.
    private val authenticators = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** False when the phone has no fingerprint and no screen lock: there is nothing to lock with. */
    fun available(a: FragmentActivity): Boolean =
        BiometricManager.from(a).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(a: FragmentActivity, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock tv-hero")
            .setSubtitle("Controls for your TV and your family's rules")
            .setAllowedAuthenticators(authenticators)
            .build())
    }
}
