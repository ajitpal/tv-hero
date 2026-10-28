package com.ajit.tvhero.remote.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.ui.hig.AlertField
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigAlert
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.TabBar
import com.ajit.tvhero.remote.ui.hig.TabItem
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar

/**
 * Asks for the parent password (or reuses one that worked in the last 5 minutes), then runs
 * [action] with it. Screens call [ask]; the alert lives at the root.
 */
class PasswordGate(private val state: AppState) {
    var request by mutableStateOf<Pair<String, (String) -> Unit>?>(null)

    fun ask(reason: String, action: (String) -> Unit) {
        val pw = state.rememberedPassword()
        if (pw != null) action(pw) else request = reason to action
    }

    /** Runs [call] with the password, remembers it if the TV accepted it, then [after]. */
    fun run(reason: String, after: () -> Unit = {}, call: suspend (String) -> Unit) = ask(reason) { pw ->
        state.run(onDone = { ok -> if (ok) { state.passwordWorked(pw); after() } }) { call(pw) }
    }
}

@Composable
fun Root(state: AppState, activity: Activity) {
    val gate = remember { PasswordGate(state) }
    if (state.tv == null) {
        ConnectScreen(state)
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pending = state.apps.count { it.state == "pending" }
    val tabs = listOf(
        TabItem("TV", Icons.Outlined.Tv, Icons.Rounded.Tv),
        TabItem("Family", Icons.Outlined.FamilyRestroom, Icons.Rounded.FamilyRestroom),
        TabItem("Apps", Icons.Outlined.Apps, Icons.Rounded.Apps, badge = pending),
        TabItem("Remote", Icons.Outlined.SettingsRemote, Icons.Rounded.SettingsRemote),
        TabItem("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
    )
    Column(Modifier.fillMaxSize().background(Hig.colors.groupedBackground)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> TvTab(state, gate) { tab = it }
                1 -> FamilyTab(state, gate)
                2 -> AppsTab(state, gate)
                3 -> RemoteTab(state, activity)
                4 -> SettingsTab(state, gate)
            }
            Toast(state)
        }
        TabBar(tabs, tab) { tab = it }
    }
    gate.request?.let { (reason, action) ->
        var pw by remember { mutableStateOf("") }
        HigAlert(
            title = "Parent Password",
            message = reason,
            onDismiss = { gate.request = null },
            content = { AlertField(pw, { pw = it }, "Password", secure = true, keyboard = KeyboardType.Password) },
            buttons = listOf("Cancel" to { gate.request = null }, "OK" to { gate.request = null; action(pw) }),
            enabled = { it == 0 || pw.isNotEmpty() },
        )
    }
}

/** Errors and confirmations: a small capsule at the top that leaves by itself (no Material snackbar). */
@Composable
private fun Toast(state: AppState) {
    val msg = state.message ?: return
    LaunchedEffect(msg) { delay(3500); state.message = null }
    val c = Hig.colors
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 52.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.TopCenter) {
        Text(msg, style = HigType.subhead, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xE6333338)).padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

// ---------- formatting shared by the tabs ----------

fun minutesText(m: Int): String = when {
    m <= 0 -> "0 min"
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} hr"
    else -> "${m / 60} hr ${m % 60} min"
}

fun clockText(minuteOfDay: Int): String {
    val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(Calendar.MINUTE, minuteOfDay % 60) }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(c.time)
}

fun timeText(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(java.util.Date(ms))

fun avatarColor(i: Int) = com.ajit.tvhero.remote.ui.hig.AvatarColors[Math.floorMod(i, 10)]
