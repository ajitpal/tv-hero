package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.NetScan
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Content
import com.ajit.tvhero.remote.ui.hig.FieldRow
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigButton
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.section
import kotlinx.coroutines.launch

/**
 * Set Up a New TV (Phase 3): from a TV without tv-hero to a paired TV, with only this phone.
 * 1. The person switches on debugging at the TV (shown step by step).
 * 2. The phone finds it, the person presses Allow on the TV, and the phone installs and sets up tv-hero.
 * 3. Pairing follows as usual.
 */
@Composable
internal fun SetupNewTvScreen(state: AppState, onBack: () -> Unit) {
    val c = Hig.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val u = state.updater
    var host by remember { mutableStateOf(u.lastHost) }
    var found by remember { mutableStateOf<List<String>?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var pairing by remember { mutableStateOf(false) }
    if (pairing) { PairScreen(state, host, u.probe?.describe() ?: "TV") { pairing = false }; return }

    HigScreen("Set Up a New TV", onBack = onBack, backLabel = "Back", largeTitle = true) {
        item("intro") {
            Text("This phone installs tv-hero on the TV over your Wi-Fi. No computer and no cables. It takes about two minutes.",
                style = HigType.body, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp))
        }
        section("tv", "1 · On the TV, with the remote",
            "Menus differ a little by brand. On Google TV: Settings → System → About → Android TV OS build. " +
                "On older TVs that only offer USB debugging, connect once from a computer instead (see the website).") {
            Row("Open Developer options", subtitle = "Settings → Device Preferences → About → select Build 7 times, until it says you're a developer.")
            Row("Turn on debugging", subtitle = "Back to Device Preferences → Developer options → USB debugging On (and Network debugging, if it's listed).")
            Row("Same Wi-Fi", subtitle = "The TV and this phone must be on the same Wi-Fi network.")
        }
        section("find", "2 · Find the TV", "Only this phone's own Wi-Fi network is searched.") {
            ButtonRow(if (scanning) "Searching…" else "Find TVs With Debugging On", enabled = !scanning && !u.running) {
                scanning = true
                scope.launch { found = runCatching { NetScan.findDebuggableTvs(ctx) }.getOrDefault(emptyList()); scanning = false }
            }
            found?.let { list ->
                if (list.isEmpty()) Row("None found yet", subtitle = "Check debugging is on, or type the TV's address below.")
                list.forEach { h -> Row(h, value = if (h == host) "✓" else "", chevron = h != host) { host = h } }
            }
            FieldRow("Address", host, { host = it.trim() }, "TV's IP address", keyboard = KeyboardType.Uri)
        }
        section("go", "3 · Set it up") {
            ButtonRow("Set Up tv-hero on This TV", enabled = host.isNotBlank() && !u.running && !u.setUpDone) { u.setUpNew(host) }
        }
        u.allowFingerprint?.let { fp ->
            section("allow", "On the TV") {
                Content(PaddingValues(16.dp)) {
                    Column {
                        Text("Press Allow on the TV", style = HigType.headline)
                        Text("Tick “Always allow from this computer”, then Allow. The TV shows this phone's key; it should end in ${fp.takeLast(11)}.",
                            style = HigType.subhead, color = c.secondaryLabel, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (u.steps.isNotEmpty()) section("steps", if (u.running) "Working…" else "Steps") {
            u.steps.forEachIndexed { i, s -> Row((if (i < u.steps.lastIndex || !u.running) "✓  " else "•  ") + s) }
            if (u.progress >= 0f) Content(PaddingValues(16.dp)) { LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth()) }
            if (u.running && u.progress < 0f) Content(PaddingValues(16.dp)) { CircularProgressIndicator(strokeWidth = 2.dp) }
        }
        u.error?.let { e -> section("err", "Didn't Finish", "Nothing is left half-done: a failed step puts back what it changed.") { Row(e) } }
        if (u.setUpDone) {
            section("done", "Ready", u.setUpNote.ifBlank { null }) { u.result?.let { Row("✓  $it") } }
            item("pair") { HigButton("Pair This Phone With the TV", Modifier.fillMaxWidth().padding(16.dp)) { pairing = true } }
        }
    }
}
