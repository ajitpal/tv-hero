package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.Kid
import com.ajit.tvhero.remote.QuizSettings
import com.ajit.tvhero.remote.ui.hig.AlertField
import com.ajit.tvhero.remote.ui.hig.Avatar
import com.ajit.tvhero.remote.ui.hig.AvatarColors
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Content
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigAlert
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigSheet
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.TimeWheels
import com.ajit.tvhero.remote.ui.hig.ToggleRow
import com.ajit.tvhero.remote.ui.hig.section

private val LIMITS = listOf(0, 30, 45, 60, 90, 120, 180, 240)

@Composable
fun FamilyTab(state: AppState, gate: PasswordGate) {
    var open by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var wakeSheet by remember { mutableStateOf(false) }
    var everyone by remember { mutableStateOf(false) }
    val family = state.family
    if (everyone && family != null) { CommonAppsPage(state, gate) { everyone = false }; return }
    if (open == "guest" && family != null) { GuestPage(state, gate) { open = null }; return }
    open?.let { id ->
        val kid = family?.kids?.firstOrNull { it.id == id }
        if (kid != null) { KidPage(state, gate, kid) { open = null }; return }
        open = null
    }
    val c = Hig.colors
    HigScreen("Family") {
        if (family == null) { section("loading") { Row("Loading…") }; return@HigScreen }
        section("kids", "Profiles", if (family.kids.size < family.maxKids)
            "Each profile sees only its own apps plus the Apps for Everyone, with its own daily limit and bedtime. Up to ${family.maxKids} profiles."
        else "That's the maximum of ${family.maxKids} profiles.") {
            family.kids.forEach { k ->
                val bits = buildList {
                    if (family.active == k.id) add("Watching now")
                    add(if (k.dailyLimit > 0) "${minutesText(k.minutesLeft.coerceAtLeast(0))} left today" else "No daily limit")
                    if (k.bedtimeOn) add("Bedtime ${clockText(k.bedStart)}")
                }
                Row(k.name, bits.joinToString(" · "), leading = { Avatar(k.name, avatarColor(k.color), 40.dp) }, chevron = true) { open = k.id }
            }
            val g = family.guest
            Row("Guest", (if (family.active == "guest") "Watching now · " else "") +
                if (g.dailyLimit > 0) "${minutesText(g.minutesLeft.coerceAtLeast(0))} left today" else "No daily limit",
                leading = { Avatar("G", Color(0xFF5A5F6B), 40.dp) }, chevron = true) { open = "guest" }
            if (family.kids.size < family.maxKids) ButtonRow("Add Profile") { adding = true }
        }
        section("everyone", footer = "On in every profile, on top of each profile's own apps.") {
            Row("Apps for Everyone", value = "${family.commonApps.size}", chevron = true,
                leading = { com.ajit.tvhero.remote.ui.hig.IconTile(Icons.Rounded.Apps, Color(0xFF34C759)) }) { everyone = true }
        }
        section("tv", "On the TV") {
            val wake = when (family.onWake) {
                "ask" -> "Ask Who's Watching"
                "parent" -> "Parent"
                else -> family.kids.firstOrNull { it.id == family.onWake }?.name ?: "Ask"
            }
            Row("Start-up Profile", value = wake, chevron = true) { wakeSheet = true }
            Row("Parent", subtitle = "Every app. On the TV, switching to Parent needs the PIN (or the parent password).",
                value = minutesText(family.parentUsed), leading = { Avatar("P", Color(0xFF8E8E93), 40.dp) })
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        HigAlert("New Profile", "Enter a name. You can choose apps, a daily limit and bedtime next.", { adding = false },
            content = { AlertField(name, { name = it.take(24) }, "Name") },
            buttons = listOf("Cancel" to { adding = false }, "Add" to {
                adding = false
                val starter = state.apps.filter { it.state == "allowed" && it.pkg == "com.google.android.youtube.tvkids" }.map { it.pkg }.toSet()
                val kid = Kid("", name.trim(), family?.kids?.size ?: 0, starter, 60, true, 21 * 60, 7 * 60)
                gate.run("Add ${name.trim()}.") { pw ->
                    val f = state.client!!.saveKid(kid, pw)
                    state.family = f
                    open = f.kids.lastOrNull()?.id
                }
            }), enabled = { it == 0 || name.isNotBlank() })
    }
    if (wakeSheet && family != null) {
        HigSheet("Start-up Profile", onCancel = { wakeSheet = false }, done = null) {
            section("w", footer = "The TV opens in this profile when it's switched on. \"Ask\" shows Who's Watching? each time; Parent always needs the PIN on the TV.") {
                val options = family.kids.map { it.id to it.name } + listOf("parent" to "Parent", "ask" to "Ask Who's Watching")
                options.forEach { (value, label) ->
                    Row(label, trailing = { if (family.onWake == value) Icon(Icons.Rounded.Check, null, tint = Hig.colors.tint) }, onClick = {
                        wakeSheet = false
                        gate.run("Change what happens when the TV turns on.") { pw -> state.family = state.client!!.setOnWake(value, pw) }
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KidPage(state: AppState, gate: PasswordGate, kid: Kid, onBack: () -> Unit) {
    val c = Hig.colors
    val family = state.family!!
    var sheet by remember { mutableStateOf<String?>(null) }   // limit | start | end | rename | color | delete
    fun save(updated: Kid, reason: String = "Change ${kid.name}'s settings.") =
        gate.run(reason) { pw -> state.family = state.client!!.saveKid(updated, pw) }
    val watching = family.active == kid.id

    HigScreen(kid.name, onBack = onBack, backLabel = "Family") {
        item("head") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable { sheet = "color" }) { Avatar(kid.name, avatarColor(kid.color), 88.dp) }
                Text(kid.name, style = HigType.title2, modifier = Modifier.padding(top = 10.dp))
                Text(if (watching) "Watching now" else "Not watching", style = HigType.subhead,
                    color = if (watching) c.green else c.secondaryLabel)
            }
        }
        section("watch") {
            if (!watching) ButtonRow("Switch the TV to ${kid.name}") { state.run { state.family = state.client!!.activate(kid.id, null); state.refresh() } }
            Row("Name", value = kid.name, chevron = true) { sheet = "rename" }
        }
        section("time", "Screen Time", "The limit counts all TV time in ${kid.name}'s profile. The TV warns 5 minutes and 1 minute before, then shows a Time's up screen.") {
            Row("Today", value = minutesText(kid.usedToday))
            Row("Daily Limit", value = if (kid.dailyLimit == 0) "None" else minutesText(kid.dailyLimit), chevron = true) { sheet = "limit" }
            if (kid.dailyLimit > 0) {
                Row("Left Today", value = minutesText(kid.minutesLeft.coerceAtLeast(0)) + if (kid.extraToday != 0) " (incl. ${if (kid.extraToday > 0) "+" else ""}${minutesText(kid.extraToday)})" else "")
                Content(PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(15, 30, 60).forEach { m ->
                            Text("+${minutesText(m)}", style = HigType.subhead, color = c.tint, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).clip(CircleShape).background(c.tint.copy(alpha = 0.12f))
                                    .clickable { gate.run("Give ${kid.name} $m more minutes today.") { pw -> state.family = state.client!!.extraTime(kid.id, m, pw) } }
                                    .padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
        section("bed", "Bedtime", "At bedtime the TV shows a good-night screen and goes to standby a minute later. A parent can allow one more hour with the PIN.") {
            ToggleRow("Bedtime", kid.bedtimeOn) { on -> save(kid.copy(bedtimeOn = on)) }
            if (kid.bedtimeOn) {
                Row("Starts", value = clockText(kid.bedStart), chevron = true) { sheet = "start" }
                Row("Ends", value = clockText(kid.bedEnd), chevron = true) { sheet = "end" }
            }
        }
        val q = kid.quiz
        section("quiz", "Earn Time", "When time's up, ${kid.name} can answer questions on the TV: each right answer adds minutes, up to the most per day. " +
            "Three wrong in a row means a 10-minute break. The questions are built into the TV and nothing is sent anywhere.") {
            ToggleRow("Earn Time with Questions", q.enabled) { on -> save(kid.copy(quiz = q.copy(enabled = on))) }
            if (q.enabled) {
                if (kid.dailyLimit == 0) Row("Needs a daily limit", subtitle = "Questions appear on the Time's up screen, so set a Daily Limit first.")
                Row("Earned Today", value = "${minutesText(kid.quizEarnedToday)} · ${kid.quizRightToday} right")
                Row("Each Right Answer", value = minutesText(q.minutes), chevron = true) { sheet = "qmin" }
                Row("Most per Day", value = minutesText(q.dailyMax), chevron = true) { sheet = "qmax" }
                Content(PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    com.ajit.tvhero.remote.ui.hig.Segmented(QuizSettings.LEVELS, q.level - 1, Modifier.fillMaxWidth()) { i ->
                        if (i + 1 != q.level) save(kid.copy(quiz = q.copy(level = i + 1)))
                    }
                }
            }
        }
        if (q.enabled) section("topics", "Topics", "Level: Easy suits about 5 to 7 years, Medium 8 to 10, Hard 11 and up. Keep at least one topic on.") {
            QuizSettings.TOPICS.forEach { (id, label) ->
                val on = id in q.topics
                ToggleRow(label, on, enabled = !(on && q.topics.size == 1)) { v ->
                    save(kid.copy(quiz = q.copy(topics = if (v) q.topics + id else q.topics - id)))
                }
            }
        }
        val apps = state.apps.filter { !it.protected || it.pkg == "com.android.tv" }
        section("apps", "Apps for ${kid.name}", "Only these apps show on the TV for ${kid.name}. Apps for Everyone are on in every profile; Settings and the Play Store are for Parent only.") {
            apps.forEach { a ->
                val globalBlock = a.state != "allowed"
                val never = a.pkg in setOf("com.android.vending", "com.android.tv.settings")
                val forAll = a.pkg in family.commonApps
                ToggleRow(a.label, (a.pkg in kid.allowed || forAll) && !globalBlock && !never,
                    subtitle = when { never -> "Parents only"; a.state == "pending" -> "Waiting for approval"; globalBlock -> "Blocked for everyone"; forAll -> "Apps for Everyone"; else -> null },
                    enabled = !globalBlock && !never && !forAll, leading = { AppIcon(state, a.pkg, 29) }) { on ->
                    save(kid.copy(allowed = if (on) kid.allowed + a.pkg else kid.allowed - a.pkg))
                }
            }
        }
        val mine = state.sessions.filter { it.profile == kid.id }.takeLast(10).reversed()
        section("activity", "Recent Activity") {
            if (mine.isEmpty()) Row("Nothing yet", subtitle = "What ${kid.name} watches shows up here, with the title where the app shares it.")
            mine.forEach { s ->
                Row(s.title.ifBlank { s.label }, subtitle = (if (s.title.isBlank()) "" else s.label + " · ") +
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(s.start)) +
                    " · " + minutesText(((s.end - s.start) / 60000).toInt()), leading = { AppIcon(state, s.pkg, 29) })
            }
        }
        section("delete") { ButtonRow("Delete ${kid.name}'s Profile", destructive = true) { sheet = "delete" } }
    }

    when (sheet) {
        "limit" -> HigSheet("Daily Limit", onCancel = { sheet = null }, done = null) {
            section("l") {
                LIMITS.forEach { m ->
                    Row(if (m == 0) "No Limit" else minutesText(m), trailing = { if (kid.dailyLimit == m) Icon(Icons.Rounded.Check, null, tint = c.tint) },
                        onClick = { sheet = null; save(kid.copy(dailyLimit = m)) })
                }
            }
        }
        "qmin", "qmax" -> {
            val isMin = sheet == "qmin"
            val choices = if (isMin) listOf(2, 5, 10) else listOf(10, 15, 20, 30)
            val now = if (isMin) kid.quiz.minutes else kid.quiz.dailyMax
            HigSheet(if (isMin) "Each Right Answer" else "Most per Day", onCancel = { sheet = null }, done = null) {
                section("q") {
                    choices.forEach { m ->
                        Row(minutesText(m), trailing = { if (now == m) Icon(Icons.Rounded.Check, null, tint = c.tint) },
                            onClick = { sheet = null; save(kid.copy(quiz = if (isMin) kid.quiz.copy(minutes = m) else kid.quiz.copy(dailyMax = m))) })
                    }
                }
            }
        }
        "start", "end" -> {
            var t by remember { mutableIntStateOf(if (sheet == "start") kid.bedStart else kid.bedEnd) }
            val isStart = sheet == "start"
            HigSheet(if (isStart) "Bedtime Starts" else "Bedtime Ends", onCancel = { sheet = null }, onDone = {
                sheet = null; save(if (isStart) kid.copy(bedStart = t) else kid.copy(bedEnd = t))
            }) {
                item("w") { Box(Modifier.padding(top = 24.dp)) { TimeWheels(t) { t = it } } }
            }
        }
        "rename" -> {
            var name by remember { mutableStateOf(kid.name) }
            HigAlert("Name", null, { sheet = null }, content = { AlertField(name, { name = it.take(24) }, "Name") },
                buttons = listOf("Cancel" to { sheet = null }, "Save" to { sheet = null; save(kid.copy(name = name.trim())) }),
                enabled = { it == 0 || name.isNotBlank() })
        }
        "color" -> HigSheet("Colour", onCancel = { sheet = null }, done = null) {
            item("c") {
                FlowRow(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AvatarColors.forEachIndexed { i, col ->
                        Box(Modifier.size(56.dp).clip(CircleShape).background(col)
                            .then(if (i == kid.color) Modifier.border(3.dp, c.label, CircleShape) else Modifier)
                            .clickable { sheet = null; save(kid.copy(color = i)) })
                    }
                }
            }
        }
        "delete" -> HigAlert("Delete ${kid.name}'s Profile?", "Their limits, bedtime and app list are removed from the TV.", { sheet = null },
            buttons = listOf("Cancel" to { sheet = null }, "Delete" to {
                sheet = null
                gate.run("Delete ${kid.name}'s profile.", after = onBack) { pw -> state.family = state.client!!.deleteKid(kid.id, pw) }
            }), destructiveIndex = 1)
    }
}

/** Apps on in every profile (the TV's "Apps for Everyone"). Turning one on needs the password. */
@Composable
internal fun CommonAppsPage(state: AppState, gate: PasswordGate, onBack: () -> Unit) {
    val family = state.family ?: return
    val apps = state.apps.filter { it.state == "allowed" && it.pkg !in setOf("com.android.vending", "com.android.tv.settings") && (!it.protected || it.pkg == "com.android.tv") }
    HigScreen("Apps for Everyone", onBack = onBack, backLabel = "Family") {
        section("a", footer = "These apps are on in every profile, on top of each profile's own apps. Parent always has every app.") {
            apps.forEach { a ->
                ToggleRow(a.label, a.pkg in family.commonApps, leading = { AppIcon(state, a.pkg, 29) }) { on ->
                    val next = if (on) family.commonApps + a.pkg else family.commonApps - a.pkg
                    gate.run(if (on) "Turn on ${a.label} in every profile." else "Turn off ${a.label} for everyone.") { pw ->
                        state.family = state.client!!.setCommonApps(next, pw)
                    }
                }
            }
        }
    }
}

/** Everything watched in the last 7 days, newest first, grouped by day. */
@Composable
fun ActivityPage(state: AppState, onBack: () -> Unit) {
    val c = Hig.colors
    val family = state.family
    val byDay = state.sessions.reversed().groupBy {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL).format(java.util.Date(it.start))
    }
    HigScreen("Activity", onBack = onBack, backLabel = "TV") {
        if (byDay.isEmpty()) section("none") { Row("Nothing watched in the last 7 days") }
        byDay.entries.forEachIndexed { i, (day, list) ->
            section("d$i", day) {
                list.forEach { s ->
                    val who = if (s.profile == "parent") "Parent" else family?.kids?.firstOrNull { it.id == s.profile }?.name ?: "Someone"
                    Row(s.title.ifBlank { s.label }, subtitle = "$who · " + (if (s.title.isBlank()) "" else s.label + " · ") +
                        timeText(s.start) + " · " + minutesText(((s.end - s.start) / 60000).toInt()), leading = { AppIcon(state, s.pkg, 29) })
                }
            }
        }
    }
}

/** Guest: every app except the parents-only ones, no history; here only its daily time. */
@Composable
internal fun GuestPage(state: AppState, gate: PasswordGate, onBack: () -> Unit) {
    val c = Hig.colors
    val g = state.family?.guest ?: return
    var sheet by remember { mutableStateOf(false) }
    HigScreen("Guest", onBack = onBack, backLabel = "Family") {
        section("time", "Screen Time", "One allowance a day, shared by every guest that day. When it's used up the TV shows Time's up; " +
            "guests can ask for more time and it appears under Needs You.") {
            Row("Today", value = minutesText(g.usedToday))
            Row("Daily Limit", value = if (g.dailyLimit == 0) "None" else minutesText(g.dailyLimit), chevron = true) { sheet = true }
            if (g.dailyLimit > 0) {
                Row("Left Today", value = minutesText(g.minutesLeft.coerceAtLeast(0)) + if (g.extraToday != 0) " (incl. +${minutesText(g.extraToday)})" else "")
                Content(PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(15, 30, 60).forEach { m ->
                            Text("+${minutesText(m)}", style = HigType.subhead, color = c.tint, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).clip(CircleShape).background(c.tint.copy(alpha = 0.12f))
                                    .clickable { gate.run("Give guests $m more minutes today.") { pw -> state.family = state.client!!.extraTime("guest", m, pw) } }
                                    .padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
    if (sheet) HigSheet("Guest Daily Limit", onCancel = { sheet = false }, done = null) {
        section("l") {
            LIMITS.forEach { m ->
                Row(if (m == 0) "No Limit" else minutesText(m), trailing = { if (g.dailyLimit == m) Icon(Icons.Rounded.Check, null, tint = c.tint) },
                    onClick = { sheet = false; gate.run("Change Guest's daily limit.") { pw -> state.family = state.client!!.setGuestLimit(m, pw) } })
            }
        }
    }
}
