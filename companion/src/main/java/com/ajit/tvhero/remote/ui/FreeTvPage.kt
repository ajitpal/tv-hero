package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.FreeTvItem
import com.ajit.tvhero.remote.FreeTvState
import com.ajit.tvhero.remote.M3u
import com.ajit.tvhero.remote.ShareActivity
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.FieldRow
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigSheet
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.SearchField
import com.ajit.tvhero.remote.ui.hig.section
import kotlinx.coroutines.launch

// ---------- Free TV (2.11) ----------

private fun check(on: Boolean): @Composable () -> Unit = {
    if (on) Text("✓", style = HigType.headline, color = Hig.colors.tint)
}

/**
 * Settings → Free TV: what the TV's Free TV screen offers (apps, YouTube channels, playlist channels), the
 * video player for channels, and what each child sees. tv-hero ships no channels; playlists are read here.
 */
@Composable
fun FreeTvPage(state: AppState, gate: PasswordGate, preview: FreeTvState? = null, onBack: () -> Unit) {
    var tv by remember { mutableStateOf(preview) }
    var old by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) }   // app | youtube | playlist | kid:<id>
    if (preview == null) LaunchedEffect(Unit) { state.run { tv = state.client!!.freeTv().also { if (it == null) old = true } } }
    val family = state.family

    fun save(items: List<FreeTvItem>) = state.run { tv = state.client!!.setFreeTvItems(items) }

    HigScreen("Free TV", onBack = onBack, backLabel = "Settings") {
        if (old) { section("old", footer = "Update the TV app (Settings → About → TV App) to set this up.") { Row("This TV's app is older than 2.11") }; return@HigScreen }
        val t = tv
        if (t == null) { section("l") { Row("Loading…") }; return@HigScreen }
        section("add", "Add", "Free TV is a screen on the TV, opened from Home's top bar. tv-hero comes with no channels: " +
            "add official apps, YouTube channels, or channels from a playlist you have the right to watch.") {
            Row("An App on the TV", chevron = true) { sheet = "app" }
            Row("A YouTube Channel", chevron = true) { sheet = "youtube" }
            Row("Channels from a Playlist (M3U)", chevron = true) { sheet = "playlist" }
        }
        if (t.items.isNotEmpty()) section("items", "On Free TV (${t.items.size} of ${t.max})", "Tap one to remove it.") {
            t.items.forEach { i ->
                Row(i.title, subtitle = when (i.kind) {
                    FreeTvItem.APP -> "App"; FreeTvItem.YOUTUBE -> "YouTube channel"; else -> "Channel" + if (i.group.isNotBlank()) " · ${i.group}" else ""
                }) { save(t.items - i) }
            }
            ButtonRow("Remove All", destructive = true) { save(emptyList()) }
        }
        val kids = buildList {
            family?.kids?.forEach { add(it.id to it.name) }
            add("guest" to "Guest")
        }
        if (t.items.isNotEmpty()) section("who", "Who Sees What", "Parent sees everything. A child or Guest sees only what you tick; " +
            "ticking needs the parent password. In YouTube a child can move on to other videos, as in the app itself.") {
            kids.forEach { (id, name) ->
                val n = t.ticks[id]?.count { tick -> t.items.any { it.id == tick } } ?: 0
                Row(name, value = if (n == 0) "Nothing" else "$n", chevron = true) { sheet = "kid:$id" }
            }
        }
        section("player", "Channels Play In", if (t.players.isEmpty()) "Install a video player on the TV, such as VLC for Android TV (free, open source), to watch playlist channels."
            else "The video player on the TV that opens playlist channels.") {
            if (t.players.isEmpty()) Row("No video player on the TV")
            t.players.forEach { (pkg, label) ->
                Row(label, trailing = check(t.player == pkg)) { state.run { tv = state.client!!.setFreeTvPlayer(pkg) } }
            }
        }
    }

    val t = tv ?: return
    val s = sheet ?: return
    when {
        s == "app" -> HigSheet("Add an App", onCancel = { sheet = null }, done = null) {
            section("apps", footer = "Official free apps work best, for example a public broadcaster's own app.") {
                state.apps.filter { a -> a.state == "allowed" && !a.system && t.items.none { it.pkg == a.pkg } }.sortedBy { it.label.lowercase() }.forEach { a ->
                    Row(a.label, leading = { AppIcon(state, a.pkg, 32) }) { sheet = null; save(t.items + FreeTvItem.app(a.pkg, a.label)) }
                }
            }
        }
        s == "youtube" -> {
            var name by remember { mutableStateOf("") }
            var link by remember { mutableStateOf("") }
            val url = ShareActivity.webLink(link)?.takeIf { isYouTubeLink(it) }
            HigSheet("YouTube Channel", onCancel = { sheet = null }, done = "Add", doneEnabled = url != null && name.isNotBlank(),
                onDone = { sheet = null; save(t.items + FreeTvItem.youtube(url!!, name.trim())) }) {
                section("y", footer = "The channel's link, for example https://www.youtube.com/@channel. It opens in YouTube on the TV.") {
                    FieldRow("Name", name, { name = it }, "Shown on the TV")
                    FieldRow("Link", link, { link = it }, "https://www.youtube.com/@…", keyboard = KeyboardType.Uri)
                }
            }
        }
        s == "playlist" -> PlaylistSheet(state, t, onAdd = { add -> sheet = null; save((t.items + add).distinctBy { it.id }.take(t.max)) }) { sheet = null }
        s.startsWith("kid:") -> {
            val id = s.removePrefix("kid:")
            val name = if (id == "guest") "Guest" else family?.kids?.firstOrNull { it.id == id }?.name ?: "Profile"
            val before = t.ticks[id].orEmpty()
            var chosen by remember(id) { mutableStateOf(before) }
            HigSheet(name, onCancel = { sheet = null }, done = "Save", onDone = {
                sheet = null
                val adds = chosen.any { it !in before }
                if (adds) gate.run("Let $name watch these on Free TV.") { pw -> tv = state.client!!.setFreeTvTicks(id, chosen, pw) }
                else state.run { tv = state.client!!.setFreeTvTicks(id, chosen, null) }
            }) {
                section("t", footer = "What $name sees on the Free TV screen.") {
                    t.items.forEach { i -> Row(i.title, trailing = check(i.id in chosen)) { chosen = if (i.id in chosen) chosen - i.id else chosen + i.id } }
                }
            }
        }
    }
}

/** Reads a playlist on the phone, then lets the parent tick channels to add (search, at most what fits). */
@Composable
private fun PlaylistSheet(state: AppState, t: FreeTvState, onAdd: (List<FreeTvItem>) -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf<List<M3u.Channel>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    val room = (t.max - t.items.size).coerceAtLeast(0)
    HigSheet("Playlist", onCancel = onCancel, done = if (picked.isEmpty()) null else "Add ${picked.size}", onDone = {
        onAdd(channels.filter { it.url in picked }.map { FreeTvItem.stream(it) })
    }) {
        section("addr", footer = "Add only playlists you have the right to watch. The phone reads it; the TV gets only the channels you tick, " +
            "never the playlist itself. Room for $room more.") {
            FieldRow("Address", address, { address = it }, "https://…/playlist.m3u", keyboard = KeyboardType.Uri)
            ButtonRow(if (loading) "Reading…" else "Read Playlist", enabled = !loading && address.isNotBlank()) {
                loading = true; problem = null
                scope.launch {
                    try { channels = M3u.fetch(address); picked = emptySet() } catch (e: Exception) { problem = e.message ?: "Couldn't read that playlist." }
                    loading = false
                }
            }
        }
        problem?.let { p -> item("p") { Text(p, style = HigType.subhead, color = Hig.colors.destructive, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) } }
        if (channels.isNotEmpty()) {
            item("filter") { SearchField(filter, { filter = it }, "Search ${channels.size} channels", Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            val shown = channels.filter { filter.isBlank() || it.name.contains(filter, true) || it.group.contains(filter, true) }.take(300)
            section("ch", footer = if (shown.size == 300) "Showing the first 300: search to narrow it." else null) {
                shown.forEach { ch ->
                    val on = ch.url in picked
                    Row(ch.name, subtitle = ch.group.ifBlank { null }, trailing = check(on)) {
                        picked = if (on) picked - ch.url else if (picked.size < room) picked + ch.url else picked
                    }
                }
            }
        }
    }
}
