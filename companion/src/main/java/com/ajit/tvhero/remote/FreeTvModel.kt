package com.ajit.tvhero.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One Free TV item (2.11): an app on the TV, a YouTube channel, or a channel from a playlist. */
data class FreeTvItem(val id: String, val kind: String, val title: String, val pkg: String = "", val url: String = "", val group: String = "") {
    fun json(): JSONObject = JSONObject().put("id", id).put("kind", kind).put("title", title).put("package", pkg).put("url", url).put("group", group)

    companion object {
        const val APP = "app"
        const val YOUTUBE = "youtube"
        const val STREAM = "stream"

        /** Same thing, same id: adding a channel twice keeps one. */
        fun idFor(kind: String, key: String): String =
            MessageDigest.getInstance("SHA-1").digest("$kind|$key".toByteArray()).take(8).joinToString("") { "%02x".format(it) }

        fun app(pkg: String, label: String) = FreeTvItem(idFor(APP, pkg), APP, label.take(60), pkg = pkg)
        fun youtube(url: String, title: String) = FreeTvItem(idFor(YOUTUBE, url), YOUTUBE, title.take(60), url = url, group = "YouTube")
        fun stream(c: M3u.Channel) = FreeTvItem(idFor(STREAM, c.url), STREAM, c.name.take(60), url = c.url, group = c.group.take(40))

        fun from(o: JSONObject) = FreeTvItem(o.optString("id"), o.optString("kind"), o.optString("title"),
            o.optString("package"), o.optString("url"), o.optString("group"))
    }
}

data class FreeTvState(val items: List<FreeTvItem>, val ticks: Map<String, Set<String>>, val players: List<Pair<String, String>>,
                       val player: String, val max: Int) {
    companion object {
        fun from(o: JSONObject): FreeTvState {
            val a = o.optJSONArray("items") ?: JSONArray()
            val t = o.optJSONObject("ticks") ?: JSONObject()
            val p = o.optJSONArray("players") ?: JSONArray()
            return FreeTvState((0 until a.length()).map { FreeTvItem.from(a.getJSONObject(it)) },
                t.keys().asSequence().associateWith { k -> t.optJSONArray(k)?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.toSet() } ?: emptySet() },
                (0 until p.length()).map { p.getJSONObject(it).let { j -> j.optString("package") to j.optString("label") } },
                o.optString("player"), o.optInt("max", 120))
        }
    }
}

/**
 * Playlists (M3U) a parent adds, read here on the phone (2.11). Only names, groups and http(s) stream
 * addresses are kept; the TV gets only the channels the parent picks. tv-hero ships no playlists.
 */
object M3u {
    data class Channel(val name: String, val group: String, val url: String)

    private val attr = Regex("""([a-zA-Z-]+)="([^"]*)"""")

    fun parse(text: String, max: Int = 5000): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var group = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attr.findAll(line.substringBeforeLast(',', line)).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = line.substringAfterLast(',', "").trim().ifEmpty { attrs["tvg-name"].orEmpty() }
                    group = attrs["group-title"].orEmpty()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(':').trim()
                line.startsWith("#") -> {}
                else -> {
                    val lower = line.lowercase()
                    if ((lower.startsWith("https://") || lower.startsWith("http://")) && line.length <= 2048 && !line.any { it.isWhitespace() }) {
                        out += Channel(name.ifEmpty { line.substringAfterLast('/').take(40) }.replace(Regex("\\p{Cntrl}"), " ").trim(), group.trim(), line)
                        if (out.size >= max) return out
                    }
                    name = ""; group = ""
                }
            }
        }
        return out
    }

    /** Downloads a playlist on the phone: http(s) only, at most 5 MB, 20 s. */
    suspend fun fetch(address: String): List<Channel> = withContext(Dispatchers.IO) {
        val a = address.trim()
        require(a.startsWith("https://", true) || a.startsWith("http://", true)) { "A playlist address starts with http:// or https://." }
        val c = URL(a).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("The playlist answered ${c.responseCode}.")
            val bytes = c.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > 5 * 1024 * 1024) throw IllegalStateException("That playlist is larger than 5 MB.")
                }
                buf.toByteArray()
            }
            parse(String(bytes, Charsets.UTF_8)).also { if (it.isEmpty()) throw IllegalStateException("No channels found at that address.") }
        } finally {
            c.disconnect()
        }
    }
}
