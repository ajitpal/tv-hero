package com.ajit.tvhero.remote

import java.net.URL

/**
 * The checks the updater makes, kept free of Android so they're unit-tested. Updates come from the public
 * releases repo; the app trusts only its own pinned release certificate, never a value from the download.
 */
object UpdateRules {
    const val MANIFEST = "https://github.com/ajitpal/tv-hero/releases/latest/download/update.json"

    /** Tried in order: the project's own releases, then the old releases repo (2.7.0 published there). */
    val MANIFESTS = listOf(MANIFEST, "https://github.com/ajitpal/tv-hero-releases/releases/latest/download/update.json")

    /** SHA-256 of tv-hero's release signing certificate (both apps). An APK signed otherwise is refused. */
    const val RELEASE_CERT_SHA256 = "309705ca333f547dfc8bb7d5d02cff8ca63b64cc49d5a533f45b6e06544c9124"

    private val HOSTS = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    /** Only HTTPS, only GitHub's release hosts (the manifest, the APKs and their redirects). */
    fun allowed(url: String): Boolean = runCatching {
        val u = URL(url)
        u.protocol == "https" && u.host in HOSTS && u.userInfo == null && (u.port == -1 || u.port == 443)
    }.getOrDefault(false)

    /** Compares "2.10.0" with "2.9.1" numerically; text after a dash or anything non-numeric counts as 0. */
    fun compareVersions(a: String, b: String): Int {
        val x = a.trim().removePrefix("v").substringBefore('-').split('.')
        val y = b.trim().removePrefix("v").substringBefore('-').split('.')
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrNull(i)?.toIntOrNull() ?: 0
            val q = y.getOrNull(i)?.toIntOrNull() ?: 0
            if (p != q) return p.compareTo(q)
        }
        return 0
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** Whether a downloaded APK may be installed: right package, the promised version, signed with our key. */
    fun acceptable(expectedPackage: String, expectedCode: Long, pkg: String?, code: Long, signerDigests: List<String>,
                   installedSigner: String?): String? = when {
        pkg != expectedPackage -> "The download isn't $expectedPackage."
        code != expectedCode -> "The download isn't the version it claims to be."
        signerDigests.isEmpty() -> "The download isn't signed."
        signerDigests.any { !it.equals(RELEASE_CERT_SHA256, ignoreCase = true) } -> "The download isn't signed with tv-hero's release key."
        installedSigner != null && !installedSigner.equals(RELEASE_CERT_SHA256, ignoreCase = true) ->
            "This copy of the app was signed with another key, so it can't update itself. Install the release once by hand."
        else -> null
    }
}
