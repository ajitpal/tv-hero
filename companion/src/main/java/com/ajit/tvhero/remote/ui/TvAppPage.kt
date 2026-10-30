package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.adb.Probe
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Content
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.section

/** Settings → TV App: versions, Check the TV, Update the TV App (Phase 3, M2). */
@Composable
internal fun TvAppPage(state: AppState, gate: PasswordGate, onBack: () -> Unit) {
    val c = Hig.colors
    val u = state.updater
    val p = u.probe
    var confirmStartOver by remember { mutableStateOf(false) }
    if (confirmStartOver) com.ajit.tvhero.remote.ui.hig.HigAlert("Remove tv-hero from the TV?",
        "Profiles, limits, history and paired phones on the TV are deleted, and the TV's own Home screen comes back. " +
            "TV debugging stays on so this phone can set it up again right away.", { confirmStartOver = false },
        buttons = listOf("Cancel" to { confirmStartOver = false }, "Remove" to {
            confirmStartOver = false
            gate.ask("Switch on TV debugging to remove tv-hero from the TV.") { pw -> u.startOver(pw) }
        }), destructiveIndex = 1)
    HigScreen("TV App", onBack = onBack, backLabel = "Settings") {
        section("versions", "Versions") {
            Row("On the TV", value = p?.tvHeroVersion?.ifBlank { "Not installed" } ?: state.tvVersion.ifBlank { "—" })
            Row("Inside This Phone App", value = u.bundled?.name ?: "—")
        }
        section("actions", footer = "Uses the TV's debugging connection on your home Wi-Fi, switched on with the parent password " +
            "and always switched off again at the end. Nothing is removed from the TV.") {
            ButtonRow(if (u.updateNeeded()) "Update the TV App to ${u.bundled?.name}" else "Reinstall the TV App",
                enabled = !u.running && u.bundled != null) {
                gate.ask("Switch on TV debugging to install tv-hero ${u.bundled?.name}.") { pw -> u.update(pw) }
            }
            ButtonRow("Check the TV", enabled = !u.running) {
                gate.ask("Switch on TV debugging to read what the TV is (nothing is changed).") { pw -> u.check(pw) }
            }
        }
        section("over", footer = "To try the first-time setup again: removes tv-hero from the TV (its profiles, rules and pairings too) and puts the TV's own Home screen back. Then use Set Up a New TV.") {
            ButtonRow("Start Over on This TV…", destructive = true, enabled = !u.running) { confirmStartOver = true }
        }
        u.allowFingerprint?.let { fp ->
            section("allow", "On the TV") {
                Content(PaddingValues(16.dp)) {
                    Column {
                        Text("Press Allow on the TV", style = HigType.headline)
                        Text("Tick “Always allow from this computer” first. The TV shows this phone's key; it should end in " +
                            fp.takeLast(11) + ".", style = HigType.subhead, color = c.secondaryLabel, modifier = Modifier.padding(top = 4.dp))
                        Text(fp, style = HigType.footnote, color = c.secondaryLabel, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
        if (u.steps.isNotEmpty()) section("steps", if (u.running) "Working…" else "Done") {
            u.steps.forEachIndexed { i, s ->
                val last = i == u.steps.lastIndex
                Row((if (!last || !u.running) "✓  " else "•  ") + s)
            }
            if (u.progress >= 0f) Content(PaddingValues(16.dp)) {
                LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        u.result?.let { r -> section("ok") { Row(r) } }
        u.error?.let { e -> section("err", "Didn't Finish") { Row(e) } }
        if (p != null) section("tv", "This TV", "Setup level: what tv-hero can do on this TV. Full: tv-hero is Home with every feature. " +
            "Guard only: the TV keeps its own home; profiles, limits and lock screens still work.") {
            Row("TV", value = p.describe())
            Row("Setup Level", value = when (p.level()) {
                Probe.Level.FULL -> "Full"; Probe.Level.GUARD_ONLY -> "Guard only"
                Probe.Level.REMOTE_ONLY -> "Remote only"; else -> "Not supported (Android 8 or older)"
            })
            Row("Guard", value = if (p.guardEnabled) "On" else "Off")
            Row("Home Screen", value = if (p.tvHeroIsHome()) "tv-hero" else p.home.substringBefore('/').ifBlank { "—" })
            Row("Screensaver", value = if (p.screensaver.startsWith("com.ajit.tvhero/")) "tv-hero clock" else p.screensaver.substringBefore('/').ifBlank { "—" })
            Row("Memory", value = "${p.ramMb} MB" + if (p.lowRam) " (low-RAM)" else "")
            Row("Free Storage", value = if (p.freeStorageMb >= 0) "${p.freeStorageMb} MB" else "—")
            Row("Firmware", value = p.build)
        }
    }
}
