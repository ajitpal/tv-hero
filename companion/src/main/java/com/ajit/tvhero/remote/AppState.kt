package com.ajit.tvhero.remote

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything the screens show, in one place. Lives as long as the app process. */
class AppState(context: Context) {
    val app: Context = context.applicationContext
    val store = Store(app)
    val discovery = Discovery(app)
    val hid = HidController(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** Phase 3: checking and updating the TV app from the phone. */
    val updater by lazy { TvUpdater(app, this) }
    /** Software updates from GitHub for this app and the TV app. */
    val software by lazy { Updater(app, this) }

    var tv by mutableStateOf<PairedTv?>(store.last())
        private set
    var client by mutableStateOf(tv?.let { TvClient(it.host, it.tvFingerprint, it.remoteHost) })
        private set

    var apps by mutableStateOf<List<TvApp>>(emptyList())
    var blockNewInstalls by mutableStateOf(true)
    var home by mutableStateOf<HomeLayout?>(null)
    var family by mutableStateOf<Family?>(null)
    var now by mutableStateOf<NowPlaying?>(null)
    var sessions by mutableStateOf<List<Session>>(emptyList())
    var remote by mutableStateOf<RemoteAccess?>(null)
    var volume by mutableStateOf<Volume?>(null)
    /** The TV app's version, from its status. */
    var tvVersion by mutableStateOf("")
    var phones by mutableStateOf<List<PairedPhone>>(emptyList())
    var internet by mutableStateOf<InternetControl?>(null)
    var requests by mutableStateOf<List<KidRequest>>(emptyList())
    /** Set when the TV refused this phone (it was removed): the app offers to pair again. */
    var revoked by mutableStateOf(false)
    private var refusals = 0

    /**
     * Only call it "removed" when it really is: the TV answered 401 (not paired), or it refused this
     * phone's key twice in a row while its pairing port answers (so it's up and it's the same TV address).
     * A Wi-Fi drop mid-handshake must never send someone to "Pair Again", which forgets the TV.
     */
    private suspend fun noteFailure(c: TvClient, e: Throwable) {
        if (e !is TvException) return
        if (e.status == 401) { revoked = true; return }
        if (!e.refusedKey) return
        refusals++
        if (refusals >= 2 && runCatching { c.info() }.isSuccess) revoked = true
    }

    /** "Try Again" on the removed screen. */
    fun retryConnection() {
        revoked = false
        refusals = 0
        refresh()
    }
    /** App lock (see AppLock): true until the fingerprint / screen lock check passes. */
    var locked by mutableStateOf(false)
    var backgroundAt = 0L
    var connection by mutableStateOf("")     // "home", "internet", or "" when unreachable
    var loading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    val icons = mutableStateMapOf<String, Bitmap?>()

    val labels: Map<String, String> get() = apps.associate { it.pkg to it.label }

    // ---------- parent password, remembered 5 minutes ----------

    private var cachedPassword: String? = null
    private var cachedUntil = 0L

    fun rememberedPassword(): String? = if (System.currentTimeMillis() < cachedUntil) cachedPassword else null

    fun passwordWorked(pw: String) {
        cachedPassword = pw
        cachedUntil = System.currentTimeMillis() + 5 * 60_000
    }

    fun forgetPassword() {
        cachedPassword = null
        cachedUntil = 0
    }

    // ---------- TV selection ----------

    fun select(t: PairedTv) {
        tv = t
        client = TvClient(t.host, t.tvFingerprint, t.remoteHost)
        apps = emptyList(); home = null; family = null; now = null; sessions = emptyList(); remote = null
        volume = null; phones = emptyList(); internet = null; requests = emptyList(); revoked = false
        icons.clear()
        refresh()
    }

    /** Screenshot tests only: show [t] without the Keystore or a network. */
    fun preview(t: PairedTv) {
        tv = t
        client = null
    }

    fun paired(t: PairedTv) {
        store.save(t)
        select(t)
    }

    fun forgetTv() {
        AdbIdentity.forget()   // the TV asks to Allow a new debugging key next time
        tv?.let { store.forget(it.id) }
        tv = null
        client = null
        apps = emptyList(); home = null; family = null; now = null
    }

    // ---------- loading ----------

    fun refresh() {
        val c = client ?: return
        scope.launch {
            try {
                val (list, blockNew) = c.apps()
                apps = list.sortedWith(compareBy({ it.state != "pending" }, { it.label.lowercase() }))
                blockNewInstalls = blockNew
                connection = c.via
                list.filter { it.pkg !in icons }.forEach { a -> scope.launch { icons[a.pkg] = c.icon(a.pkg) } }
                family = c.family()
                now = c.now()
                home = c.home()
                val (s, _) = c.activity(System.currentTimeMillis() - 7L * 24 * 3600_000)
                sessions = s
                volume = c.volume()
                requests = c.requests()
                phones = c.phones()
                internet = c.internet()
                learnRemoteAddress(c)
                refusals = 0
            } catch (e: TvException) {
                connection = ""
                noteFailure(c, e)
                if (!revoked) message = e.message
            } catch (e: Exception) {
                message = e.message
            }
        }
    }

    /** The TV reports its Tailscale address; keep it so the app can reach the TV away from home. */
    private suspend fun learnRemoteAddress(c: TvClient) {
        val r = runCatching { c.remoteAccess() }.getOrNull() ?: return
        remote = r
        val t = tv ?: return
        if (r.tailscaleIp.isNotBlank() && r.tailscaleIp != t.remoteHost) {
            store.updateRemoteHost(t.id, r.tailscaleIp)
            val updated = t.copy(remoteHost = r.tailscaleIp)
            tv = updated
            client = TvClient(updated.host, updated.tvFingerprint, updated.remoteHost)
        }
    }

    private var poller: Job? = null

    /** While the app is on screen: what's playing, every 5 seconds. */
    fun startPolling() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                val c = client
                if (c != null) {
                    runCatching {
                        val st = c.status()
                        volume = st.volume
                        tvVersion = st.version
                        now = c.now()
                        connection = c.via
                        if (internet?.enabled != st.internetControl) internet = c.internet()
                    }.onFailure { e ->
                        connection = ""
                        noteFailure(c, e)
                    }
                }
                delay(5000)
            }
        }
    }

    fun stopPolling() {
        poller?.cancel()
        poller = null
    }

    /** Runs a TV call, shows its error as a message, and reports success. */
    fun run(onDone: (Boolean) -> Unit = {}, block: suspend () -> Unit) {
        scope.launch {
            loading = true
            val ok = try {
                block()
                true
            } catch (e: TvException) {
                message = e.message
                if (e.status == 403 && e.message?.contains("password") == true) forgetPassword()
                false
            } catch (e: Exception) {
                message = e.message ?: "Something went wrong."
                false
            } finally {
                loading = false
            }
            onDone(ok)
        }
    }

    companion object {
        @Volatile private var instance: AppState? = null
        fun get(context: Context): AppState =
            instance ?: synchronized(this) { instance ?: AppState(context.applicationContext).also { instance = it } }
    }
}
