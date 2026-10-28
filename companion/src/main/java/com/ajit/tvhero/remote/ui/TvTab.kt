package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MoreTime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.Kid
import com.ajit.tvhero.remote.ui.hig.Avatar
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigSheet
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Content
import com.ajit.tvhero.remote.ui.hig.section

@Composable
fun TvTab(state: AppState, gate: PasswordGate, openTab: (Int) -> Unit) {
    var sheet by remember { mutableStateOf<String?>(null) }   // sleep | who | extra
    var allActivity by remember { mutableStateOf(false) }
    if (allActivity) { ActivityPage(state) { allActivity = false }; return }
    val now = state.now
    val family = state.family
    val c = Hig.colors
    val activeKid = family?.kids?.firstOrNull { it.id == family.active }

    HigScreen(title = state.tv?.name?.removePrefix("tv-hero · ") ?: "TV", trailing = { ConnectionBadge(state.connection) }) {
        item("now") { NowCard(state) }
        item("actions") {
            Row(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val locked = family?.locked == true
                ActionTile(if (locked) "Resume TV" else "Pause TV", if (locked) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    if (locked) c.green else c.orange, Modifier.weight(1f)) {
                    if (locked) gate.run("Resume the TV.") { pw -> state.family = state.client!!.lock(false, pw) }
                    else state.run { state.family = state.client!!.lock(true, null) }
                }
                ActionTile(if ((family?.sleepAt ?: 0) > 0) "Off at " + timeText(family!!.sleepAt) else "Sleep Timer",
                    Icons.Rounded.Bedtime, Color(0xFF5856D6), Modifier.weight(1f)) { sheet = "sleep" }
            }
            Row(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("Who's Watching", Icons.Rounded.Group, c.tint, Modifier.weight(1f)) { sheet = "who" }
                ActionTile("More Time", Icons.Rounded.MoreTime, c.green, Modifier.weight(1f), enabled = activeKid != null) { sheet = "extra" }
            }
        }
        val pending = state.apps.filter { it.state == "pending" }
        if (pending.isNotEmpty()) section("pending", "Waiting for Approval", "New apps stay blocked on the TV until you approve them.") {
            pending.forEach { a ->
                Row(a.label, "Installed on the TV", leading = { AppIcon(state, a.pkg, 32) }, onClick = { openTab(2) }) {
                    Text("Approve", style = HigType.headline, color = c.tint, modifier = Modifier.clickable {
                        gate.run("Approve ${a.label} on the TV.", after = { state.refresh() }) { pw -> state.client!!.allow(a.pkg, pw, true) }
                    }.padding(8.dp))
                }
            }
        }
        if (family != null && family.kids.isNotEmpty()) section("time", "Screen Time Today") {
            family.kids.forEach { k ->
                Content(androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)) { UsageRow(k) }
            }
            Row("Parent", value = minutesText(family.parentUsed), leading = { Avatar("P", Color(0xFF8E8E93), 32.dp) })
        }
        val recent = state.sessions.takeLast(5).reversed()
        section("recent", "Recent Activity") {
            if (recent.isEmpty()) Row("Nothing watched yet today", subtitle = "Apps opened on the TV show up here.")
            recent.forEach { s ->
                val who = if (s.profile == "parent") "Parent" else family?.kids?.firstOrNull { it.id == s.profile }?.name ?: "Someone"
                Row(s.title.ifBlank { s.label }, subtitle = "$who · ${if (s.title.isBlank()) "" else s.label + " · "}${timeText(s.start)} · ${minutesText(((s.end - s.start) / 60000).toInt())}",
                    leading = { AppIcon(state, s.pkg, 32) })
            }
            ButtonRow("See All Activity") { allActivity = true }
        }
    }

    when (sheet) {
        "sleep" -> HigSheet("Sleep Timer", onCancel = { sheet = null }, done = null) {
            section("s", footer = "The TV goes to standby when the timer runs out. A minute before, a note appears on the TV.") {
                listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                    Row(minutesText(m), onClick = { sheet = null; state.run { state.client!!.sleepIn(m); state.family = state.client!!.family() } })
                }
            }
            section("s2") {
                if ((family?.sleepAt ?: 0) > 0) ButtonRow("Cancel Timer") { sheet = null; state.run { state.client!!.sleepIn(0); state.family = state.client!!.family() } }
                ButtonRow("Turn Off TV Now", destructive = true) { sheet = null; state.run { state.client!!.sleepIn(0, now = true) } }
            }
        }
        "who" -> HigSheet("Who's Watching", onCancel = { sheet = null }, done = null) {
            section("w", footer = "Switching to Parent needs the parent password. Kids only see their own apps.") {
                family?.kids?.forEach { k ->
                    Row(k.name, leading = { Avatar(k.name, avatarColor(k.color), 32.dp) },
                        trailing = { if (family.active == k.id) Text("Watching", style = HigType.subhead, color = c.green) },
                        onClick = { sheet = null; state.run { state.family = state.client!!.activate(k.id, null); state.refresh() } })
                }
                Row("Parent", leading = { Avatar("P", Color(0xFF8E8E93), 32.dp) },
                    trailing = { if (family?.active == "parent") Text("Watching", style = HigType.subhead, color = c.green) },
                    onClick = { sheet = null; gate.run("Switch the TV to Parent.", after = { state.refresh() }) { pw -> state.family = state.client!!.activate("parent", pw) } })
            }
            section("w2") { ButtonRow("Show “Who’s Watching?” on the TV") { sheet = null; state.run { state.client!!.key("picker") } } }
        }
        "extra" -> if (activeKid != null) HigSheet("More Time for ${activeKid.name}", onCancel = { sheet = null }, done = null) {
            section("e", footer = "Added to today only. ${activeKid.name} has ${if (activeKid.minutesLeft < 0) "no limit" else minutesText(activeKid.minutesLeft) + " left"}.") {
                listOf(15, 30, 60).forEach { m ->
                    Row("+ ${minutesText(m)}", onClick = {
                        sheet = null
                        gate.run("Give ${activeKid.name} $m more minutes.") { pw -> state.family = state.client!!.extraTime(activeKid.id, m, pw) }
                    })
                }
            }
        }
    }
}

@Composable
private fun NowCard(state: AppState) {
    val c = Hig.colors
    val now = state.now
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.cell).padding(16.dp)) {
        when {
            state.connection.isEmpty() && now == null -> {
                Text("Can't reach the TV", style = HigType.headline)
                Text("Check that the TV is on. Away from home, turn on Tailscale on this phone (Settings → Remote Access).",
                    style = HigType.subhead, color = c.secondaryLabel, modifier = Modifier.padding(top = 4.dp))
            }
            now == null -> Text("Connecting…", style = HigType.subhead, color = c.secondaryLabel)
            !now.screenOn -> {
                Text("The TV is off", style = HigType.headline)
                Text("It's in standby. Rules still apply when it's switched on.", style = HigType.subhead, color = c.secondaryLabel)
            }
            else -> {
                val kid = state.family?.kids?.firstOrNull { it.id == now.profile }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(now.profileName, if (kid != null) avatarColor(kid.color) else Color(0xFF8E8E93), 28.dp)
                    Text("${now.profileName} is watching", style = HigType.subhead, color = c.secondaryLabel, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.weight(1f))
                    if (now.minutesLeft >= 0) Text(minutesText(now.minutesLeft) + " left", style = HigType.subhead,
                        color = if (now.minutesLeft <= 10) c.orange else c.secondaryLabel)
                }
                Spacer(Modifier.height(14.dp))
                val status = when (now.overlay) {
                    "locked" -> "Paused by a parent"
                    "bedtime" -> "Bedtime screen"
                    "times_up" -> "Time's up for today"
                    "picker" -> "Choosing who's watching"
                    "blocked" -> "Blocked app message"
                    else -> null
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (now.pkg.isNotBlank() && now.pkg != "com.ajit.tvhero") AppIcon(state, now.pkg, 52)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(status ?: now.title.ifBlank { if (now.pkg == "com.ajit.tvhero" || now.pkg.isBlank()) "Home screen" else now.label },
                            style = HigType.title3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (status == null && now.title.isNotBlank()) Text(now.label, style = HigType.subhead, color = c.secondaryLabel)
                        if (now.since > 0 && status == null) Text("Since " + timeText(now.since), style = HigType.footnote, color = c.secondaryLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRow(k: Kid) {
    val c = Hig.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(k.name, avatarColor(k.color), 32.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(k.name, style = HigType.body)
                Text(if (k.dailyLimit > 0) "${minutesText(k.usedToday)} of ${minutesText(k.dailyLimit + k.extraToday)}" else minutesText(k.usedToday) + " · no limit",
                    style = HigType.subhead, color = c.secondaryLabel)
            }
            if (k.minutesLeft == 0) Text("Time's up", style = HigType.subhead, color = c.orange)
        }
        if (k.dailyLimit > 0) {
            val f = (k.usedToday.toFloat() / (k.dailyLimit + k.extraToday).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.padding(start = 44.dp, top = 8.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(c.fill)) {
                Box(Modifier.fillMaxWidth(f).height(6.dp).clip(CircleShape).background(if (f >= 1f) c.orange else c.green))
            }
        }
    }
}

@Composable
private fun ActionTile(title: String, icon: ImageVector, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Hig.colors
    Column(modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(16.dp)).background(c.cell)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(14.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (enabled) color else c.fill), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = HigType.headline, color = if (enabled) c.label else c.tertiaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ConnectionBadge(connection: String) {
    val c = Hig.colors
    val (text, color) = when (connection) {
        "home" -> "Home Wi-Fi" to c.green
        "internet" -> "Internet" to c.tint
        else -> "Offline" to c.tertiaryLabel
    }
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = HigType.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 6.dp))
        Spacer(Modifier.width(4.dp))
    }
}
