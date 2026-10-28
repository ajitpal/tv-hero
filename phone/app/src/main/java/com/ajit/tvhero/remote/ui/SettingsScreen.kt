package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import org.json.JSONArray
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(state: AppState, gate: PasswordGate) {
    var devices by remember { mutableStateOf(JSONArray()) }
    var log by remember { mutableStateOf(JSONArray()) }
    var changing by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val labels = state.apps.associate { it.pkg to it.label }

    fun load() = state.run { devices = state.client!!.devices(); log = state.client!!.log() }
    LaunchedEffect(state.tv) { load() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("This TV")
        Text(state.tv?.name ?: "", style = MaterialTheme.typography.titleMedium)
        Text(state.tv?.host ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section("Parent password")
        OutlinedButton(onClick = { changing = true }) { Text("Change Password") }

        Section("Phones that can control this TV")
        for (i in 0 until devices.length()) {
            val d = devices.getJSONObject(i)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.optString("name"))
                    Text("Paired " + DateFormat.getDateInstance().format(Date(d.optLong("paired_at"))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    gate.ask("Remove ${d.optString("name")} from this TV.") { pw ->
                        state.run(onDone = { if (it) { state.passwordWorked(pw); load() } }) { state.client!!.removeDevice(d.getString("id"), pw) }
                    }
                }) { Text("Remove") }
            }
        }
        Text("To add another parent's phone, install tv-hero on it and pair with the code the TV shows. It needs the parent password.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section("Recent activity on the TV")
        if (log.length() == 0) Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (i in (log.length() - 1) downTo maxOf(0, log.length() - 25)) {
            val e = log.getJSONObject(i)
            val pkg = e.optString("package")
            val what = when (e.optString("type")) {
                "opened_blocked" -> "Someone tried to open ${labels[pkg] ?: pkg} (blocked)"
                "opened_pending" -> "Someone tried to open ${labels[pkg] ?: pkg} (waiting for approval)"
                "installed_pending" -> "${labels[pkg] ?: pkg} was installed and is waiting for approval"
                "installed" -> "${labels[pkg] ?: pkg} was installed"
                "removed" -> "$pkg was uninstalled"
                "blocked" -> "${labels[pkg] ?: pkg} was blocked"
                "allowed" -> "${labels[pkg] ?: pkg} was allowed"
                "paired" -> "$pkg was paired with the TV"
                else -> e.optString("type") + " " + pkg
            }
            Column {
                Text(what)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e.optLong("t"))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Section("This phone")
        OutlinedButton(onClick = { confirmForget = true }) { Text("Forget This TV") }
    }

    if (changing) {
        var old by remember { mutableStateOf("") }
        var new by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { changing = false }, title = { Text("Change parent password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(old, { old = it }, label = { Text("Current password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    OutlinedTextField(new, { new = it }, label = { Text("New password (4+ characters)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                Button(enabled = old.isNotEmpty() && new.length >= 4, onClick = {
                    changing = false
                    state.run(onDone = { if (it) { state.passwordWorked(new); state.message = "Password changed." } }) {
                        state.client!!.changePassword(old, new)
                    }
                }) { Text("Change") }
            },
            dismissButton = { TextButton(onClick = { changing = false }) { Text("Cancel") } })
    }
    if (confirmForget) {
        AlertDialog(onDismissRequest = { confirmForget = false }, title = { Text("Forget this TV?") },
            text = { Text("This phone will need to pair again to control the TV. The TV keeps its rules.") },
            confirmButton = { TextButton(onClick = { confirmForget = false; state.forgetTv() }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } })
    }
}
