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

data class Kid(
    val id: String,
    val name: String,
    val color: Int,
    val allowed: Set<String>,
    val dailyLimit: Int,        // minutes, 0 = none
    val bedtimeOn: Boolean,
    val bedStart: Int,          // minutes after midnight
    val bedEnd: Int,
    val usedToday: Int = 0,
    val extraToday: Int = 0,
    val minutesLeft: Int = -1,  // -1 = no limit
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("color", color)
        .put("allowed", JSONArray(allowed.toList())).put("daily_limit", dailyLimit)
        .put("bedtime_on", bedtimeOn).put("bedtime_start", bedStart).put("bedtime_end", bedEnd)

    companion object {
        fun from(o: JSONObject) = Kid(
            o.optString("id"), o.optString("name"), o.optInt("color"),
            o.optJSONArray("allowed")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            o.optInt("daily_limit"), o.optBoolean("bedtime_on"), o.optInt("bedtime_start", 21 * 60),
            o.optInt("bedtime_end", 7 * 60), o.optInt("used_today"), o.optInt("extra_today"), o.optInt("minutes_left", -1),
        )
    }
}

data class Family(
    val kids: List<Kid>,
    val active: String,         // "parent" or a kid id
    val onWake: String,         // "ask", "parent" or a kid id
    val pinSet: Boolean,
    val locked: Boolean,
    val parentUsed: Int,
    val sleepAt: Long,
    val maxKids: Int,
)

data class NowPlaying(
    val screenOn: Boolean,
    val profile: String,
    val profileName: String,
    val pkg: String,
    val label: String,
    val title: String,
    val since: Long,
    val overlay: String,
    val minutesLeft: Int,
    val locked: Boolean,
    val sleepAt: Long,
)

data class Session(val profile: String, val pkg: String, val label: String, val title: String, val start: Long, val end: Long)

data class RemoteAccess(
    val adbEnabled: Boolean,
    val adbPort: String,
    val addresses: List<String>,
    val tailscaleInstalled: Boolean,
    val tailscaleIp: String,
)

/**
 * Talks to one TV: first on the home Wi-Fi ([host]), then through the internet over Tailscale
 * ([remoteHost]) when the home address doesn't answer. All calls run on the IO dispatcher.
 */
class TvClient(
    val host: String,
    private val deviceId: String? = null,
    private val secret: ByteArray? = null,
    val remoteHost: String? = null,
) {
    /** The address that answered last ("home" or "internet"), for the UI. */
    @Volatile var via: String = "home"
        private set
    @Volatile private var preferRemote = false

    // ---------- pairing (unsigned) ----------

    suspend fun info(): JSONObject = request("GET", "/v1/info", null, signed = false)

    suspend fun pairStart(deviceName: String): JSONObject =
        request("POST", "/v1/pair/start", JSONObject().put("device_name", deviceName), signed = false)

    /** Returns (deviceId, secret). */
    suspend fun pairFinish(deviceName: String, code: String, password: String, saltB64: String): Pair<String, ByteArray> {
        val r = request("POST", "/v1/pair/finish",
            JSONObject().put("device_name", deviceName).put("code", code).put("password", password), signed = false)
        val secret = withContext(Dispatchers.Default) { TvProtocol.pairingSecret(code, password, saltB64, r.getString("box")) }
        return r.getString("device_id") to secret
    }

    // ---------- apps ----------

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

    suspend fun icon(pkg: String): Bitmap? = try {
        val bytes = raw("GET", "/v1/apps/icon?package=" + URLEncoder.encode(pkg, "UTF-8"), null, true)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    // ---------- home screen ----------

    suspend fun home(): HomeLayout = parseHome(request("GET", "/v1/home"))

    suspend fun saveHome(h: HomeLayout): HomeLayout = parseHome(request("POST", "/v1/home", JSONObject()
        .put("dock_apps", JSONArray(h.dock)).put("hidden_apps", JSONArray(h.hidden)).put("columns", h.columns)
        .put("greeting_name", h.greeting).put("show_greeting", h.showGreeting)))

    // ---------- family ----------

    suspend fun family(): Family = parseFamily(request("GET", "/v1/profiles"))

    suspend fun saveKid(k: Kid, password: String): Family =
        parseFamily(request("POST", "/v1/profiles/save", JSONObject().put("profile", k.json()).put("password", password))
            .getJSONObject("profiles"))

    suspend fun deleteKid(id: String, password: String): Family =
        parseFamily(request("POST", "/v1/profiles/delete", JSONObject().put("id", id).put("password", password))
            .getJSONObject("profiles"))

    suspend fun activate(id: String, password: String?): Family {
        val b = JSONObject().put("id", id)
        if (password != null) b.put("password", password)
        return parseFamily(request("POST", "/v1/profiles/activate", b))
    }

    suspend fun extraTime(id: String, minutes: Int, password: String): Family = parseFamily(request("POST", "/v1/profiles/extra",
        JSONObject().put("id", id).put("minutes", minutes).put("password", password)))

    suspend fun setOnWake(value: String, password: String): Family =
        parseFamily(request("POST", "/v1/profiles/on_wake", JSONObject().put("on_wake", value).put("password", password)))

    suspend fun setPin(pin: String, password: String): Family =
        parseFamily(request("POST", "/v1/pin", JSONObject().put("pin", pin).put("password", password)))

    suspend fun lock(locked: Boolean, password: String?): Family {
        val b = JSONObject().put("locked", locked)
        if (password != null) b.put("password", password)
        return parseFamily(request("POST", "/v1/lock", b))
    }

    suspend fun sleepIn(minutes: Int, now: Boolean = false) {
        request("POST", "/v1/sleep", JSONObject().put("minutes", minutes).put("now", now))
    }

    suspend fun quiet(): JSONObject = request("GET", "/v1/quiet")
    suspend fun setQuiet(o: JSONObject): JSONObject = request("POST", "/v1/quiet", o)

    suspend fun now(): NowPlaying {
        val o = request("GET", "/v1/now")
        return NowPlaying(o.optBoolean("screen_on"), o.optString("profile"), o.optString("profile_name"),
            o.optString("package"), o.optString("label"), o.optString("title"), o.optLong("since"),
            o.optString("overlay"), o.optInt("minutes_left", -1), o.optBoolean("locked"), o.optLong("sleep_at"))
    }

    suspend fun activity(sinceMs: Long): Pair<List<Session>, Map<String, Int>> {
        val o = request("GET", "/v1/activity?since=$sinceMs")
        val labels = o.optJSONObject("labels") ?: JSONObject()
        val a = o.getJSONArray("sessions")
        val list = (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            val pkg = s.optString("package")
            Session(s.optString("profile"), pkg, labels.optString(pkg, pkg), s.optString("title"), s.optLong("start"), s.optLong("end"))
        }
        val usage = o.optJSONObject("usage_today") ?: JSONObject()
        return list to usage.keys().asSequence().associateWith { usage.optInt(it) }
    }

    suspend fun launch(pkg: String) { request("POST", "/v1/launch", JSONObject().put("package", pkg)) }
    suspend fun key(action: String) { request("POST", "/v1/key", JSONObject().put("action", action)) }

    // ---------- account and remote access ----------

    suspend fun changePassword(old: String, new: String) {
        request("POST", "/v1/password", JSONObject().put("password", old).put("new_password", new))
    }

    suspend fun devices(): JSONArray = request("GET", "/v1/devices").getJSONArray("devices")

    suspend fun removeDevice(id: String, password: String) {
        request("POST", "/v1/devices/remove", JSONObject().put("device_id", id).put("password", password))
    }

    suspend fun log(): JSONArray = request("GET", "/v1/log").getJSONArray("log")

    suspend fun remoteAccess(): RemoteAccess = parseRemote(request("GET", "/v1/remote"))

    suspend fun setAdb(enabled: Boolean, password: String): RemoteAccess =
        parseRemote(request("POST", "/v1/adb", JSONObject().put("enabled", enabled).put("password", password)))

    // ---------- parsing ----------

    private fun parseHome(o: JSONObject) = HomeLayout(
        o.getJSONArray("dock_apps").strings(), o.getJSONArray("hidden_apps").strings(),
        o.optInt("columns", 6), o.optString("greeting_name", ""), o.optBoolean("show_greeting", true))

    private fun parseFamily(o: JSONObject): Family {
        val a = o.optJSONArray("kids") ?: JSONArray()
        return Family((0 until a.length()).map { Kid.from(a.getJSONObject(it)) }, o.optString("active", "parent"),
            o.optString("on_wake", "ask"), o.optBoolean("pin_set"), o.optBoolean("locked"),
            o.optInt("parent_used_today"), o.optLong("sleep_at"), o.optInt("max_kids", 10))
    }

    private fun parseRemote(o: JSONObject) = RemoteAccess(o.optBoolean("adb_enabled"), o.optString("adb_port"),
        o.optJSONArray("addresses")?.strings() ?: emptyList(), o.optBoolean("tailscale_installed"), o.optString("tailscale_ip"))

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    // ---------- plumbing ----------

    private suspend fun request(method: String, path: String, body: JSONObject? = null, signed: Boolean = true): JSONObject {
        val bytes = raw(method, path, body?.toString()?.toByteArray(Charsets.UTF_8), signed)
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    /** Home address first (fast fail), then the internet address; remembers which one worked. */
    private suspend fun raw(method: String, target: String, body: ByteArray?, signed: Boolean): ByteArray {
        val hosts = buildList {
            val remote = remoteHost?.takeIf { it.isNotBlank() && it != host }
            if (preferRemote && remote != null) { add(remote to "internet"); add(host to "home") }
            else { add(host to "home"); if (remote != null) add(remote to "internet") }
        }
        var last: TvException? = null
        for ((h, label) in hosts) {
            try {
                val out = once(h, method, target, body, signed, connectTimeout = if (hosts.size > 1 && label == "home") 2500 else 6000)
                via = label
                preferRemote = label == "internet"
                return out
            } catch (e: TvException) {
                if (e.status != 0) throw e   // the TV answered: don't retry elsewhere
                last = e
            }
        }
        throw TvException(0, if (remoteHost.isNullOrBlank()) "Can't reach the TV at $host. Is it on and on the same Wi-Fi?"
            else "Can't reach the TV at home or over the internet. Is it on, and is Tailscale on here and on the TV?")
    }

    private suspend fun once(h: String, method: String, target: String, body: ByteArray?, signed: Boolean, connectTimeout: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val data = body ?: ByteArray(0)
            val conn = (URL("http://$h:${TvProtocol.PORT}$target").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                this.connectTimeout = connectTimeout
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
                throw TvException(0, "Can't reach the TV.")
            } finally {
                conn.disconnect()
            }
        }
}
