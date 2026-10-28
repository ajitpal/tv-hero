package com.ajit.tvhero.remote.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.HomeLayout
import com.ajit.tvhero.remote.ui.hig.AlertField
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Content
import com.ajit.tvhero.remote.ui.hig.FieldRow
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigAlert
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigSheet
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.IconTile
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.TimeWheels
import com.ajit.tvhero.remote.ui.hig.ToggleRow
import com.ajit.tvhero.remote.ui.hig.section
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsTab(state: AppState, gate: PasswordGate) {
    var page by remember { mutableStateOf<String?>(null) }
    when (page) {
        "home" -> { HomeScreenPage(state) { page = null }; return }
        "quiet" -> { QuietPage(state) { page = null }; return }
        "remote" -> { RemoteAccessPage(state, gate) { page = null }; return }
        "phones" -> { PhonesPage(state, gate) { page = null }; return }
        "log" -> { LogPage(state) { page = null }; return }
    }
    val c = Hig.colors
    var alert by remember { mutableStateOf<String?>(null) }   // password | pin | forget | name
    val family = state.family
    HigScreen("Settings") {
        section("tv", "TV") {
            Row(state.tv?.name?.removePrefix("tv-hero · ") ?: "TV", subtitle = buildString {
                append(state.tv?.host ?: "")
                if (!state.tv?.remoteHost.isNullOrBlank()) append(" · internet ").append(state.tv?.remoteHost)
            }, trailing = { ConnectionBadge(state.connection) })
        }
        section("screen", "TV Experience") {
            Row("Home Screen", subtitle = "App row, greeting, hidden apps", chevron = true,
                leading = { IconTile(Icons.Rounded.Dashboard, Color(0xFF007AFF)) }) { page = "home" }
            Row("Night Volume", subtitle = "Cap the volume at night", chevron = true,
                leading = { IconTile(Icons.Rounded.VolumeDown, Color(0xFF5856D6)) }) { page = "quiet" }
        }
        section("access", "Access") {
            Row("Remote Access", subtitle = if (state.tv?.remoteHost.isNullOrBlank()) "Control the TV from anywhere" else "Ready over the internet",
                chevron = true, leading = { IconTile(Icons.Rounded.Public, Color(0xFF34C759)) }) { page = "remote" }
            Row("Parent Password", subtitle = "Needed to allow apps and change rules", chevron = true,
                leading = { IconTile(Icons.Rounded.Lock, Color(0xFF8E8E93)) }) { alert = "password" }
            Row("Parent PIN on TV", value = if (family?.pinSet == true) "On" else "Not Set", chevron = true,
                leading = { IconTile(Icons.Rounded.Key, Color(0xFFFF9500)) }) { alert = "pin" }
            Row("Paired Phones", chevron = true, leading = { IconTile(Icons.Rounded.Smartphone, Color(0xFF007AFF)) }) { page = "phones" }
            Row("TV Event Log", chevron = true, leading = { IconTile(Icons.Rounded.History, Color(0xFF8E8E93)) }) { page = "log" }
        }
        section("phone", "This Phone", "Forgetting the TV removes it from this phone only; the TV keeps its rules.") {
            Row("Name", value = state.store.deviceName, chevron = true) { alert = "name" }
            ButtonRow("Forget This TV", destructive = true) { alert = "forget" }
        }
    }

    when (alert) {
        "password" -> {
            var old by remember { mutableStateOf("") }
            var new by remember { mutableStateOf("") }
            HigAlert("Change Parent Password", "At least 4 characters. Every parent's phone uses the same password.", { alert = null },
                content = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertField(old, { old = it }, "Current password", secure = true)
                    AlertField(new, { new = it }, "New password", secure = true)
                } },
                buttons = listOf("Cancel" to { alert = null }, "Change" to {
                    alert = null
                    state.run(onDone = { if (it) { state.passwordWorked(new); state.message = "Password changed" } }) { state.client!!.changePassword(old, new) }
                }), enabled = { it == 0 || (old.isNotEmpty() && new.length >= 4) })
        }
        "pin" -> {
            var pin by remember { mutableStateOf("") }
            HigAlert("Parent PIN on TV", "4 to 6 digits, typed with the remote to switch the TV to Parent, add time or allow a late night.", { alert = null },
                content = { AlertField(pin, { pin = it.filter(Char::isDigit).take(6) }, "PIN", secure = true, keyboard = KeyboardType.NumberPassword) },
                buttons = listOf("Cancel" to { alert = null }, "Save" to {
                    alert = null
                    gate.run("Set the PIN used on the TV.") { pw -> state.family = state.client!!.setPin(pin, pw); state.message = "PIN saved on the TV" }
                }), enabled = { it == 0 || pin.length >= 4 })
        }
        "name" -> {
            var name by remember { mutableStateOf(state.store.deviceName) }
            HigAlert("Phone Name", "Shown on the TV's list of paired phones the next time you pair.", { alert = null },
                content = { AlertField(name, { name = it.take(40) }, "Name") },
                buttons = listOf("Cancel" to { alert = null }, "Save" to { state.store.deviceName = name.trim(); alert = null }),
                enabled = { it == 0 || name.isNotBlank() })
        }
        "forget" -> HigAlert("Forget This TV?", "You'll need the code on the TV and the parent password to pair again.", { alert = null },
            buttons = listOf("Cancel" to { alert = null }, "Forget" to { alert = null; state.forgetTv() }), destructiveIndex = 1)
    }
}

// ---------- Home Screen ----------

@Composable
private fun HomeScreenPage(state: AppState, onBack: () -> Unit) {
    val current = state.home
    val c = Hig.colors
    if (current == null) { HigScreen("Home Screen", onBack = onBack, backLabel = "Settings") { section("l") { Row("Loading…") } }; return }
    var dock by remember(current) { mutableStateOf(current.dock) }
    var hidden by remember(current) { mutableStateOf(current.hidden) }
    var columns by remember(current) { mutableFloatStateOf(current.columns.toFloat()) }
    var greeting by remember(current) { mutableStateOf(current.greeting) }
    var showGreeting by remember(current) { mutableStateOf(current.showGreeting) }
    var adding by remember { mutableStateOf(false) }
    val labels = state.labels
    val edited = HomeLayout(dock, hidden, columns.toInt(), greeting, showGreeting)
    val changed = edited != current

    HigScreen("Home Screen", onBack = onBack, backLabel = "Settings", trailing = {
        Text("Save", style = HigType.headline, color = if (changed) c.tint else c.tertiaryLabel,
            modifier = Modifier.padding(end = 8.dp).then(if (changed) Modifier.clickableNoRipple {
                state.run(onDone = { if (it) state.message = "Saved to the TV" }) { state.home = state.client!!.saveHome(edited) }
            } else Modifier))
    }) {
        section("dock", "App Row", "The apps along the bottom of the TV's home screen, in this order. A kid only sees the ones on their list.") {
            dock.forEachIndexed { i, pkg ->
                Row(labels[pkg] ?: pkg, leading = { AppIcon(state, pkg, 29) }) {
                    androidx.compose.foundation.layout.Row {
                        Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = if (i > 0) c.tint else c.tertiaryLabel,
                            modifier = Modifier.padding(6.dp).clickableNoRipple { if (i > 0) dock = dock.toMutableList().apply { add(i - 1, removeAt(i)) } })
                        Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = if (i < dock.lastIndex) c.tint else c.tertiaryLabel,
                            modifier = Modifier.padding(6.dp).clickableNoRipple { if (i < dock.lastIndex) dock = dock.toMutableList().apply { add(i + 1, removeAt(i)) } })
                        Icon(Icons.Rounded.RemoveCircle, "Remove", tint = c.destructive,
                            modifier = Modifier.padding(6.dp).clickableNoRipple { dock = dock - pkg })
                    }
                }
            }
            ButtonRow("Add App…") { adding = true }
        }
        section("size", "Apps per Row: ${columns.toInt()}") {
            Content(PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                Slider(columns, { columns = it }, valueRange = 4f..7f, steps = 2,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill,
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
            }
        }
        section("greet", "Greeting", "Kids see their own name instead.") {
            ToggleRow("Show Greeting", showGreeting) { showGreeting = it }
            if (showGreeting) FieldRow("Name", greeting, { greeting = it.take(40) }, "e.g. Pal family")
        }
        section("hidden", "Hidden from Home", "Hidden apps still work; they just don't show on the TV's home screen.") {
            state.apps.filter { it.state == "allowed" && !it.protected }.forEach { a ->
                ToggleRow(a.label, a.pkg !in hidden, leading = { AppIcon(state, a.pkg, 29) }) { show ->
                    hidden = if (show) hidden - a.pkg else hidden + a.pkg
                }
            }
        }
    }
    if (adding) HigSheet("Add App", onCancel = { adding = false }, done = null) {
        section("a") {
            state.apps.filter { it.state == "allowed" && it.pkg !in dock }.forEach { a ->
                Row(a.label, leading = { AppIcon(state, a.pkg, 29) }) { dock = dock + a.pkg; adding = false }
            }
        }
    }
}

// ---------- Night Volume ----------

@Composable
private fun QuietPage(state: AppState, onBack: () -> Unit) {
    val c = Hig.colors
    var q by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { state.run { q = state.client!!.quiet() } }
    var editing by remember { mutableStateOf<String?>(null) }
    fun save(update: JSONObject) = state.run { q = state.client!!.setQuiet(update) }
    HigScreen("Night Volume", onBack = onBack, backLabel = "Settings") {
        val cur = q
        if (cur == null) { section("l") { Row("Loading…") }; return@HigScreen }
        section("on", footer = "Between these times the TV's volume can't go above the cap: the remote's Volume Up and loud apps are brought back down.") {
            ToggleRow("Night Volume Cap", cur.optBoolean("enabled")) { save(JSONObject().put("enabled", it)) }
            if (cur.optBoolean("enabled")) {
                Row("Starts", value = clockText(cur.optInt("start")), chevron = true) { editing = "start" }
                Row("Ends", value = clockText(cur.optInt("end")), chevron = true) { editing = "end" }
            }
        }
        if (cur.optBoolean("enabled")) section("cap", "Loudest: ${cur.optInt("cap_percent")}%") {
            var v by remember(cur) { mutableIntStateOf(cur.optInt("cap_percent")) }
            Content(PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                Slider(v.toFloat(), { v = (it / 5).toInt() * 5 }, valueRange = 5f..100f, onValueChangeFinished = { save(JSONObject().put("cap_percent", v)) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.tint, inactiveTrackColor = c.fill))
            }
        }
    }
    editing?.let { which ->
        var t by remember { mutableIntStateOf(q?.optInt(which) ?: 0) }
        HigSheet(if (which == "start") "Starts" else "Ends", onCancel = { editing = null }, onDone = { editing = null; save(JSONObject().put(which, t)) }) {
            item("w") { androidx.compose.foundation.layout.Box(Modifier.padding(top = 24.dp)) { TimeWheels(t) { t = it } } }
        }
    }
}

// ---------- Remote Access ----------

@Composable
private fun RemoteAccessPage(state: AppState, gate: PasswordGate, onBack: () -> Unit) {
    val c = Hig.colors
    val ctx = LocalContext.current
    val r = state.remote
    LaunchedEffect(Unit) { state.run { state.remote = state.client!!.remoteAccess() } }
    HigScreen("Remote Access", onBack = onBack, backLabel = "Settings") {
        section("how", "From Anywhere", "Tailscale links this phone and the TV privately and encrypted, wherever you are. Nothing is opened on your router. Use the same Tailscale account on both.") {
            Row("Tailscale on the TV", value = when {
                r == null -> "…"
                !r.tailscaleInstalled -> "Not installed"
                r.tailscaleIp.isBlank() -> "Not signed in"
                else -> "Connected"
            })
            if (r != null && r.tailscaleIp.isNotBlank()) Row("TV's Internet Address", value = r.tailscaleIp)
            Row("This App", value = if (state.tv?.remoteHost.isNullOrBlank()) "Not set up" else "Ready")
            ButtonRow("Get Tailscale for This Phone") {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.tailscale.ipn")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        section("steps", "Set Up Once", "After this, the app uses the home Wi-Fi when you're home and the internet when you're not, on its own.") {
            Row("1. On the TV, open Tailscale and sign in", subtitle = "It shows a QR code: scan it with this phone.")
            Row("2. On this phone, sign in to Tailscale", subtitle = "Same account as the TV, and keep it switched on.")
            Row("3. Come back here", subtitle = "The app picks up the TV's internet address by itself.")
        }
        section("adb", "TV Debugging", "Debugging lets a computer on your Wi-Fi control the TV completely. Keep it off unless you're setting the TV up.") {
            ToggleRow("Network Debugging (ADB)", r?.adbEnabled == true, subtitle = if (r?.adbEnabled == true && r.adbPort.isNotBlank()) "On, port ${r.adbPort}" else null) { on ->
                gate.run(if (on) "Turn on debugging on the TV." else "Turn off debugging on the TV.") { pw -> state.remote = state.client!!.setAdb(on, pw) }
            }
        }
    }
}

// ---------- Paired Phones ----------

@Composable
private fun PhonesPage(state: AppState, gate: PasswordGate, onBack: () -> Unit) {
    var devices by remember { mutableStateOf(JSONArray()) }
    fun load() = state.run { devices = state.client!!.devices() }
    LaunchedEffect(Unit) { load() }
    HigScreen("Paired Phones", onBack = onBack, backLabel = "Settings") {
        section("list", footer = "To add another parent, install tv-hero on their phone and pair with the code on the TV and the parent password.") {
            for (i in 0 until devices.length()) {
                val d = devices.getJSONObject(i)
                Row(d.optString("name"), subtitle = "Paired " + DateFormat.getDateInstance().format(Date(d.optLong("paired_at")))) {
                    Text("Remove", style = HigType.body, color = Hig.colors.destructive, modifier = Modifier.clickableNoRipple {
                        gate.run("Remove ${d.optString("name")} from the TV.", after = { load() }) { pw -> state.client!!.removeDevice(d.getString("id"), pw) }
                    }.padding(6.dp))
                }
            }
        }
    }
}

// ---------- Event log ----------

@Composable
private fun LogPage(state: AppState, onBack: () -> Unit) {
    var log by remember { mutableStateOf(JSONArray()) }
    LaunchedEffect(Unit) { state.run { log = state.client!!.log() } }
    val labels = state.labels
    HigScreen("TV Event Log", onBack = onBack, backLabel = "Settings") {
        section("l") {
            if (log.length() == 0) Row("Nothing yet")
            for (i in (log.length() - 1) downTo maxOf(0, log.length() - 60)) {
                val e = log.getJSONObject(i)
                val p = e.optString("package")
                val name = labels[p] ?: p
                val what = when (e.optString("type")) {
                    "opened_blocked" -> "$name was blocked when opened"
                    "opened_pending" -> "$name was opened before approval"
                    "opened_not_allowed" -> "$name isn't on the kid's list"
                    "installed_pending" -> "$name was installed and waits for approval"
                    "installed" -> "$name was installed"
                    "removed" -> "$name was uninstalled"
                    "blocked" -> "$name was blocked"
                    "allowed" -> "$name was allowed"
                    "paired" -> "$p paired with the TV"
                    "profile" -> "$p started watching"
                    "profile_saved" -> "$p's profile was changed"
                    "times_up" -> "Time's up for $p"
                    "bedtime" -> "Bedtime for $p"
                    "extra_time" -> "Extra time: $p"
                    "locked" -> "TV paused"
                    "unlocked" -> "TV resumed"
                    "sleep" -> "TV went to standby"
                    "adb_on" -> "Debugging turned on"
                    "adb_off" -> "Debugging turned off"
                    else -> e.optString("type") + " " + p
                }
                Row(what, subtitle = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e.optLong("t"))))
            }
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
