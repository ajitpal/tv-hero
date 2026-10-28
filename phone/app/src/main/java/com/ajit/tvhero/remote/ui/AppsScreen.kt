package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.TvApp

@Composable
fun AppsScreen(state: AppState, gate: PasswordGate) {
    var query by remember { mutableStateOf("") }
    val pending = state.apps.filter { it.state == "pending" }
    val others = state.apps.filter { it.state != "pending" && (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)) }

    fun allow(a: TvApp, approve: Boolean) = gate.ask(if (approve) "Approve ${a.label} on the TV." else "Unblock ${a.label} on the TV.") { pw ->
        state.run(onDone = { ok -> if (ok) { state.passwordWorked(pw); state.refresh() } }) { state.client!!.allow(a.pkg, pw, approve) }
    }
    fun block(a: TvApp) = state.run(onDone = { if (it) state.refresh() }) { state.client!!.block(a.pkg) }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hold new apps for approval", style = MaterialTheme.typography.titleSmall)
                        Text("Apps installed on the TV stay blocked until a parent approves them here.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(state.blockNewInstalls, onCheckedChange = { on ->
                        if (on) state.run(onDone = { if (it) state.refresh() }) { state.client!!.setBlockNewInstalls(true, null) }
                        else gate.ask("Turn off approval for new apps.") { pw ->
                            state.run(onDone = { ok -> if (ok) { state.passwordWorked(pw); state.refresh() } }) {
                                state.client!!.setBlockNewInstalls(false, pw)
                            }
                        }
                    })
                }
            }
        }
        if (pending.isNotEmpty()) {
            item { Section("Waiting for approval (${pending.size})") }
            items(pending, key = { "p-" + it.pkg }) { a ->
                Card {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(state, a.pkg)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(a.label, style = MaterialTheme.typography.titleSmall)
                            Text("New app, blocked until you approve it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { block(a) }) { Text("Block") }
                        Button(onClick = { allow(a, true) }, modifier = Modifier.padding(start = 8.dp)) { Text("Approve") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Search apps") }, modifier = Modifier.weight(1f))
                IconButton(onClick = { state.refresh() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
            }
        }
        item { Section("Apps on the TV") }
        if (state.apps.isEmpty() && !state.loading) item {
            Text("No apps yet. Pull the list with Refresh; the TV must be on.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(others, key = { it.pkg }) { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(state, a.pkg)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        a.protected -> "Always allowed (needed by the TV)"
                        a.state == "blocked" -> "Blocked"
                        else -> "Allowed"
                    }, style = MaterialTheme.typography.bodySmall,
                        color = if (a.state == "blocked") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = a.state == "allowed", enabled = !a.protected, onCheckedChange = { on ->
                    if (on) allow(a, false) else block(a)
                })
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun AppIcon(state: AppState, pkg: String, size: Int = 40) {
    val bmp = state.icons[pkg]
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(size.dp))
    }
}
