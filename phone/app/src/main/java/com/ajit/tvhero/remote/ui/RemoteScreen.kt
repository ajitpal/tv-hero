package com.ajit.tvhero.remote.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.HidController

/**
 * The remote. Arrows, OK, volume and typing go over Bluetooth (the phone acts as a keyboard
 * and media remote). Home, Back and "open an app" also work over Wi-Fi through the TV app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(state: AppState, activity: Activity, openController: () -> Unit) {
    val bt by state.hid.status.collectAsState()
    val connected = bt is HidController.Status.Connected
    val hid = state.hid
    var text by remember { mutableStateOf("") }

    fun home() = if (connected) hid.tapConsumer(HidController.CC_HOME) else state.run { state.client!!.key("home") }
    fun back() = if (connected) hid.tapConsumer(HidController.CC_BACK) else state.run { state.client!!.key("back") }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (!connected) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().clickable(onClick = openController)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Arrows, volume and typing need Bluetooth", style = MaterialTheme.typography.titleSmall)
                    Text("Pair this phone with the TV once as a Bluetooth remote (Controller tab). Home, Back and opening apps work over Wi-Fi now.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = openController) { Text("Set up Bluetooth") }
                }
            }
        }

        // D-pad
        Box(Modifier.size(240.dp)) {
            Box(Modifier.align(Alignment.TopCenter)) { HoldButton(icon = Icons.Outlined.KeyboardArrowUp, size = 72.dp, contentDescription = "Up", onDown = { hid.tapKey(HidController.KEY_UP) }) }
            Box(Modifier.align(Alignment.BottomCenter)) { HoldButton(icon = Icons.Outlined.KeyboardArrowDown, size = 72.dp, contentDescription = "Down", onDown = { hid.tapKey(HidController.KEY_DOWN) }) }
            Box(Modifier.align(Alignment.CenterStart)) { HoldButton(icon = Icons.Outlined.KeyboardArrowLeft, size = 72.dp, contentDescription = "Left", onDown = { hid.tapKey(HidController.KEY_LEFT) }) }
            Box(Modifier.align(Alignment.CenterEnd)) { HoldButton(icon = Icons.Outlined.KeyboardArrowRight, size = 72.dp, contentDescription = "Right", onDown = { hid.tapKey(HidController.KEY_RIGHT) }) }
            Box(Modifier.align(Alignment.Center)) {
                HoldButton(label = "OK", size = 88.dp, color = MaterialTheme.colorScheme.primaryContainer, onDown = { hid.tapKey(HidController.KEY_ENTER) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HoldButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", onDown = { back() })
            HoldButton(icon = Icons.Outlined.Home, contentDescription = "Home", onDown = { home() })
            HoldButton(icon = Icons.Outlined.Menu, contentDescription = "Menu", onDown = { hid.tapConsumer(HidController.CC_MENU) })
            HoldButton(icon = Icons.Outlined.PlayArrow, contentDescription = "Play or pause", onDown = { hid.tapConsumer(HidController.CC_PLAY_PAUSE) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            HoldButton(icon = Icons.Outlined.Remove, contentDescription = "Volume down", onDown = { hid.tapConsumer(HidController.CC_VOL_DOWN) })
            Text("Volume", color = MaterialTheme.colorScheme.onSurfaceVariant)
            HoldButton(icon = Icons.Outlined.Add, contentDescription = "Volume up", onDown = { hid.tapConsumer(HidController.CC_VOL_UP) })
            HoldButton(icon = Icons.AutoMirrored.Outlined.VolumeOff, contentDescription = "Mute", onDown = { hid.tapConsumer(HidController.CC_MUTE) })
        }

        Section("Type on the TV")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, singleLine = true, enabled = connected,
                placeholder = { Text(if (connected) "Search text, passwords…" else "Needs Bluetooth") }, modifier = Modifier.weight(1f))
            TextButton(enabled = connected && text.isNotEmpty(), onClick = { hid.type(text); text = "" }) { Text("Send") }
            TextButton(enabled = connected, onClick = { hid.tapKey(HidController.KEY_BACKSPACE) }) { Text("⌫") }
        }

        Section("Open on TV")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.apps.filter { it.state == "allowed" }.forEach { a ->
                Column(Modifier.width(76.dp).clickable { state.run { state.client!!.launch(a.pkg) } },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(state, a.pkg, 52)
                    Text(a.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}
