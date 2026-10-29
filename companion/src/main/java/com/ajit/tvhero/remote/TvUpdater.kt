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
    fun check(password: String) = go(password, install = false)

    /** Installs this phone's TV app on the TV, makes sure the guard runs, switches debugging off. */
    fun update(password: String) = go(password, install = true)

    private fun go(password: String, install: Boolean) {
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
                    val b = bundled ?: throw AdbClient.AdbException("This phone app has no TV app inside it.")
                    step("Installing tv-hero ${b.name}")
                    val answer = withContext(Dispatchers.IO) {
                        ctx.assets.openFd("tv-hero-tv.apk").use { fd ->
                            fd.createInputStream().use { input ->
                                c.install(input, fd.length) { sent, total -> state.scope.launch { progress = sent.toFloat() / total } }
                            }
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

    private fun step(s: String) { steps.add(s) }
}
