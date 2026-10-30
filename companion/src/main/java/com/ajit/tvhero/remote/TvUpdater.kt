package com.ajit.tvhero.remote

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ajit.tvhero.adb.AdbClient
import com.ajit.tvhero.adb.Probe
import com.ajit.tvhero.adb.Steps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties

/**
 * Phase 3, M2: check the TV and install or update tv-hero on it from the phone, over the TV's debugging
 * connection on the home Wi-Fi. No computer needed.
 *
 * Security (docs/SECURITY.md, "Updating the TV from the phone"):
 * - Home Wi-Fi only (the ADB client refuses any other address).
 * - Debugging is switched on with the parent password (existing /v1/adb) and always switched off at the end,
 *   with the normal switch, even when something fails.
 * - The phone's ADB key is in the Android Keystore; the first time, a person compares the fingerprint
 *   and presses Allow on the TV.
 * - The APK is the one built into this phone app (same release key); Android refuses an update signed
 *   with another key anyway.
 */
class TvUpdater(private val ctx: Context, private val state: AppState) {
    data class Bundled(val code: Int, val name: String)

    /** An APK to install on the TV: the copy inside this phone app, or one downloaded and checked by Updater. */
    class Source(val code: Int, val name: String, val length: Long, val open: () -> java.io.InputStream)

    private fun bundledSource(): Source? {
        val b = bundled ?: return null
        val length = ctx.assets.openFd("tv-hero-tv.apk").use { it.length }
        return Source(b.code, b.name, length) { ctx.assets.open("tv-hero-tv.apk") }
    }

    val bundled: Bundled? = runCatching {
        val p = Properties().apply { ctx.assets.open("tv-hero-tv.properties").use { load(it) } }
        Bundled(p.getProperty("versionCode").trim().toInt(), p.getProperty("versionName").trim())
    }.getOrNull()

    var running by mutableStateOf(false); private set
    var allowFingerprint by mutableStateOf<String?>(null); private set
    var progress by mutableFloatStateOf(-1f); private set
    var probe by mutableStateOf<Probe.Result?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var result by mutableStateOf<String?>(null); private set
    val steps = mutableStateListOf<String>()

    fun updateNeeded(): Boolean {
        val b = bundled ?: return false
        val p = probe ?: return true
        return p.tvHeroVersionCode < b.code
    }

    /** Reads what the TV is (read-only), then switches debugging off again. */
    fun check(password: String) = go(password, null)

    /** Installs [source] (default: the TV app inside this phone app), makes sure the guard runs, switches debugging off. */
    fun update(password: String, source: Source? = null) {
        val s = source ?: bundledSource()
        if (s == null) { error = "This phone app has no TV app inside it."; return }
        go(password, s)
    }

    private fun go(password: String, source: Source?) {
        val install = source != null
        val client = state.client ?: return
        val host = state.tv?.host ?: return
        if (running) return
        running = true; error = null; result = null; progress = -1f; allowFingerprint = null
        steps.clear()
        state.scope.launch {
            var adb: AdbClient? = null
            try {
                if (state.connection != "home") throw AdbClient.AdbException("Connect this phone to the home Wi-Fi first.")
                step("Checking TV debugging")
                if (!client.remoteAccess().adbEnabled) {
                    client.setAdb(true, password)
                    delay(2500)
                }
                step("Connecting to the TV")
                val name = "tv-hero phone (" + state.store.deviceName + ")"
                adb = withContext(Dispatchers.IO) {
                    AdbClient.connect(host, 5555, AdbIdentity.signer(), name,
                        { fp -> state.scope.launch { allowFingerprint = fp } }, 120_000)
                }
                allowFingerprint = null
                val c = adb!!
                step("Reading the TV")
                probe = withContext(Dispatchers.IO) { Probe.run(c) }
                if (install) {
                    val b = source!!
                    step("Installing tv-hero ${b.name}")
                    val answer = withContext(Dispatchers.IO) {
                        b.open().use { input ->
                            c.install(input, b.length) { sent, total -> state.scope.launch { progress = sent.toFloat() / total } }
                        }
                    }
                    progress = -1f
                    if (!answer.startsWith("Success")) throw AdbClient.AdbException("The TV didn't install it: $answer")
                    step("Checking the new version")
                    val after = withContext(Dispatchers.IO) { Probe.run(c) }
                    probe = after
                    if (after.tvHeroVersionCode != b.code) throw AdbClient.AdbException("The TV still reports ${after.tvHeroVersion}.")
                    step("Making sure the guard runs")
                    val bound = withContext(Dispatchers.IO) {
                        repeat(3) {
                            delay(3000)
                            if (Steps.guardBound(c)) return@withContext true
                            Steps.rebindGuard(c)
                        }
                        Steps.guardBound(c)
                    }
                    if (!bound) throw AdbClient.AdbException("tv-hero ${b.name} is installed, but the guard didn't start. Restart the TV once.")
                    // 2.9: Android's app-usage record makes screen time exact; older installs never had it.
                    withContext(Dispatchers.IO) { runCatching { Steps.grantUsageAccess(c) } }
                    result = "tv-hero ${b.name} is on the TV and the guard is running."
                } else {
                    result = probe?.describe()
                }
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                val c = adb
                step("Switching TV debugging off")
                withContext(Dispatchers.IO) {
                    if (c != null) { Steps.debuggingOff(c); c.close() }
                }
                // Through the TV app as well, in case the ADB connection never opened.
                if (c == null) runCatching { client.setAdb(false, password) }
                allowFingerprint = null
                running = false
                state.refresh()
            }
        }
    }

    /** Set Up a New TV: true once tv-hero is on the TV and pairing can start. */
    var setUpDone by mutableStateOf(false); private set
    var setUpNote by mutableStateOf(""); private set

    /**
     * Sets up a TV that doesn't have tv-hero yet, at [host], over the debugging connection a person switched
     * on at the TV. Installs the TV app built into this phone app, makes it Home where the TV allows, switches
     * on its guard, then switches debugging off. Pairing follows on the normal pairing screen.
     */
    fun setUpNew(host: String) {
        if (running) return
        val src = bundledSource() ?: run { error = "This phone app has no TV app inside it."; return }
        running = true; error = null; result = null; progress = -1f; allowFingerprint = null; setUpDone = false; setUpNote = ""
        steps.clear()
        state.scope.launch {
            var adb: AdbClient? = null
            try {
                step("Connecting to the TV")
                adb = withContext(Dispatchers.IO) {
                    AdbClient.connect(host, 5555, AdbIdentity.signer(), "tv-hero phone (" + state.store.deviceName + ")",
                        { fp -> state.scope.launch { allowFingerprint = fp } }, 180_000)
                }
                allowFingerprint = null
                val c = adb!!
                step("Reading the TV")
                val p = withContext(Dispatchers.IO) { Probe.run(c) }
                probe = p
                if (p.level() == Probe.Level.UNSUPPORTED) throw AdbClient.AdbException("This TV runs Android ${p.release}; tv-hero needs Android 9 or later.")
                val r = withContext(Dispatchers.IO) {
                    src.open().use { input ->
                        Steps.setUp(c, p, input, src.length,
                            { sent, total -> state.scope.launch { progress = sent.toFloat() / total } },
                            { what -> state.scope.launch { progress = -1f; step(what) } })
                    }
                }
                probe = withContext(Dispatchers.IO) { Probe.run(c) }
                setUpNote = r.note
                result = "tv-hero ${src.name} is on ${p.describe()}" + if (r.home) ", as its Home screen." else "."
                setUpDone = true
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                val c = adb
                if (c != null) {
                    step("Switching TV debugging off")
                    withContext(Dispatchers.IO) { Steps.debuggingOff(c); c.close() }
                }
                allowFingerprint = null
                running = false
            }
        }
    }

    /** True after Start Over removed tv-hero from the TV. */
    var removed by mutableStateOf(false); private set

    /**
     * Start Over: puts the TV's own Home back, switches the guard off and uninstalls tv-hero, with the parent
     * password. Debugging is left on so the phone can set the TV up again at once (Set Up a New TV). The phone
     * forgets this TV, because the TV forgets the phone.
     */
    fun startOver(password: String) {
        val client = state.client ?: return
        val host = state.tv?.host ?: return
        if (running) return
        running = true; error = null; result = null; progress = -1f; allowFingerprint = null; removed = false
        steps.clear()
        state.scope.launch {
            var adb: AdbClient? = null
            try {
                if (state.connection != "home") throw AdbClient.AdbException("Connect this phone to the home Wi-Fi first.")
                step("Switching TV debugging on")
                if (!client.remoteAccess().adbEnabled) { client.setAdb(true, password); delay(2500) }
                step("Connecting to the TV")
                adb = withContext(Dispatchers.IO) {
                    AdbClient.connect(host, 5555, AdbIdentity.signer(), "tv-hero phone (" + state.store.deviceName + ")",
                        { fp -> state.scope.launch { allowFingerprint = fp } }, 120_000)
                }
                allowFingerprint = null
                val c = adb!!
                withContext(Dispatchers.IO) { Steps.remove(c) { what -> state.scope.launch { step(what) } } }
                lastHost = host
                result = "tv-hero is gone from the TV and its own Home screen is back. TV debugging is still on, so you can set it up again now."
                removed = true
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                adb?.close()
                allowFingerprint = null
                running = false
                if (removed) state.forgetTv()
            }
        }
    }

    /** The TV address Start Over used, offered by Set Up a New TV. */
    var lastHost: String = ""
        private set

    private fun step(s: String) { steps.add(s) }
}
