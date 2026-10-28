package com.ajit.tvhero.remote.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.ajit.tvhero.remote.AppState

enum class Tab(val title: String) { Apps("Apps"), Home("Home screen"), Remote("Remote"), Controller("Controller"), Settings("Settings") }

/**
 * Asks for the parent password (or reuses one that worked in the last 5 minutes), then runs
 * [action] with it. Screens call [PasswordGate.ask]; the dialog lives at the root.
 */
class PasswordGate(private val state: AppState) {
    var request by mutableStateOf<Pair<String, (String) -> Unit>?>(null)

    fun ask(reason: String, action: (String) -> Unit) {
        val pw = state.rememberedPassword()
        if (pw != null) action(pw) else request = reason to action
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Root(state: AppState, activity: Activity) {
    val gate = remember { PasswordGate(state) }
    if (state.tv == null) {
        ConnectScreen(state)
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.Apps) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); state.message = null }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (tab == Tab.Apps) state.tv?.name ?: "tv-hero" else tab.title) })
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = {
                            Icon(when (t) {
                                Tab.Apps -> Icons.Outlined.Apps
                                Tab.Home -> Icons.Outlined.Home
                                Tab.Remote -> Icons.Outlined.SettingsRemote
                                Tab.Controller -> Icons.Outlined.SportsEsports
                                Tab.Settings -> Icons.Outlined.Settings
                            }, contentDescription = null)
                        },
                        label = { Text(t.title, maxLines = 1) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Apps -> AppsScreen(state, gate)
                Tab.Home -> HomeEditor(state)
                Tab.Remote -> RemoteScreen(state, activity) { tab = Tab.Controller }
                Tab.Controller -> ControllerScreen(state, activity)
                Tab.Settings -> SettingsScreen(state, gate)
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    gate.request?.let { (reason, action) ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { gate.request = null },
            title = { Text("Parent password") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(reason)
                    OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.padding(top = androidx.compose.ui.unit.Dp(12f)))
                }
            },
            confirmButton = {
                TextButton(enabled = pw.isNotEmpty(), onClick = { gate.request = null; action(pw) }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { gate.request = null }) { Text("Cancel") } },
        )
    }
}
