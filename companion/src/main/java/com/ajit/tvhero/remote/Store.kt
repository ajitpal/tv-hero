package com.ajit.tvhero.remote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A paired TV. Nothing here is secret: the phone's private key stays in the Android Keystore
 * (see [Identity]), and [tvFingerprint] is the TV certificate pinned at pairing.
 */
data class PairedTv(
    val id: String,
    val name: String,
    val host: String,
    val deviceId: String,
    val tvFingerprint: String,
    val remoteHost: String = "",
)

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    init {
        // Pairings from protocol 1 (shared secret, no pinned certificate) can't be used any more.
        if (prefs.getInt("protocol", 1) < 2) {
            prefs.edit().remove("tvs").remove("last").putInt("protocol", 2).apply()
        }
    }

    fun tvs(): List<PairedTv> {
        val arr = JSONArray(prefs.getString("tvs", "[]"))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            runCatching {
                PairedTv(o.getString("id"), o.getString("name"), o.getString("host"), o.getString("device_id"),
                    o.getString("tv_fp"), o.optString("remote_host", ""))
            }.getOrNull()
        }
    }

    fun save(tv: PairedTv) {
        write(tvs().filter { it.id != tv.id } + tv)
        prefs.edit().putString("last", tv.id).apply()
    }

    fun updateHost(id: String, host: String) = write(tvs().map { if (it.id == id) it.copy(host = host) else it })

    fun updateRemoteHost(id: String, remote: String) = write(tvs().map { if (it.id == id) it.copy(remoteHost = remote) else it })

    fun forget(id: String) = write(tvs().filter { it.id != id })

    fun last(): PairedTv? = tvs().firstOrNull { it.id == prefs.getString("last", null) } ?: tvs().firstOrNull()

    var deviceName: String
        get() = prefs.getString("device_name", android.os.Build.MODEL) ?: "Phone"
        set(v) = prefs.edit().putString("device_name", v).apply()

    /** App lock: ask for fingerprint / screen lock when the app opens. On by default. */
    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", true)
        set(v) = prefs.edit().putBoolean("app_lock", v).apply()

    private fun write(list: List<PairedTv>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host)
                .put("device_id", it.deviceId).put("tv_fp", it.tvFingerprint).put("remote_host", it.remoteHost))
        }
        prefs.edit().putString("tvs", arr.toString()).apply()
    }
}
