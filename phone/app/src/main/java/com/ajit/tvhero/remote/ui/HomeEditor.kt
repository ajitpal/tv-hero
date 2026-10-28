package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.HomeLayout

/** Changes the TV's home screen: the app row, its size, the greeting and hidden apps. */
@Composable
fun HomeEditor(state: AppState) {
    val current = state.home
    if (current == null) {
        Text("Loading the TV's home screen…", Modifier.padding(16.dp))
        return
    }
    var dock by remember(current) { mutableStateOf(current.dock) }
    var hidden by remember(current) { mutableStateOf(current.hidden) }
    var columns by remember(current) { mutableStateOf(current.columns.toFloat()) }
    var greeting by remember(current) { mutableStateOf(current.greeting) }
    var showGreeting by remember(current) { mutableStateOf(current.showGreeting) }
    var adding by remember { mutableStateOf(false) }
    val labels = state.apps.associate { it.pkg to it.label }
    val choosable = state.apps.filter { it.state == "allowed" && it.pkg !in dock }
    val changed = HomeLayout(dock, hidden, columns.toInt(), greeting, showGreeting) != current

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("Home row")
        Text("These apps sit in the row at the bottom of the TV's home screen, in this order.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        dock.forEachIndexed { i, pkg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(state, pkg, 36)
                Text(labels[pkg] ?: pkg, Modifier.weight(1f).padding(horizontal = 12.dp))
                IconButton(enabled = i > 0, onClick = { dock = dock.toMutableList().apply { add(i - 1, removeAt(i)) } }) {
                    Icon(Icons.Outlined.KeyboardArrowUp, "Move up")
                }
                IconButton(enabled = i < dock.size - 1, onClick = { dock = dock.toMutableList().apply { add(i + 1, removeAt(i)) } }) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "Move down")
                }
                IconButton(onClick = { dock = dock - pkg }) { Icon(Icons.Outlined.Close, "Remove from row") }
            }
        }
        Row {
            OutlinedButton(onClick = { adding = true }, enabled = choosable.isNotEmpty()) {
                Icon(Icons.Outlined.Add, null); Text("Add app", Modifier.padding(start = 6.dp))
            }
            DropdownMenu(adding, onDismissRequest = { adding = false }) {
                choosable.forEach { a ->
                    DropdownMenuItem(text = { Text(a.label) }, leadingIcon = { AppIcon(state, a.pkg, 28) },
                        onClick = { dock = dock + a.pkg; adding = false })
                }
            }
        }

        Section("Apps per row: ${columns.toInt()}")
        Slider(columns, { columns = it }, valueRange = 4f..7f, steps = 2)

        Section("Greeting")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show greeting", Modifier.weight(1f))
            Switch(showGreeting, { showGreeting = it })
        }
        OutlinedTextField(greeting, { greeting = it.take(40) }, singleLine = true, enabled = showGreeting,
            label = { Text("Name after \"Good evening\"") }, modifier = Modifier.fillMaxWidth())

        Section("Hidden from home screen")
        Text("Hidden apps still work; they just don't show on Home. Blocked apps are always hidden.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.apps.filter { it.state == "allowed" }.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(a.pkg in hidden, { on -> hidden = if (on) hidden + a.pkg else hidden - a.pkg })
                Text(a.label)
            }
        }

        Button(enabled = changed, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
            state.run { state.home = state.client!!.saveHome(HomeLayout(dock, hidden, columns.toInt(), greeting, showGreeting)) }
        }) { Text("Save to TV") }
    }
}
