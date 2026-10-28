package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.TvApp
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.SearchField
import com.ajit.tvhero.remote.ui.hig.ToggleRow
import com.ajit.tvhero.remote.ui.hig.section

@Composable
fun AppsTab(state: AppState, gate: PasswordGate) {
    var query by remember { mutableStateOf("") }
    val c = Hig.colors
    val pending = state.apps.filter { it.state == "pending" }
    val list = state.apps.filter { it.state != "pending" && (query.isBlank() || it.label.contains(query, true)) }

    fun allow(a: TvApp, approve: Boolean) = gate.run(if (approve) "Approve ${a.label} on the TV." else "Allow ${a.label} on the TV.",
        after = { state.refresh() }) { pw -> state.client!!.allow(a.pkg, pw, approve) }
    fun block(a: TvApp) = state.run(onDone = { if (it) state.refresh() }) { state.client!!.block(a.pkg) }

    HigScreen("Apps") {
        item("search") { SearchField(query, { query = it }, "Search Apps", Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        if (pending.isNotEmpty()) section("pending", "Waiting for Approval", "These were installed on the TV and stay blocked until you decide.") {
            pending.forEach { a ->
                Row(a.label, "New on the TV", leading = { AppIcon(state, a.pkg, 32) }) {
                    androidx.compose.foundation.layout.Row {
                        Text("Block", style = HigType.body, color = c.destructive, modifier = Modifier.clickable { block(a) }.padding(8.dp))
                        Text("Approve", style = HigType.headline, color = c.tint, modifier = Modifier.clickable { allow(a, true) }.padding(8.dp))
                    }
                }
            }
        }
        section("rule", footer = "When on, anything installed on the TV, from the Play Store or elsewhere, waits for a parent's approval.") {
            ToggleRow("Approve New Apps", state.blockNewInstalls) { on ->
                if (on) state.run(onDone = { if (it) state.refresh() }) { state.client!!.setBlockNewInstalls(true, null) }
                else gate.run("Let new apps open without approval.", after = { state.refresh() }) { pw -> state.client!!.setBlockNewInstalls(false, pw) }
            }
        }
        section("apps", "On the TV", "Blocked apps disappear from the TV's home screen and can't be opened by anyone. Unblocking needs the parent password.") {
            if (state.apps.isEmpty()) Row("Loading apps…")
            list.forEach { a ->
                ToggleRow(a.label, a.state == "allowed",
                    subtitle = if (a.protected) "Needed by the TV" else if (a.state == "blocked") "Blocked" else null,
                    enabled = !a.protected, leading = { AppIcon(state, a.pkg, 32) }) { on -> if (on) allow(a, false) else block(a) }
            }
        }
    }
}

@Composable
fun AppIcon(state: AppState, pkg: String, size: Int = 32) {
    val bmp = state.icons[pkg]
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.225f).dp)).background(Hig.colors.fill)) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(size.dp))
    }
}
