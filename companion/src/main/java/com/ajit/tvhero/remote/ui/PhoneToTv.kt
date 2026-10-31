package com.ajit.tvhero.remote.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.LinkSettings
import com.ajit.tvhero.remote.Opened
import com.ajit.tvhero.remote.ShareActivity
import com.ajit.tvhero.remote.TvException
import com.ajit.tvhero.remote.TypingDiff
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Content
import com.ajit.tvhero.remote.ui.hig.FieldRow
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigButton
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigSheet
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.IconTile
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.Segmented
import com.ajit.tvhero.remote.ui.hig.section
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ---------- Send to TV (2.10): links, search, typing, photos. Home Wi-Fi only. ----------

private const val HOME_ONLY = "Works on the home Wi-Fi only. Nothing here goes over the internet."

/** A footnote between sections. */
private fun androidx.compose.foundation.lazy.LazyListScope.note(key: String, text: String) = item(key) {
    Text(text, style = HigType.footnote, color = Hig.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
}

/** The "Send to TV" rows on the TV tab. */
fun androidx.compose.foundation.lazy.LazyListScope.sendToTvSection(open: (String) -> Unit) {
    section("send", "Send to TV", HOME_ONLY) {
        Row("Open a Link", subtitle = "Or share any link to tv-hero", chevron = true,
            leading = { IconTile(Icons.Rounded.Link, Color(0xFF007AFF)) }) { open("link") }
        Row("Search", subtitle = "YouTube or the web, safe search on", chevron = true,
            leading = { IconTile(Icons.Rounded.Search, Color(0xFF34C759)) }) { open("search") }
        Row("Type on TV", subtitle = "Into the box that's open on the TV", chevron = true,
            leading = { IconTile(Icons.Rounded.Keyboard, Color(0xFF8E8E93)) }) { open("type") }
        Row("Show Photos", subtitle = "Streamed, never saved on the TV", chevron = true,
            leading = { IconTile(Icons.Rounded.PhotoLibrary, Color(0xFFFF9500)) }) { open("photos") }
    }
}

/**
 * Sends a link or search; when the TV says the app isn't on the child's list, asks for the parent
 * password to open it once (no list changes).
 */
private fun sendAllowingOnce(state: AppState, gate: PasswordGate, call: suspend (String?) -> Opened, done: (Opened) -> Unit) {
    state.scope.launch {
        try {
            done(call(null))
        } catch (e: TvException) {
            if (e.status == 403 && e.message?.contains("allow this once") == true) {
                gate.run("${e.message} Enter the parent password to open it this once. Their list doesn't change.") { pw -> done(call(pw)) }
            } else state.message = e.message
        } catch (e: Exception) {
            state.message = e.message ?: "Something went wrong."
        }
    }
}

private fun opened(state: AppState, o: Opened) {
    state.message = if (o.once) "Opened once in ${o.label}" else "Opened in ${o.label}"
}

private fun clipboardText(context: Context): String =
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull().orEmpty()

/** Where to open a link: YouTube or the TV's browser by default, or a browser chosen here. */
@Composable
fun LinkSheet(state: AppState, gate: PasswordGate, initial: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    var links by remember { mutableStateOf<LinkSettings?>(null) }
    var app by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { links = runCatching { state.client?.links() }.getOrNull() }
    val link = ShareActivity.webLink(text)
    HigSheet("Open on TV", onCancel = onClose, done = "Open", doneEnabled = link != null, onDone = {
        val l = link ?: return@HigSheet
        onClose()
        sendAllowingOnce(state, gate, { pw -> state.client!!.open(l, app, pw) }) { opened(state, it) }
    }) {
        section("link", footer = "Only web links (http and https). In a child's profile the link opens only in an app on their list, " +
            "unless you allow it once with the parent password. Every link is in the TV's event log.") {
            FieldRow("Link", text, { text = it }, "https://", keyboard = KeyboardType.Uri)
            if (initial.isEmpty()) ButtonRow("Paste") { text = clipboardText(ctx) }
        }
        val l = links
        if (l != null) section("where", "Open In") {
            Row(if (link != null && com.ajit.tvhero.remote.ui.isYouTubeLink(link) && l.youtube) "YouTube" else "Default (${l.browserLabel(l.defaultBrowser)})",
                subtitle = "YouTube links go to YouTube", trailing = { if (app.isEmpty()) Text("✓", style = HigType.headline, color = Hig.colors.tint) }) { app = "" }
            l.browsers.forEach { (pkg, label) ->
                Row(label, trailing = { if (app == pkg) Text("✓", style = HigType.headline, color = Hig.colors.tint) }) { app = pkg }
            }
        }
        note("home", HOME_ONLY)
    }
}

internal fun isYouTubeLink(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull() ?: return false
    return host == "youtube.com" || host == "m.youtube.com" || host == "youtu.be" || host == "music.youtube.com"
}

/** A search on the TV: in YouTube, or on the web with the watching profile's engine (safe search on). */
@Composable
fun SearchSheet(state: AppState, gate: PasswordGate, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var where by remember { mutableStateOf(0) }   // 0 YouTube, 1 web
    val go = {
        val q = query.trim()
        if (q.isNotEmpty()) {
            onClose()
            sendAllowingOnce(state, gate, { pw -> state.client!!.search(q, where == 0, pw) }) { opened(state, it) }
        }
    }
    HigSheet("Search on TV", onCancel = onClose, done = "Search", doneEnabled = query.isNotBlank(), onDone = go) {
        item("seg") { Segmented(listOf("YouTube", "Web"), where, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { where = it } }
        section("q", footer = if (where == 0) "Opens YouTube's results on the TV."
            else "Opens in the TV's browser with the watching profile's search engine. Safe search stays on: strict for children and Guest. " +
                "Choose engines in Settings → Links & Search.") {
            FieldRow("Search", query, { query = it }, "What to look for")
        }
        note("home", HOME_ONLY)
    }
}

/**
 * Type on TV: what's typed here appears in the TV's focused box as it's typed. The TV switches to
 * tv-hero Keyboard while this page is open, and back to its own keyboard when it closes.
 */
@Composable
fun TypePage(state: AppState, preview: String? = null, onBack: () -> Unit) {
    val c = Hig.colors
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(preview ?: "") }
    var sent by remember { mutableStateOf(preview ?: "") }      // what the TV has, as far as we know
    var ready by remember { mutableStateOf(preview != null) }
    var problem by remember { mutableStateOf<String?>(null) }
    val lock = remember { Mutex() }

    fun push(after: String, enter: Boolean = false) {
        state.scope.launch {
            lock.withLock {
                val d = TypingDiff.of(sent, after)
                if (d.empty && !enter) return@withLock
                try {
                    val ok = state.client?.type(d.insert, d.backspaces, enter) ?: false
                    ready = ok
                    if (ok) sent = after
                } catch (e: Exception) {
                    problem = e.message
                }
            }
        }
    }

    if (preview == null) LaunchedEffect(Unit) {
        try {
            ready = state.client?.typeStart() ?: false
            while (!ready) { delay(1500); ready = state.client?.typeStart() ?: false }
        } catch (e: Exception) {
            problem = e.message
        }
    }
    if (preview == null) DisposableEffect(Unit) { onDispose { state.scope.launch { state.client?.typeStop() } } }

    HigScreen("Type on TV", onBack = onBack, backLabel = "TV") {
        item("status") {
            Text(when {
                problem != null -> problem!!
                ready -> "Type here: it appears on the TV as you type."
                else -> "Open a text box on the TV (a search box, for example). tv-hero Keyboard is waiting for it."
            }, style = HigType.subhead, color = if (problem != null) c.destructive else c.secondaryLabel,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        }
        item("field") {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(10.dp))
                .background(c.cell).padding(14.dp)) {
                if (text.isEmpty()) Text("Text for the TV", style = HigType.body, color = c.tertiaryLabel)
                BasicTextField(text, { text = it; push(it) }, textStyle = HigType.body.copy(color = c.label), cursorBrush = SolidColor(c.tint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { push(text, enter = true) }), modifier = Modifier.fillMaxWidth())
            }
        }
        item("buttons") {
            androidx.compose.foundation.layout.Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HigButton("Enter", Modifier.weight(1f)) { push(text, enter = true) }
                HigButton("Paste", Modifier.weight(1f), prominent = false) { val t = text + clipboardText(ctx); text = t; push(t) }
                HigButton("New Text", Modifier.weight(1f), prominent = false) { text = ""; sent = "" }
            }
        }
        note("about", "While this page is open the TV uses tv-hero Keyboard, which only types into the box that has focus " +
            "and never reads it. The TV's own keyboard comes back when you leave, after 2 minutes without typing, or at standby. " +
            "New Text starts afresh here without deleting what's already on the TV. $HOME_ONLY")
    }
}

/** Photos streamed to the TV: the phone runs the slideshow; the TV saves nothing. */
@Composable
fun PhotosPage(state: AppState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val show = state.photos
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) show.start(ctx, uris)
    }
    HigScreen("Show Photos", onBack = onBack, backLabel = "TV") {
        section("pick", footer = "Each photo is made TV-sized on this phone, sent over the encrypted connection just before it shows, " +
            "and kept only in the TV's memory while it's on screen: nothing is saved on the TV, and where a photo was taken isn't sent. " +
            "Keep this app open: the TV goes back 30 seconds after the last photo. Back on the TV remote stops it too. $HOME_ONLY") {
            ButtonRow(if (show.running) "Choose Other Photos" else "Choose Photos") {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        show.problem?.let { p -> item("problem") { Text(p, style = HigType.subhead, color = Hig.colors.destructive, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) } }
        if (show.photos.isNotEmpty()) {
            section("now", "On the TV") {
                Row(if (show.running) "Photo ${show.index + 1} of ${show.photos.size}" else "Stopped",
                    subtitle = if (show.running) (if (show.paused) "Paused" else "Every ${show.seconds} seconds") else null)
                if (show.running) {
                    Content(androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HigButton("Previous", Modifier.weight(1f), prominent = false) { show.previous() }
                            HigButton(if (show.paused) "Play" else "Pause", Modifier.weight(1f)) { show.togglePause() }
                            HigButton("Next", Modifier.weight(1f), prominent = false) { show.next() }
                        }
                    }
                    ButtonRow("Stop Showing", destructive = true) { show.stop() }
                } else {
                    ButtonRow("Show Again") { show.start(ctx, show.photos) }
                }
            }
            section("speed", "Each Photo") {
                listOf(5, 8, 15, 30).forEach { s ->
                    Row("$s seconds", trailing = { if (show.seconds == s) Text("✓", style = HigType.headline, color = Hig.colors.tint) }) { show.seconds = s }
                }
            }
        }
    }
}

/** Settings → Links & Search: the TV's browser for links, and each profile's search engine. */
@Composable
fun LinksPage(state: AppState, preview: LinkSettings? = null, onBack: () -> Unit) {
    var links by remember { mutableStateOf(preview) }
    var old by remember { mutableStateOf(false) }
    if (preview == null) LaunchedEffect(Unit) { state.run { links = state.client!!.links().also { if (it == null) old = true } } }
    val family = state.family
    HigScreen("Links & Search", onBack = onBack, backLabel = "Settings") {
        val l = links
        if (old) { section("old", footer = "Update the TV app (Settings → About → TV App) to choose these.") { Row("This TV's app is older than 2.10") }; return@HigScreen }
        if (l == null) { section("l") { Row("Loading…") }; return@HigScreen }
        section("browser", "Links Open In", "Links shared from this phone open here, YouTube links in YouTube. " +
            "tv-hero can't change a browser's own settings, such as its search engine.") {
            if (l.browsers.isEmpty()) Row("No web browser on this TV")
            l.browsers.forEach { (pkg, label) ->
                Row(label, leading = { IconTile(Icons.Rounded.Language, Color(0xFF007AFF)) },
                    trailing = { if (l.defaultBrowser == pkg) Text("✓", style = HigType.headline, color = Hig.colors.tint) }) {
                    state.run { links = state.client!!.setDefaultBrowser(pkg) }
                }
            }
        }
        val profiles = buildList {
            add("parent" to "Parent")
            family?.kids?.forEach { add(it.id to it.name) }
            add("guest" to "Guest")
        }
        profiles.forEach { (id, name) ->
            section("engine-$id", name, if (id == "parent") "Safe search moderate." else "Safe search strict.") {
                l.engineChoices.forEach { (engine, label) ->
                    Row(label, trailing = { if ((l.engines[id] ?: "duckduckgo") == engine) Text("✓", style = HigType.headline, color = Hig.colors.tint) }) {
                        state.run { links = state.client!!.setSearchEngine(id, engine) }
                    }
                }
            }
        }
        note("dns", "For SafeSearch in every browser and app, also set a family DNS such as AdGuard Family (see the user guide).")
    }
}
