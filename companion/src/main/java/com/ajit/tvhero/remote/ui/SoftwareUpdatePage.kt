package com.ajit.tvhero.remote.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Content
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.ToggleRow
import com.ajit.tvhero.remote.ui.hig.section
import java.text.DateFormat
import java.util.Date

/** Settings → About → Software Update: new releases from GitHub for this phone app and the TV app. */
@Composable
internal fun SoftwareUpdatePage(state: AppState, gate: PasswordGate, onBack: () -> Unit) {
    val sw = state.software
    val ctx = LocalContext.current
    val tv = state.updater
    HigScreen("Software Update", onBack = onBack, backLabel = "Settings") {
        val rel = sw.release
        section("phone", "This Phone", "Android asks you to confirm the install. The update must be signed with tv-hero's release key, or it's refused.") {
            Row("Installed", value = sw.installedName)
            val p = sw.phoneUpdate()
            if (p != null) ButtonRow("Update to ${p.versionName}", enabled = sw.busy == null) { sw.updatePhone() }
            else if (rel != null) Row("Up to date")
        }
        section("tv", "TV", "Downloaded and checked on this phone, then installed on the TV over your home Wi-Fi with the parent password. TV debugging is switched off again at the end.") {
            Row("On the TV", value = state.tvVersion.ifBlank { "—" })
            val t = sw.tvUpdate()
            if (t != null) ButtonRow("Update the TV to ${t.versionName}", enabled = sw.busy == null && !tv.running) {
                gate.ask("Switch on TV debugging to install tv-hero ${t.versionName} on the TV.") { pw -> sw.updateTv(pw) }
            } else if (rel != null && state.tvVersion.isNotBlank()) Row("Up to date")
            if (tv.steps.isNotEmpty()) tv.steps.forEach { s -> Row("✓  $s") }
            tv.allowFingerprint?.let { fp -> Row("Press Allow on the TV", subtitle = "Its key should end in ${fp.takeLast(11)}") }
            tv.result?.let { Row(it) }
            tv.error?.let { Row(it) }
        }
        if (sw.busy != null || sw.progress >= 0f) section("busy", sw.busy ?: "Downloading") {
            if (sw.progress >= 0f) Content(PaddingValues(16.dp)) { LinearProgressIndicator(progress = { sw.progress }, modifier = Modifier.fillMaxWidth()) }
        }
        sw.error?.let { e -> section("err") { Row(e) } }
        section("check", footer = if (sw.lastChecked > 0) "Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(sw.lastChecked)) + "." else null) {
            ButtonRow(if (sw.checking) "Checking…" else "Check Now", enabled = !sw.checking) { sw.check() }
            var auto by remember { mutableStateOf(sw.autoCheck) }
            ToggleRow("Check Automatically", auto, subtitle = "Once a day, when the app opens. Off means no checks at all.") { sw.autoCheck = it; auto = it }
            if (rel != null && rel.notesUrl.isNotBlank()) ButtonRow("What's New in ${rel.version}") {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.notesUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
