package com.ajit.tvhero.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PairedTv(val id: String, val name: String, val host: String, val deviceId: String, val secret: ByteArray, val remoteHost: String = "")

/**
 * Paired TVs. The per-TV secret is encrypted with an AES key that never leaves the phone's
 * Android Keystore; only ciphertext is written to app storage.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    fun tvs(): List<PairedTv> {
        val arr = JSONArray(prefs.getString("tvs", "[]"))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            runCatching {
                PairedTv(o.getString("id"), o.getString("name"), o.getString("host"), o.getString("device_id"),
                    decrypt(TvProtocol.b64d(o.getString("secret"))), o.optString("remote_host", ""))
            }.getOrNull()
        }
    }

    fun save(tv: PairedTv) {
        val keep = tvs().filter { it.id != tv.id }
        write(keep + tv)
        prefs.edit().putString("last", tv.id).apply()
    }

    fun updateHost(id: String, host: String) {
        write(tvs().map { if (it.id == id) it.copy(host = host) else it })
    }

    fun updateRemoteHost(id: String, remote: String) {
        write(tvs().map { if (it.id == id) it.copy(remoteHost = remote) else it })
    }

    fun forget(id: String) = write(tvs().filter { it.id != id })

    fun last(): PairedTv? = tvs().firstOrNull { it.id == prefs.getString("last", null) } ?: tvs().firstOrNull()

    var deviceName: String
        get() = prefs.getString("device_name", android.os.Build.MODEL) ?: "Phone"
        set(v) = prefs.edit().putString("device_name", v).apply()

    private fun write(list: List<PairedTv>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host)
                .put("device_id", it.deviceId).put("secret", TvProtocol.b64e(encrypt(it.secret)))
                .put("remote_host", it.remoteHost))
        }
        prefs.edit().putString("tvs", arr.toString()).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    private fun decrypt(box: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, box, 0, 12))
        return c.doFinal(box, 12, box.size - 12)
    }

    companion object {
        private const val ALIAS = "tvhero-secrets"
    }
}
