package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.ajit.tvhero.protocol.Pairing
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.PairedTv
import com.ajit.tvhero.remote.PairingSession
import com.ajit.tvhero.remote.TvClient
import com.ajit.tvhero.remote.TvException
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.FieldRow
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigButton
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.IconTile
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.section
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** First run (or after forgetting a TV): find the TV on the Wi-Fi and pair with the code it shows. */
@Composable
fun ConnectScreen(state: AppState) {
    val found by state.discovery.found.collectAsState()
    val paired = remember { state.store.tvs() }
    var manual by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf<Pair<String, String>?>(null) }
    pairing?.let { (host, name) -> PairScreen(state, host, name) { pairing = null }; return }
    val c = Hig.colors

    HigScreen("tv-hero") {
        item("intro") {
            Text("Control your TV from this phone: apps, kids' screen time, bedtime, the remote and a game controller.",
                style = HigType.body, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (paired.isNotEmpty()) section("paired", "Paired TVs") {
            paired.forEach { tv -> Row(tv.name.removePrefix("tv-hero · "), tv.host, leading = { IconTile(Icons.Rounded.Tv, c.tint) }, chevron = true) { state.select(tv) } }
        }
        section("found", "TVs on This Wi-Fi", "Your phone and the TV need to be on the same Wi-Fi to pair. After that, the app can also reach the TV over the internet.") {
            if (found.isEmpty()) Row("Looking for tv-hero TVs…", trailing = { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp)) })
            found.forEach { tv -> Row(tv.name, tv.host, leading = { IconTile(Icons.Rounded.Tv, c.tint) }, chevron = true) { pairing = tv.host to tv.name } }
        }
        section("manual", "Or Enter the TV's Address") {
            FieldRow("Address", manual, { manual = it.trim() }, "192.168.1.50", keyboard = KeyboardType.Uri)
            ButtonRow("Pair", enabled = manual.isNotEmpty()) { pairing = manual to "TV" }
        }
    }
}

/**
 * Pairing by code comparison (docs/SECURITY.md):
 *  1. name → the phone and TV swap certificates; both show the same 6-digit code
 *  2. the person checks the codes match and presses Allow on the TV (the phone waits)
 *  3. the parent password goes to the TV over the now-encrypted, pinned channel
 */
@Composable
private fun PairScreen(state: AppState, host: String, tvName: String, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = Hig.colors
    var step by remember { mutableStateOf("name") }   // name | starting | compare | password | working | error
    var session by remember { mutableStateOf<PairingSession?>(null) }
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(state.store.deviceName) }
    var error by remember { mutableStateOf<String?>(null) }
    val open = remember { TvClient(host) }

    fun fail(msg: String?) { error = msg; step = "error" }

    fun start() {
        step = "starting"; error = null
        scope.launch {
            try {
                val p = open.pairStart(name.ifBlank { "Phone" })
                code = open.pairReveal(p)
                session = p
                step = "compare"
                while (step == "compare") {
                    delay(1000)
                    when (open.pairStatus(p)) {
                        "allowed" -> step = "password"
                        "denied" -> fail("Someone chose Don't Allow on the TV.")
                        "expired", "none" -> fail("The code expired. Start again.")
                    }
                }
            } catch (e: TvException) { fail(e.message) }
        }
    }

    val p = session
    val valid = if (p?.passwordSet != false) pw.isNotEmpty() else pw.length >= 6 && pw == pw2

    HigScreen("Pair", onBack = onCancel, backLabel = "TVs", largeTitle = true) {
        when (step) {
            "name" -> {
                item("hint") {
                    Text("The TV will show a code and ask whether to allow this phone. You'll need the TV remote nearby.",
                        style = HigType.body, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp))
                }
                section("name", "This Phone", "This name appears on the TV and in the list of phones that can control it.") {
                    FieldRow("Name", name, { name = it.take(40) }, "e.g. Ajit's phone")
                }
                item("go") {
                    HigButton("Show Code on TV", Modifier.fillMaxWidth().padding(16.dp), enabled = name.isNotBlank()) { start() }
                }
            }
            "starting" -> item("s") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Contacting $tvName…", style = HigType.subhead, color = c.secondaryLabel, modifier = Modifier.padding(top = 12.dp))
                }
            }
            "compare" -> item("compare") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Does the TV show this code?", style = HigType.headline, color = c.label, textAlign = TextAlign.Center)
                    Text(Pairing.display(code), style = HigType.largeTitle.copy(fontSize = 56.sp, lineHeight = 64.sp, letterSpacing = 4.sp),
                        color = c.label, modifier = Modifier.padding(vertical = 24.dp).semantics { contentDescription = "Code " + code.toList().joinToString(" ") })
                    Text("If it matches, press Allow on the TV with the remote.\nIf it doesn't match, choose Don't Allow on the TV.",
                        style = HigType.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center)
                    androidx.compose.foundation.layout.Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Waiting for the TV…", style = HigType.subhead, color = c.secondaryLabel, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
            "error" -> {
                item("err") {
                    Text(error ?: "Pairing stopped.", style = HigType.body, color = c.label, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp))
                }
                section("again") { ButtonRow("Try Again") { start() } }
            }
            else -> {   // password | working
                item("ok") {
                    Text("Allowed on the TV. The connection is now encrypted and tied to this TV.",
                        style = HigType.body, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 16.dp))
                }
                section("pw", "Parent Password", if (p?.passwordSet != false) "The password the first parent created." else
                    "Create the parent password. It's needed to allow apps, change rules and add phones, from every parent's phone. At least 6 characters.") {
                    FieldRow("Password", pw, { pw = it }, if (p?.passwordSet != false) "Required" else "New password", secure = true)
                    if (p?.passwordSet == false) FieldRow("Repeat", pw2, { pw2 = it }, "Same password", secure = true)
                }
                item("go") {
                    Column(Modifier.padding(16.dp)) {
                        if (error != null) Text(error!!, style = HigType.subhead, color = c.destructive, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                        if (p?.passwordSet == false && pw2.isNotEmpty() && pw != pw2) Text("The passwords don't match.", style = HigType.subhead,
                            color = c.destructive, modifier = Modifier.padding(bottom = 12.dp))
                        HigButton(if (step == "working") "Pairing…" else "Finish", Modifier.fillMaxWidth(), enabled = valid && step != "working" && p != null) {
                            val s = p ?: return@HigButton
                            step = "working"; error = null
                            scope.launch {
                                try {
                                    val secure = TvClient(host, s.tvFingerprint)
                                    val deviceId = secure.pairComplete(pw)
                                    val info = secure.info()
                                    state.store.deviceName = name
                                    state.passwordWorked(pw)
                                    val model = info.optString("model", tvName)
                                    state.paired(PairedTv(s.tvFingerprint.take(16), "tv-hero · $model", host, deviceId, s.tvFingerprint,
                                        info.optString("tailscale_ip", "")))
                                } catch (e: TvException) {
                                    error = e.message; step = "password"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
