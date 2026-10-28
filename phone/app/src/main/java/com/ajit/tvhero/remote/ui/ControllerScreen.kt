package com.ajit.tvhero.remote.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.HidController
import com.ajit.tvhero.remote.HidController.Companion as K

/** Bluetooth set-up for the remote, and the game controller itself. */
@SuppressLint("MissingPermission")
@Composable
fun ControllerScreen(state: AppState, activity: Activity) {
    val hid = state.hid
    val status by hid.status.collectAsState()
    val needed = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE) else emptyArray()
    fun granted() = needed.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    val tick = remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick.intValue++
        if (granted()) hid.start()
    }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tick.intValue
                Text("Bluetooth remote and controller", style = MaterialTheme.typography.titleMedium)
                when (val s = status) {
                    is HidController.Status.Connected -> Text("Connected to ${s.name}. The TV sees this phone as a keyboard, remote and game pad.")
                    HidController.Status.Ready -> Text("Ready. Connect to the TV below, or pair it the first time.")
                    HidController.Status.Starting -> Text("Starting…")
                    is HidController.Status.Unavailable -> Text(s.why, color = MaterialTheme.colorScheme.error)
                    HidController.Status.Off -> Text("Off. Turn it on to use the arrows, volume, typing and the game controller.")
                }
                when {
                    !granted() -> Button(onClick = { ask.launch(needed) }) { Text("Allow Bluetooth") }
                    status is HidController.Status.Off || status is HidController.Status.Unavailable ->
                        Button(onClick = { hid.start() }) { Text("Turn On") }
                    status is HidController.Status.Ready -> {
                        Text("First time: tap Make Visible, then on the TV open Settings → Remotes & Accessories → Add accessory and pick \"tv-hero remote\" / this phone.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = {
                            discoverable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
                        }) { Text("Make Visible for 2 Minutes") }
                        val bonded = hid.bondedDevices()
                        if (bonded.isNotEmpty()) Text("Already paired — connect to:", style = MaterialTheme.typography.bodySmall)
                        bonded.forEach { d -> OutlinedButton(onClick = { hid.connect(d) }) { Text(d.name ?: d.address) } }
                    }
                    else -> OutlinedButton(onClick = { hid.stop() }) { Text("Turn Off") }
                }
            }
        }

        if (status is HidController.Status.Connected) GamePad(hid)
        else Text("The game controller appears here once the TV is connected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GamePad(hid: HidController) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HoldButton("L1", size = 64.dp, round = false, onDown = { hid.button(K.BTN_L1, true) }, onUp = { hid.button(K.BTN_L1, false) })
            HoldButton("R1", size = 64.dp, round = false, onDown = { hid.button(K.BTN_R1, true) }, onUp = { hid.button(K.BTN_R1, false) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(168.dp)) {   // D-pad (hat): 0 up, 2 right, 4 down, 6 left
                Box(Modifier.align(Alignment.TopCenter)) { HoldButton(icon = Icons.Outlined.KeyboardArrowUp, size = 56.dp, round = false, contentDescription = "Up", onDown = { hid.dpad(0) }, onUp = { hid.dpad(-1) }) }
                Box(Modifier.align(Alignment.CenterEnd)) { HoldButton(icon = Icons.Outlined.KeyboardArrowRight, size = 56.dp, round = false, contentDescription = "Right", onDown = { hid.dpad(2) }, onUp = { hid.dpad(-1) }) }
                Box(Modifier.align(Alignment.BottomCenter)) { HoldButton(icon = Icons.Outlined.KeyboardArrowDown, size = 56.dp, round = false, contentDescription = "Down", onDown = { hid.dpad(4) }, onUp = { hid.dpad(-1) }) }
                Box(Modifier.align(Alignment.CenterStart)) { HoldButton(icon = Icons.Outlined.KeyboardArrowLeft, size = 56.dp, round = false, contentDescription = "Left", onDown = { hid.dpad(6) }, onUp = { hid.dpad(-1) }) }
            }
            Box(Modifier.size(168.dp)) {   // face buttons, Xbox-style placement
                Box(Modifier.align(Alignment.TopCenter)) { Face("Y", Color(0xFFE8B931), K.BTN_Y, hid) }
                Box(Modifier.align(Alignment.CenterStart)) { Face("X", Color(0xFF3D7BFF), K.BTN_X, hid) }
                Box(Modifier.align(Alignment.CenterEnd)) { Face("B", Color(0xFFE5484D), K.BTN_B, hid) }
                Box(Modifier.align(Alignment.BottomCenter)) { Face("A", Color(0xFF30A46C), K.BTN_A, hid) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HoldButton("Select", size = 72.dp, round = false, onDown = { hid.button(K.BTN_SELECT, true) }, onUp = { hid.button(K.BTN_SELECT, false) })
            HoldButton("Home", size = 72.dp, round = false, onDown = { hid.button(K.BTN_MODE, true) }, onUp = { hid.button(K.BTN_MODE, false) })
            HoldButton("Start", size = 72.dp, round = false, onDown = { hid.button(K.BTN_START, true) }, onUp = { hid.button(K.BTN_START, false) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stick { x, y -> hid.stick(0, x, y) }
            Stick { x, y -> hid.stick(1, x, y) }
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun Face(label: String, color: Color, button: Int, hid: HidController) {
    HoldButton(label, size = 56.dp, color = color.copy(alpha = 0.85f),
        onDown = { hid.button(button, true) }, onUp = { hid.button(button, false) })
}
