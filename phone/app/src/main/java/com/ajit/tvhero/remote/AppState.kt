package com.ajit.tvhero.remote

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Everything the screens show, in one place. Lives as long as the app process. */
class AppState(context: Context) {
    val app: Context = context.applicationContext
    val store = Store(app)
    val discovery = Discovery(app)
    val hid = HidController(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var tv by mutableStateOf<PairedTv?>(store.last())
        private set
    var client by mutableStateOf(tv?.let { TvClient(it.host, it.deviceId, it.secret) })
        private set

    var apps by mutableStateOf<List<TvApp>>(emptyList())
    var blockNewInstalls by mutableStateOf(true)
    var home by mutableStateOf<HomeLayout?>(null)
    var loading by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    val icons = mutableStateMapOf<String, Bitmap?>()

    /** The parent password, kept in memory for 5 minutes after it last worked. */
    private var cachedPassword: String? = null
    private var cachedUntil = 0L

    fun rememberedPassword(): String? =
        if (System.currentTimeMillis() < cachedUntil) cachedPassword else null

    fun passwordWorked(pw: String) {
        cachedPassword = pw
        cachedUntil = System.currentTimeMillis() + 5 * 60_000
    }

    fun forgetPassword() {
        cachedPassword = null
        cachedUntil = 0
    }

    fun select(t: PairedTv) {
        tv = t
        client = TvClient(t.host, t.deviceId, t.secret)
        apps = emptyList()
        home = null
        icons.clear()
        refresh()
    }

    fun paired(t: PairedTv) {
        store.save(t)
        select(t)
    }

    fun forgetTv() {
        tv?.let { store.forget(it.id) }
        tv = null
        client = null
        apps = emptyList()
        home = null
    }

    fun refresh() {
        val c = client ?: return
        run {
            val (list, blockNew) = c.apps()
            apps = list.sortedWith(compareBy({ it.state != "pending" }, { it.label.lowercase() }))
            blockNewInstalls = blockNew
            home = c.home()
            list.filter { it.pkg !in icons }.forEach { a ->
                scope.launch { icons[a.pkg] = c.icon(a.pkg) }
            }
        }
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
            instance ?: synchronized(this) { instance ?: AppState(context).also { instance = it } }
    }
}
