package com.ajit.tvhero.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.ajit.tvhero.protocol.Bytes
import com.ajit.tvhero.protocol.Protocol
import com.ajit.tvhero.protocol.Tls
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** An error the TV reported (or a network failure), with a message fit to show a person. */
class TvException(val status: Int, message: String, val refusedKey: Boolean = false) : Exception(message)

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
    /** Apps for everyone: on in every profile on top of its own apps. */
    val commonApps: Set<String> = emptySet(),
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

data class TvStatus(val power: String, val volume: Volume, val profile: String, val internetControl: Boolean)

data class Volume(val level: Int, val max: Int, val muted: Boolean, val cap: Int)

data class PairedPhone(
    val id: String,
    val name: String,
    val pairedAt: Long,
    val lastSeen: Long,
    val lastVia: String,
    val active: Boolean,
    val isThisPhone: Boolean,
)

data class InternetControl(val enabled: Boolean, val since: Long, val by: String, val pending: Boolean, val tailscaleIp: String)

data class KidRequest(val id: String, val profile: String, val profileName: String, val pkg: String, val label: String, val time: Long)

data class RemoteAccess(
    val adbEnabled: Boolean,
    val adbPort: String,
    val addresses: List<String>,
    val tailscaleInstalled: Boolean,
    val tailscaleIp: String,
)

/**
 * Talks to one TV (docs/PROTOCOL.md, protocol 2).
 *
 * Pairing calls go to the plain pairing port and can't control anything. Every other call goes
 * over mutual TLS to the secure port: the phone presents its Keystore key, and the TV must present
 * exactly the certificate pinned at pairing ([tvFingerprint]), or the connection is dropped.
 * The home address ([host]) is tried first, then the TV's Tailscale address ([remoteHost]).
 */
class TvClient(
    val host: String,
    private val tvFingerprint: String? = null,
    val remoteHost: String? = null,
) {
    /** The address that answered last ("home" or "internet"), for the UI. */
    @Volatile var via: String = "home"
        private set
    @Volatile private var preferRemote = false

    private val tlsFactory: SSLSocketFactory? by lazy {
        tvFingerprint?.let { RestrictedFactory(Identity.contextFor(it).socketFactory) }
    }

    // ---------- pairing (pairing port; nothing here can control the TV) ----------

    suspend fun info(): JSONObject = request("GET", "/v1/info", null, secure = false)

    /** Step 1: send our certificate; the TV answers with its own and a commitment. */
    suspend fun pairStart(deviceName: String): PairingSession {
        val me = withContext(Dispatchers.Default) { Identity.get() }
        val r = request("POST", Protocol.Path.PAIR_START,
            JSONObject().put("device_name", deviceName).put("cert", Bytes.b64(me.der)), secure = false)
        return PairingSession(r.getString("pair_id"), me.der, Bytes.unb64(r.getString("tv_cert")),
            Bytes.unb64(r.getString("commitment")), r.optBoolean("password_set", true))
    }

    /** Step 2: reveal our nonce, check the TV's, and return the code both screens show. */
    suspend fun pairReveal(p: PairingSession): String {
        val r = request("POST", Protocol.Path.PAIR_REVEAL,
            JSONObject().put("pair_id", p.pairId).put("nonce", Bytes.b64(p.myNonce)), secure = false)
        return p.code(Bytes.unb64(r.getString("nonce")) ?: ByteArray(0))
    }

    /** waiting_tv | allowed | denied | expired | none */
    suspend fun pairStatus(p: PairingSession): String =
        request("GET", Protocol.Path.PAIR_STATUS + "?pair_id=" + p.pairId, null, secure = false).optString("state")

    /** Step 3, on a client built with the TV's fingerprint: the parent password over the encrypted channel. */
    suspend fun pairComplete(password: String): String =
        request("POST", Protocol.Path.PAIR_COMPLETE, JSONObject().put("password", password)).getString("device_id")

    // ---------- the control loop ----------

    suspend fun status(): TvStatus {
        val o = request("GET", Protocol.Path.STATUS)
        return TvStatus(o.optString("power"), parseVolume(o.getJSONObject("volume")), o.optString("profile"), o.optBoolean("internet_control"))
    }

    suspend fun volume(): Volume = parseVolume(request("GET", Protocol.Path.VOLUME))
    suspend fun setVolume(level: Int): Volume = parseVolume(request("POST", Protocol.Path.VOLUME, JSONObject().put("level", level)))
    suspend fun stepVolume(step: Int): Volume = parseVolume(request("POST", Protocol.Path.VOLUME, JSONObject().put("step", step)))
    suspend fun mute(on: Boolean): Volume = parseVolume(request("POST", Protocol.Path.VOLUME, JSONObject().put("mute", on)))

    suspend fun power(action: String) { request("POST", Protocol.Path.POWER, JSONObject().put("action", action)) }

    // ---------- who can control this TV ----------

    suspend fun phones(): List<PairedPhone> {
        val o = request("GET", Protocol.Path.DEVICES)
        val you = o.optString("you")
        val a = o.getJSONArray("devices")
        return (0 until a.length()).map { i ->
            val d = a.getJSONObject(i)
            PairedPhone(d.getString("id"), d.optString("name"), d.optLong("paired_at"), d.optLong("last_seen"),
                d.optString("last_via", "home"), d.optBoolean("active"), d.getString("id") == you)
        }.sortedWith(compareBy({ !it.isThisPhone }, { -it.lastSeen }))
    }

    /** Removing this phone needs nothing; removing another phone needs the parent password. */
    suspend fun removePhone(id: String?, password: String?) {
        val b = JSONObject()
        if (id != null) b.put("device_id", id)
        if (password != null) b.put("password", password)
        request("POST", Protocol.Path.DEVICES_REMOVE, b)
    }

    suspend fun internet(): InternetControl = parseInternet(request("GET", Protocol.Path.INTERNET))

    /** Off works at once. On returns pending=true: the TV now asks someone to press Allow. */
    suspend fun setInternet(on: Boolean, password: String?): InternetControl {
        val b = JSONObject().put("enabled", on)
        if (password != null) b.put("password", password)
        return parseInternet(request("POST", Protocol.Path.INTERNET, b))
    }

    // ---------- kids asking for apps ----------

    suspend fun requests(): List<KidRequest> = parseRequests(request("GET", "/v1/requests"))

    suspend fun answerRequest(id: String, allow: Boolean, password: String?): List<KidRequest> {
        val b = JSONObject().put("id", id).put("allow", allow)
        if (password != null) b.put("password", password)
        return parseRequests(request("POST", "/v1/requests/answer", b))
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
        val bytes = raw("GET", "/v1/apps/icon?package=" + URLEncoder.encode(pkg, "UTF-8"), null, secure = true)
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

    suspend fun setCommonApps(apps: Set<String>, password: String): Family =
        parseFamily(request("POST", "/v1/profiles/common", JSONObject().put("apps", JSONArray(apps.toList())).put("password", password)))

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
            o.optInt("parent_used_today"), o.optLong("sleep_at"), o.optInt("max_kids", 10),
            o.optJSONArray("common_apps")?.strings()?.toSet() ?: emptySet())
    }

    private fun parseRemote(o: JSONObject) = RemoteAccess(o.optBoolean("adb_enabled"), o.optString("adb_port"),
        o.optJSONArray("addresses")?.strings() ?: emptyList(), o.optBoolean("tailscale_installed"), o.optString("tailscale_ip"))

    private fun parseVolume(o: JSONObject) = Volume(o.optInt("level"), o.optInt("max", 100), o.optBoolean("muted"), o.optInt("cap", o.optInt("max", 100)))

    private fun parseInternet(o: JSONObject) = InternetControl(o.optBoolean("enabled"), o.optLong("since"), o.optString("by"),
        o.optBoolean("pending"), o.optString("tailscale_ip"))

    private fun parseRequests(o: JSONObject): List<KidRequest> {
        val a = o.optJSONArray("requests") ?: JSONArray()
        return (0 until a.length()).map { i ->
            val r = a.getJSONObject(i)
            KidRequest(r.optString("id"), r.optString("profile"), r.optString("profile_name"), r.optString("package"),
                r.optString("label", r.optString("package")), r.optLong("t"))
        }
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    // ---------- plumbing ----------

    private suspend fun request(method: String, path: String, body: JSONObject? = null, secure: Boolean = true): JSONObject {
        val bytes = raw(method, path, body?.toString()?.toByteArray(Charsets.UTF_8), secure)
        return if (bytes.isEmpty()) JSONObject() else JSONObject(String(bytes, Charsets.UTF_8))
    }

    /** Home address first (fast fail), then the internet address; remembers which one worked. Pairing is home-only. */
    private suspend fun raw(method: String, target: String, body: ByteArray?, secure: Boolean): ByteArray {
        val hosts = buildList {
            val remote = remoteHost?.takeIf { secure && it.isNotBlank() && it != host }
            if (preferRemote && remote != null) { add(remote to "internet"); add(host to "home") }
            else { add(host to "home"); if (remote != null) add(remote to "internet") }
        }
        var last: TvException? = null
        for ((h, label) in hosts) {
            try {
                val out = once(h, method, target, body, secure, connectTimeout = if (hosts.size > 1 && label == "home") 2500 else 6000)
                via = label
                preferRemote = label == "internet"
                return out
            } catch (e: TvException) {
                if (e.status != 0) throw e   // the TV answered: don't retry elsewhere
                last = e
            }
        }
        throw last?.takeIf { it.refusedKey }
            ?: TvException(0, if (remoteHost.isNullOrBlank()) "Can't reach the TV at $host. Is it on and on the same Wi-Fi?"
            else "Can't reach the TV at home or over the internet. Is it on, and is Tailscale on here and on the TV?")
    }

    private suspend fun once(h: String, method: String, target: String, body: ByteArray?, secure: Boolean, connectTimeout: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val data = body ?: ByteArray(0)
            val url = if (secure) URL("https://${bracket(h)}:${Protocol.SECURE_PORT}$target")
                      else URL("http://${bracket(h)}:${Protocol.PAIRING_PORT}$target")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                if (this is HttpsURLConnection) {
                    sslSocketFactory = tlsFactory ?: throw TvException(401, "This phone isn't paired with the TV yet.")
                    hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }   // identity = the pinned certificate, not a name
                }
                requestMethod = method
                this.connectTimeout = connectTimeout
                readTimeout = 15000
                useCaches = false
                setRequestProperty("Content-Type", "application/json")
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
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                // The TV refused our key (this phone was removed), a different device answered, or the network
                // dropped mid-handshake. AppState decides which, by checking the TV is up and retrying.
                throw TvException(0, "The TV didn't accept this phone's key.", refusedKey = true)
            } catch (e: javax.net.ssl.SSLException) {
                throw TvException(0, "Can't reach the TV.")
            } catch (e: IOException) {
                throw TvException(0, "Can't reach the TV.")
            } finally {
                conn.disconnect()
            }
        }

    private fun bracket(h: String) = if (h.contains(':')) "[$h]" else h

    /** Limits every TLS socket to the protocol versions and cipher suites in protocol.Protocol. */
    private class RestrictedFactory(private val d: SSLSocketFactory) : SSLSocketFactory() {
        private fun r(s: Socket): Socket = s.also { if (it is SSLSocket) Tls.restrict(it) }
        override fun getDefaultCipherSuites(): Array<String> = d.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = d.supportedCipherSuites
        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean) = r(d.createSocket(s, host, port, autoClose))
        override fun createSocket(host: String, port: Int) = r(d.createSocket(host, port))
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) = r(d.createSocket(host, port, localHost, localPort))
        override fun createSocket(host: InetAddress, port: Int) = r(d.createSocket(host, port))
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int) =
            r(d.createSocket(address, port, localAddress, localPort))
    }
}
