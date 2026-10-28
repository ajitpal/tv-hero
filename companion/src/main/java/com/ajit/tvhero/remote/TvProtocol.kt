package com.ajit.tvhero.remote

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The crypto half of the tv-hero protocol (docs/PROTOCOL.md). Pure JVM, so it is unit-tested
 * against the same vectors as the TV and tools/tvhero_client.py.
 */
object TvProtocol {
    const val PORT = 8765
    const val SERVICE_TYPE = "_tvhero._tcp"
    private const val ROUNDS = 20000

    fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ROUNDS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    /** Opens the pairing box: AES-256-GCM, 12-byte IV first. */
    fun openBox(key: ByteArray, box: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, box, 0, 12))
        return c.doFinal(box, 12, box.size - 12)
    }

    fun pairingSecret(code: String, password: String, saltB64: String, boxB64: String): ByteArray =
        openBox(pbkdf2("$code:$password", b64d(saltB64)), b64d(boxB64))

    /** X-Signature: HMAC-SHA256 over "METHOD\nPATH?QUERY\nTIMESTAMP\nsha256hex(body)". */
    fun sign(secret: ByteArray, method: String, target: String, timestamp: String, body: ByteArray): String {
        val msg = "$method\n$target\n$timestamp\n${sha256Hex(body)}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return b64e(mac.doFinal(msg.toByteArray(Charsets.UTF_8)))
    }

    fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    fun b64e(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun b64d(s: String): ByteArray = Base64.getDecoder().decode(s)
}
