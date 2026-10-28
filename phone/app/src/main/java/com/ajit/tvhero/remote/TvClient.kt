package com.ajit.tvhero.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** An error the TV reported (or a network failure), with a message fit to show a person. */
class TvException(val status: Int, message: String) : Exception(message)

data class TvApp(
    val pkg: String,
    val label: String,
    val state: String,          // allowed | blocked | pending
    val system: Boolean,
    val protected: Boolean,
)

data class HomeLayout(
    val dock: List<String>,
    val hidden: List<String>,
    val columns: Int,
    val greeting: String,
    val showGreeting: Boolean,
)

/** Talks to one TV. All calls are suspend functions running on the IO dispatcher. */
class TvClient(val host: String, private val deviceId: String? = null, private val secret: ByteArray? = null) {

    private val base = "http://$host:${TvProtocol.PORT}"

    // ---------- pairing (unsigned) ----------

    suspend fun info(): JSONObject = request("GET", "/v1/info", null, signed = false)

    suspend fun pairStart(deviceName: String): JSONObject =
        request("POST", "/v1/pair/start", JSONObject().put("device_name", deviceName), signed = false)

    /** Returns (deviceId, secret). */
    suspend fun pairFinish(deviceName: String, code: String, password: String, saltB64: String): Pair<String, ByteArray> {
        val r = request("POST", "/v1/pair/finish",
            JSONObject().put("device_name", deviceName).put("code", code).put("password", password), signed = false)
        val secret = withContext(Dispatchers.Default) {
            TvProtocol.pairingSecret(code, password, saltB64, r.getString("box"))
        }
        return r.getString("device_id") to secret
    }

    // ---------- signed calls ----------

    suspend fun apps(): Pair<List<TvApp>, Boolean> {
        val r = request("GET", "/v1/apps")
        val arr = r.getJSONArray("apps")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TvApp(o.getString("package"), o.optString("label", o.getString("package")), o.getString("state"),
                o.optBoolean("system"), o.optBoolean("protected"))
        }
        return list to r.optBoolean("block_new_installs", true)
    }

    suspend fun block(pkg: String) { request("POST", "/v1/apps/block", JSONObject().put("package", pkg)) }

    suspend fun allow(pkg: String, password: String, approve: Boolean) {
        request("POST", if (approve) "/v1/apps/approve" else "/v1/apps/unblock",
            JSONObject().put("package", pkg).put("password", password))
    }

    suspend fun setBlockNewInstalls(on: Boolean, password: String?) {
        val b = JSONObject().put("block_new_installs", on)
        if (password != null) b.put("password", password)
        request("POST", "/v1/settings", b)
    }

    suspend fun home(): HomeLayout = parseHome(request("GET", "/v1/home"))

    suspend fun saveHome(h: HomeLayout): HomeLayout = parseHome(request("POST", "/v1/home", JSONObject()
        .put("dock_apps", JSONArray(h.dock))
        .put("hidden_apps", JSONArray(h.hidden))
        .put("columns", h.columns)
        .put("greeting_name", h.greeting)
        .put("show_greeting", h.showGreeting)))

    suspend fun launch(pkg: String) { request("POST", "/v1/launch", JSONObject().put("package", pkg)) }

    suspend fun key(action: String) { request("POST", "/v1/key", JSONObject().put("action", action)) }

    suspend fun changePassword(old: String, new: String) {
        request("POST", "/v1/password", JSONObject().put("password", old).put("new_password", new))
    }

    suspend fun devices(): JSONArray = request("GET", "/v1/devices").getJSONArray("devices")

    suspend fun removeDevice(id: String, password: String) {
        request("POST", "/v1/devices/remove", JSONObject().put("device_id", id).put("password", password))
    }

    suspend fun log(): JSONArray = request("GET", "/v1/log").getJSONArray("log")

    suspend fun icon(pkg: String): Bitmap? = try {
        val bytes = raw("GET", "/v1/apps/icon?package=" + URLEncoder.encode(pkg, "UTF-8"), null, true)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    // ---------- plumbing ----------

    private fun parseHome(o: JSONObject): HomeLayout = HomeLayout(
        dock = o.getJSONArray("dock_apps").let { a -> (0 until a.length()).map { a.getString(it) } },
        hidden = o.getJSONArray("hidden_apps").let { a -> (0 until a.length()).map { a.getString(it) } },
        columns = o.optInt("columns", 6),
        greeting = o.optString("greeting_name", ""),
        showGreeting = o.optBoolean("show_greeting", true),
    )

    private suspend fun request(method: String, path: String, body: JSONObject? = null, signed: Boolean = true): JSONObject {
        val bytes = raw(method, path, body?.toString()?.toByteArray(Charsets.UTF_8), signed)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    private suspend fun raw(method: String, target: String, body: ByteArray?, signed: Boolean): ByteArray =
        withContext(Dispatchers.IO) {
            val data = body ?: ByteArray(0)
            val conn = (URL(base + target).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 5000
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
                if (signed) {
                    val id = deviceId ?: throw TvException(401, "This phone isn't paired with the TV yet.")
                    val ts = System.currentTimeMillis().toString()
                    setRequestProperty("X-Device-Id", id)
                    setRequestProperty("X-Timestamp", ts)
                    setRequestProperty("X-Signature", TvProtocol.sign(secret!!, method, target, ts, data))
                }
                if (method == "POST") {
                    doOutput = true
                    setFixedLengthStreamingMode(data.size)
                }
            }
            try {
                if (method == "POST") conn.outputStream.use { it.write(data) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val out = stream?.use { it.readBytes() } ?: ByteArray(0)
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(String(out)).optString("error") }.getOrNull()
                    throw TvException(code, if (msg.isNullOrBlank()) "The TV answered $code." else msg.replaceFirstChar { it.uppercase() } + ".")
                }
                out
            } catch (e: IOException) {
                throw TvException(0, "Can't reach the TV at $host. Is it on and on the same Wi-Fi?")
            } finally {
                conn.disconnect()
            }
        }
}
