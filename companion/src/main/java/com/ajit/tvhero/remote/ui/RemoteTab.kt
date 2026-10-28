package com.ajit.tvhero.remote.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.HidController
import com.ajit.tvhero.remote.HidController.Companion as K
import com.ajit.tvhero.remote.ui.hig.AlertField
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigAlert
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.IconTile
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.Segmented
import com.ajit.tvhero.remote.ui.hig.section
import kotlin.math.abs
import kotlin.math.hypot

@SuppressLint("MissingPermission")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteTab(state: AppState, activity: Activity) {
    var mode by remember { mutableIntStateOf(0) }
    var typing by remember { mutableStateOf(false) }
    val hid = state.hid
    val bt by hid.status.collectAsState()
    val connected = bt is HidController.Status.Connected
    val c = Hig.colors

    val needed = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE) else emptyArray()
    var tick by remember { mutableIntStateOf(0) }
    fun granted() = needed.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++; if (granted()) hid.start() }
    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun home() = if (connected) hid.tapConsumer(K.CC_HOME) else state.run { state.client!!.key("home") }
    fun back() = if (connected) hid.tapConsumer(K.CC_BACK) else state.run { state.client!!.key("back") }

    HigScreen("Remote", trailing = { ConnectionBadge(state.connection) }) {
        item("mode") { Segmented(listOf("Remote", "Game Controller"), mode, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) { mode = it } }

        if (!connected) section("bt", "Bluetooth", footer = when {
            mode == 1 -> "The game controller works over Bluetooth: the TV sees this phone as a real game pad, in every game."
            else -> "Arrows, OK, volume and typing use Bluetooth: the TV sees this phone as a remote and keyboard. Home, Back and opening apps also work over Wi-Fi or the internet."
        }) {
            tick
            when (val s = bt) {
                is HidController.Status.Unavailable -> Row(s.why, leading = { IconTile(Icons.Rounded.Bluetooth, c.tint) })
                HidController.Status.Starting -> Row("Starting Bluetooth…", leading = { IconTile(Icons.Rounded.Bluetooth, c.tint) })
                HidController.Status.Ready -> {
                    Row("Ready to connect", "First time: tap Make Phone Visible, then on the TV open Settings → Remotes & Accessories → Add accessory and pick this phone.",
                        leading = { IconTile(Icons.Rounded.Bluetooth, c.tint) })
                    ButtonRow("Make Phone Visible") {
                        discoverable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
                    }
                    hid.bondedDevices().forEach { d -> ButtonRow("Connect to ${d.name ?: d.address}") { hid.connect(d) } }
                }
                else -> {
                    Row("Bluetooth remote is off", leading = { IconTile(Icons.Rounded.Bluetooth, c.tint) })
                    ButtonRow(if (granted()) "Turn On" else "Allow Bluetooth") { if (granted()) hid.start() else ask.launch(needed) }
                }
            }
        }

        if (mode == 0) {
            item("pad") {
                Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ClickPad(enabled = connected) { dir ->
                        when (dir) { 0 -> hid.tapKey(K.KEY_UP); 1 -> hid.tapKey(K.KEY_RIGHT); 2 -> hid.tapKey(K.KEY_DOWN); 3 -> hid.tapKey(K.KEY_LEFT); else -> hid.tapKey(K.KEY_ENTER) }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        HoldButton(icon = Icons.Rounded.ChevronLeft, contentDescription = "Back", onDown = { back() })
                        HoldButton(icon = Icons.Rounded.Tv, contentDescription = "Home", onDown = { home() })
                        HoldButton(icon = Icons.Rounded.Menu, contentDescription = "Menu", onDown = { if (connected) hid.tapConsumer(K.CC_MENU) })
                        HoldButton(icon = Icons.Rounded.PlayArrow, contentDescription = "Play or pause", onDown = { if (connected) hid.tapConsumer(K.CC_PLAY_PAUSE) })
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.clip(RoundedCornerShape(32.dp)).background(if (c.dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))) {
                            HoldButton(icon = Icons.Rounded.Add, contentDescription = "Volume up", color = Color.Transparent,
                                onDown = { if (connected) hid.tapConsumer(K.CC_VOL_UP) else state.run { state.volume = state.client!!.stepVolume(2) } })
                            HoldButton(icon = Icons.Rounded.Remove, contentDescription = "Volume down", color = Color.Transparent,
                                onDown = { if (connected) hid.tapConsumer(K.CC_VOL_DOWN) else state.run { state.volume = state.client!!.stepVolume(-2) } })
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            HoldButton(icon = Icons.AutoMirrored.Rounded.VolumeOff, contentDescription = "Mute",
                                onDown = { if (connected) hid.tapConsumer(K.CC_MUTE) else state.run { state.volume = state.client!!.mute(state.volume?.muted != true) } })
                            HoldButton(icon = Icons.Rounded.Keyboard, contentDescription = "Type on TV", onDown = { if (connected) typing = true })
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            HoldButton(icon = Icons.Rounded.PowerSettingsNew, contentDescription = "Turn the TV off", color = Color(0xFFFF3B30),
                                onDown = { state.run { state.client!!.power("sleep"); state.message = "TV going to standby" } })
                            val v = state.volume
                            Text(when { v == null -> ""; v.muted -> "Muted"; else -> "Vol ${v.level}" },
                                style = HigType.footnote, color = c.secondaryLabel,
                                modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                        }
                    }
                }
            }
            val apps = state.apps.filter { it.state == "allowed" && !it.protected || it.pkg == "com.android.tv" }
            if (apps.isNotEmpty()) item("open") {
                Column(Modifier.padding(top = 28.dp)) {
                    Text("OPEN ON TV", style = HigType.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 32.dp, bottom = 8.dp))
                    FlowRow(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp)).background(c.cell).padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        apps.forEach { a ->
                            Column(Modifier.width(74.dp).clip(RoundedCornerShape(10.dp)).clickable { state.run { state.client!!.launch(a.pkg) } }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(state, a.pkg, 52)
                                Text(a.label, style = HigType.caption, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        } else if (connected) {
            item("pad") { GamePad(hid) }
        }
    }

    if (typing) {
        var text by remember { mutableStateOf("") }
        HigAlert("Type on TV", "Sends the text to the box that's selected on the TV.", { typing = false },
            content = { AlertField(text, { text = it }, "Text") },
            buttons = listOf("Cancel" to { typing = false }, "Send" to { hid.type(text); typing = false }),
            enabled = { it == 0 || text.isNotEmpty() })
    }
}

/** The Apple TV remote's click pad: a circle; press near an edge for that arrow, the middle for OK. */
@Composable
private fun ClickPad(enabled: Boolean, onPress: (Int) -> Unit) {
    val c = Hig.colors
    val view = LocalView.current
    var pressed by remember { mutableIntStateOf(-1) }
    val surface = if (c.dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    Box(
        Modifier.size(250.dp).clip(CircleShape).background(surface)
            .semantics { contentDescription = "Arrows and OK" }
            .pointerInput(enabled) {
                detectTapGestures(onPress = { p ->
                    if (!enabled) return@detectTapGestures
                    val r = size.width / 2f
                    val dx = p.x - r
                    val dy = p.y - r
                    val dir = when {
                        hypot(dx, dy) < r * 0.36f -> 4
                        abs(dx) > abs(dy) -> if (dx > 0) 1 else 3
                        else -> if (dy > 0) 2 else 0
                    }
                    pressed = dir
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onPress(dir)
                    tryAwaitRelease()
                    pressed = -1
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        val arrow = if (enabled) c.label else c.tertiaryLabel
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = arrow, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(34.dp))
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = arrow, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(34.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = arrow, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).size(34.dp))
        Icon(Icons.Rounded.KeyboardArrowLeft, null, tint = arrow, modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(34.dp))
        Box(Modifier.size(92.dp).clip(CircleShape).background(if (pressed == 4) c.tint.copy(alpha = 0.35f) else c.cell), contentAlignment = Alignment.Center) {
            Text("OK", style = HigType.headline, color = arrow)
        }
        if (!enabled) Text("Connect Bluetooth\nto use the arrows", style = HigType.footnote, color = c.secondaryLabel, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(top = 150.dp))
    }
}

@Composable
private fun GamePad(hid: HidController) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HoldButton("L1", size = 64.dp, round = false, onDown = { hid.button(K.BTN_L1, true) }, onUp = { hid.button(K.BTN_L1, false) })
            HoldButton("R1", size = 64.dp, round = false, onDown = { hid.button(K.BTN_R1, true) }, onUp = { hid.button(K.BTN_R1, false) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(168.dp)) {
                Box(Modifier.align(Alignment.TopCenter)) { HoldButton(icon = Icons.Rounded.KeyboardArrowUp, size = 56.dp, round = false, contentDescription = "Up", onDown = { hid.dpad(0) }, onUp = { hid.dpad(-1) }) }
                Box(Modifier.align(Alignment.CenterEnd)) { HoldButton(icon = Icons.Rounded.KeyboardArrowRight, size = 56.dp, round = false, contentDescription = "Right", onDown = { hid.dpad(2) }, onUp = { hid.dpad(-1) }) }
                Box(Modifier.align(Alignment.BottomCenter)) { HoldButton(icon = Icons.Rounded.KeyboardArrowDown, size = 56.dp, round = false, contentDescription = "Down", onDown = { hid.dpad(4) }, onUp = { hid.dpad(-1) }) }
                Box(Modifier.align(Alignment.CenterStart)) { HoldButton(icon = Icons.Rounded.KeyboardArrowLeft, size = 56.dp, round = false, contentDescription = "Left", onDown = { hid.dpad(6) }, onUp = { hid.dpad(-1) }) }
            }
            Box(Modifier.size(168.dp)) {
                Box(Modifier.align(Alignment.TopCenter)) { Face("Y", Color(0xFFE8B931), K.BTN_Y, hid) }
                Box(Modifier.align(Alignment.CenterStart)) { Face("X", Color(0xFF3D7BFF), K.BTN_X, hid) }
                Box(Modifier.align(Alignment.CenterEnd)) { Face("B", Color(0xFFE5484D), K.BTN_B, hid) }
                Box(Modifier.align(Alignment.BottomCenter)) { Face("A", Color(0xFF30A46C), K.BTN_A, hid) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HoldButton("Select", size = 72.dp, round = false, onDown = { hid.button(K.BTN_SELECT, true) }, onUp = { hid.button(K.BTN_SELECT, false) })
            HoldButton("Home", size = 72.dp, round = false, onDown = { hid.button(K.BTN_MODE, true) }, onUp = { hid.button(K.BTN_MODE, false) })
            HoldButton("Start", size = 72.dp, round = false, onDown = { hid.button(K.BTN_START, true) }, onUp = { hid.button(K.BTN_START, false) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stick { x, y -> hid.stick(0, x, y) }
            Stick { x, y -> hid.stick(1, x, y) }
        }
    }
}

@Composable
private fun Face(label: String, color: Color, button: Int, hid: HidController) {
    HoldButton(label, size = 56.dp, color = color, textColor = Color.White,
        onDown = { hid.button(button, true) }, onUp = { hid.button(button, false) })
}
