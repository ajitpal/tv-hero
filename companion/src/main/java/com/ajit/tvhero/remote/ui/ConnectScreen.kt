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
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.PairedTv
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

@Composable
private fun PairScreen(state: AppState, host: String, tvName: String, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = Hig.colors
    var step by remember { mutableStateOf("starting") }   // starting | code | working | error
    var salt by remember { mutableStateOf("") }
    var passwordSet by remember { mutableStateOf(true) }
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(state.store.deviceName) }
    var error by remember { mutableStateOf<String?>(null) }
    val client = remember { TvClient(host) }

    fun start() {
        step = "starting"; error = null
        scope.launch {
            try {
                val r = client.pairStart(name)
                salt = r.getString("salt"); passwordSet = r.optBoolean("password_set", true); step = "code"
            } catch (e: TvException) { error = e.message; step = "error" }
        }
    }
    LaunchedEffect(Unit) { start() }
    val valid = code.length == 6 && pw.length >= 4 && (passwordSet || pw == pw2)

    HigScreen("Pair", onBack = onCancel, backLabel = "TVs", largeTitle = true) {
        when (step) {
            "starting" -> item("s") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Asking the TV for a code…", style = HigType.subhead, color = c.secondaryLabel, modifier = Modifier.padding(top = 12.dp))
                }
            }
            "error" -> section("e", footer = error) { ButtonRow("Try Again") { start() } }
            else -> {
                item("hint") {
                    Text("A 6-digit code is showing on $tvName now.", style = HigType.body, color = c.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }
                section("code", "Code on TV") { FieldRow("Code", code, { code = it.filter(Char::isDigit).take(6) }, "123456", keyboard = KeyboardType.NumberPassword) }
                section("pw", "Parent Password", if (passwordSet) "The password the first parent created." else
                    "Create the parent password. It's needed to allow apps and change rules, from every parent's phone. At least 4 characters.") {
                    FieldRow("Password", pw, { pw = it }, if (passwordSet) "Required" else "New password", secure = true)
                    if (!passwordSet) FieldRow("Repeat", pw2, { pw2 = it }, "Same password", secure = true)
                }
                section("name", "This Phone") { FieldRow("Name", name, { name = it.take(40) }, "e.g. Ajit's phone") }
                item("go") {
                    Column(Modifier.padding(16.dp)) {
                        if (error != null) Text(error!!, style = HigType.subhead, color = c.destructive, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                        if (!passwordSet && pw2.isNotEmpty() && pw != pw2) Text("The passwords don't match.", style = HigType.subhead,
                            color = c.destructive, modifier = Modifier.padding(bottom = 12.dp))
                        HigButton(if (step == "working") "Pairing…" else "Pair", Modifier.fillMaxWidth(), enabled = valid && step != "working") {
                            step = "working"; error = null
                            scope.launch {
                                try {
                                    val (deviceId, secret) = client.pairFinish(name, code, pw, salt)
                                    val info = client.info()
                                    state.store.deviceName = name
                                    state.passwordWorked(pw)
                                    state.paired(PairedTv(info.optString("model", tvName) + "@" + host, "tv-hero · " + info.optString("model", tvName),
                                        host, deviceId, secret, info.optString("tailscale_ip", "")))
                                } catch (e: TvException) {
                                    error = e.message; step = "code"
                                    if (e.status == 409) start()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
