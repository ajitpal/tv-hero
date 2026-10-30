package com.ajit.tvhero.remote

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Software updates from GitHub (Phase 3), for the phone app and, through it, the TV app.
 *
 * - Reads `update.json` from the latest GitHub release of ajitpal/tv-hero (and the old releases repo), with no API and no rate limit.
 * - Downloads over HTTPS from GitHub's release hosts only; checks size and SHA-256.
 * - Before installing, the APK must be the expected package and versionCode and be signed with tv-hero's
 *   pinned release certificate (and, for the phone app, the same key as the installed app).
 * - The phone app installs through PackageInstaller: Android asks the person to confirm.
 * - The TV app is installed over the home Wi-Fi by TvUpdater, as when updating from the bundled copy.
 * - Automatic checks (once a day, when the app opens) can be switched off; off means off.
 */
class Updater(private val ctx: Context, private val state: AppState) {
    data class Entry(val id: String, val pkg: String, val versionCode: Long, val versionName: String,
                     val url: String, val sha256: String, val size: Long)
    data class Release(val version: String, val notesUrl: String, val phone: Entry?, val tv: Entry?)

    private val prefs = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)

    var release by mutableStateOf<Release?>(null); private set
    var checking by mutableStateOf(false); private set
    var busy by mutableStateOf<String?>(null); private set
    var progress by mutableFloatStateOf(-1f); private set
    var error by mutableStateOf<String?>(null); private set
    var lastChecked by mutableStateOf(prefs.getLong("last_checked", 0)); private set
    var autoCheck: Boolean
        get() = prefs.getBoolean("auto", true)
        set(v) { prefs.edit().putBoolean("auto", v).apply() }

    val installedCode: Long get() = info(ctx.packageName)?.longVersionCode ?: 0
    val installedName: String get() = info(ctx.packageName)?.versionName ?: ""

    fun phoneUpdate(): Entry? = release?.phone?.takeIf { it.versionCode > installedCode }

    fun tvUpdate(): Entry? {
        val e = release?.tv ?: return null
        val onTv = state.tvVersion.ifBlank { return null }
        return e.takeIf { UpdateRules.compareVersions(it.versionName, onTv) > 0 }
    }

    /** Once a day when the app opens, unless switched off. */
    fun checkIfDue() {
        if (autoCheck && System.currentTimeMillis() - lastChecked > 24 * 3600_000L) check()
    }

    fun check() {
        if (checking) return
        checking = true; error = null
        state.scope.launch {
            try {
                var found: Release? = null
                var last: Exception? = null
                for (url in UpdateRules.MANIFESTS) {
                    try {
                        val text = withContext(Dispatchers.IO) { String(fetch(url, 128 * 1024).readBytes()) }
                        val r = parse(JSONObject(text))
                        if (found == null || UpdateRules.compareVersions(r.version, found.version) > 0) found = r
                    } catch (e: Exception) { last = e }
                }
                release = found ?: throw (last ?: IllegalStateException("no releases"))
                lastChecked = System.currentTimeMillis()
                prefs.edit().putLong("last_checked", lastChecked).apply()
            } catch (e: Exception) {
                error = "Couldn't check for updates: " + (e.message ?: "no connection")
            } finally {
                checking = false
            }
        }
    }

    private fun parse(o: JSONObject): Release {
        val apps = o.optJSONArray("apps")
        var phone: Entry? = null; var tv: Entry? = null
        for (i in 0 until (apps?.length() ?: 0)) {
            val a = apps!!.getJSONObject(i)
            val e = Entry(a.optString("id"), a.optString("package"), a.optLong("versionCode"), a.optString("versionName"),
                a.optString("url"), a.optString("sha256").lowercase(), a.optLong("size"))
            if (!UpdateRules.allowed(e.url) || e.sha256.length != 64 || e.size <= 0) continue
            when (e.pkg) {
                ctx.packageName -> phone = e
                "com.ajit.tvhero" -> tv = e
            }
        }
        val notes = o.optString("notes").takeIf { UpdateRules.allowed(it) } ?: ""
        return Release(o.optString("version"), notes, phone, tv)
    }

    /** Downloads, checks and asks Android to install the new phone app (the person confirms). */
    fun updatePhone() {
        val e = phoneUpdate() ?: return
        run("Updating the phone app") {
            val f = download(e)
            verify(f, e, requireSameAsInstalled = true)
            withContext(Dispatchers.Main) { installSelf(f) }
        }
    }

    /** Downloads and checks the new TV app, then installs it on the TV over the home Wi-Fi. */
    fun updateTv(password: String) {
        val e = tvUpdate() ?: return
        run("Downloading the TV app") {
            val f = download(e)
            verify(f, e, requireSameAsInstalled = false)
            withContext(Dispatchers.Main) {
                state.updater.update(password, TvUpdater.Source(e.versionCode.toInt(), e.versionName, f.length()) { f.inputStream() })
            }
        }
    }

    private fun run(what: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = what; error = null; progress = -1f
        state.scope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "The update didn't finish."
            } finally {
                busy = null
                progress = -1f
            }
        }
    }

    // ---------- download and checks ----------

    private suspend fun download(e: Entry): File = withContext(Dispatchers.IO) {
        if (e.size > 200L * 1024 * 1024) throw IllegalStateException("The update is unexpectedly large.")
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "${e.id}-${e.versionCode}.apk")
        val md = MessageDigest.getInstance("SHA-256")
        fetch(e.url, e.size).use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > e.size) throw IllegalStateException("The download is bigger than promised.")
                    md.update(buf, 0, n)
                    o.write(buf, 0, n)
                    withContext(Dispatchers.Main) { progress = total.toFloat() / e.size }
                }
                if (total != e.size) throw IllegalStateException("The download was cut short.")
            }
        }
        if (UpdateRules.hex(md.digest()) != e.sha256) { out.delete(); throw IllegalStateException("The download's checksum doesn't match.") }
        out
    }

    /** HTTPS GET following at most 5 redirects, each to an allowed GitHub host. */
    private fun fetch(start: String, maxBytes: Long): InputStream {
        var url = start
        repeat(6) {
            if (!UpdateRules.allowed(url)) throw IllegalStateException("Refused to download from an unexpected address.")
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = c.responseCode
            if (code in 300..399) {
                url = c.getHeaderField("Location") ?: throw IllegalStateException("Bad redirect.")
                c.disconnect()
                return@repeat
            }
            if (code != 200) throw IllegalStateException("GitHub answered $code.")
            if (c.contentLengthLong > maxBytes) throw IllegalStateException("The file is unexpectedly large.")
            return c.inputStream
        }
        throw IllegalStateException("Too many redirects.")
    }

    @Suppress("DEPRECATION")
    private fun verify(f: File, e: Entry, requireSameAsInstalled: Boolean) {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val pi = pm.getPackageArchiveInfo(f.path, flags) ?: throw IllegalStateException("The download isn't an app.")
        val digests = signers(pi)
        val installed = if (requireSameAsInstalled) info(ctx.packageName)?.let { signers(it).firstOrNull() } else null
        UpdateRules.acceptable(e.pkg, e.versionCode, pi.packageName, pi.longVersionCode, digests, installed)?.let {
            f.delete(); throw IllegalStateException(it)
        }
    }

    @Suppress("DEPRECATION")
    private fun signers(pi: PackageInfo): List<String> {
        val sigs = pi.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
            ?: pi.signatures ?: return emptyList()
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.map { UpdateRules.hex(md.digest(it.toByteArray())) }
    }

    @Suppress("DEPRECATION")
    private fun info(pkg: String): PackageInfo? = runCatching {
        ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)
    }.getOrNull()

    // ---------- installing the phone app ----------

    /** Android must allow tv-hero to install apps; if not, the person is sent to that switch first. */
    fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun installSelf(f: File) {
        if (!canInstall()) { openInstallPermission(); throw IllegalStateException("Allow tv-hero to install updates, then tap Update again.") }
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(f.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            f.inputStream().use { input -> s.openWrite("base.apk", 0, f.length()).use { out -> input.copyTo(out); s.fsync(out) } }
            val action = ctx.packageName + ".INSTALL_STATUS"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
                        }
                        PackageInstaller.STATUS_SUCCESS -> runCatching { ctx.unregisterReceiver(this) }
                        else -> {
                            error = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android didn't install the update."
                            runCatching { ctx.unregisterReceiver(this) }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            else ctx.registerReceiver(receiver, IntentFilter(action))
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(action).setPackage(ctx.packageName), flags)
            s.commit(pi.intentSender)
        }
    }
}
