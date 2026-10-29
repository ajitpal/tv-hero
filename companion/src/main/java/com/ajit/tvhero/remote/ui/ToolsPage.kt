package com.ajit.tvhero.remote.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Content
import com.ajit.tvhero.remote.ui.hig.FieldRow
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigSheet
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.section
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** TV tab → Tools: start and control the TV's Timer, Stopwatch and Workout from the phone (Phase 3, M3). */
@Composable
internal fun ToolsPage(state: AppState, onBack: () -> Unit) {
    val c = Hig.colors
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var custom by remember { mutableStateOf(false) }
    // Live while the page is open: once a second.
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { data = state.client!!.tools() }
            delay(1000)
        }
    }
    fun start(body: JSONObject) = state.run { data = state.client!!.startTool(body) }
    fun control(a: String) = state.run { data = state.client!!.toolControl(a) }
    val st = data?.optJSONObject("state")
    val tool = st?.optString("tool") ?: "none"

    HigScreen("Tools", onBack = onBack, backLabel = "TV") {
        if (st != null && tool != "none") item("live") { LiveCard(st, ::control) }
        section("timer", "Timer", "Counts down on the TV in big digits and beeps at the end, wherever the TV is. Back on the remote keeps it running.") {
            Content(PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10, 15, 30, 60).forEach { m ->
                        Chip(if (m < 60) "$m" else "1 h", Modifier.weight(1f)) {
                            start(JSONObject().put("tool", "timer").put("minutes", m))
                        }
                    }
                }
            }
        }
        section("sw", "Stopwatch") {
            ButtonRow("Start the Stopwatch") { start(JSONObject().put("tool", "stopwatch")) }
        }
        val presets = data?.optJSONArray("presets") ?: JSONArray()
        section("workout", "Workout", "Get ready, then work and rest in big colours with 3-2-1 beeps. Any music playing on the TV is turned down under the beeps.") {
            for (i in 0 until presets.length()) {
                val p = presets.getJSONObject(i)
                Row(p.optString("name"), subtitle = summary(p), chevron = true) {
                    start(JSONObject().put("tool", "workout").put("workout", p))
                }
            }
            ButtonRow("Build Your Own…") { custom = true }
            val saved = loadCustom(ctx)
            if (saved != null) Row(saved.optString("name"), subtitle = summary(saved) + " · yours", chevron = true) {
                start(JSONObject().put("tool", "workout").put("workout", saved))
            }
        }
        section("clock", footer = "The watch screensaver; any key on the remote closes it.") {
            ButtonRow("Show the Clock on the TV") { start(JSONObject().put("tool", "clock")) }
        }
    }

    if (custom) {
        val saved = loadCustom(ctx)
        var name by remember { mutableStateOf(saved?.optString("name") ?: "My Workout") }
        var work by remember { mutableStateOf((saved?.optInt("work") ?: 40).toString()) }
        var rest by remember { mutableStateOf((saved?.optInt("rest") ?: 20).toString()) }
        var rounds by remember { mutableStateOf((saved?.optInt("rounds") ?: 8).toString()) }
        var sets by remember { mutableStateOf((saved?.optInt("sets") ?: 1).toString()) }
        var setRest by remember { mutableStateOf((saved?.optInt("set_rest") ?: 60).toString()) }
        var exercises by remember {
            mutableStateOf(saved?.optJSONArray("exercises")?.let { a -> (0 until a.length()).joinToString(", ") { a.getString(it) } } ?: "")
        }
        fun spec() = JSONObject().put("name", name.trim().ifBlank { "My Workout" }).put("prepare", 10)
            .put("work", work.toIntOrNull() ?: 40).put("rest", rest.toIntOrNull() ?: 0)
            .put("rounds", rounds.toIntOrNull() ?: 8).put("sets", sets.toIntOrNull() ?: 1).put("set_rest", setRest.toIntOrNull() ?: 0)
            .put("exercises", JSONArray(exercises.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
        HigSheet("Your Workout", onCancel = { custom = false }, done = "Start", onDone = {
            custom = false
            val s = spec()
            saveCustom(ctx, s)
            start(JSONObject().put("tool", "workout").put("workout", s))
        }) {
            section("f", footer = "Seconds for work and rest. Exercises (optional, separated by commas) show on the TV in turn, e.g. Squats, Push-ups, Plank.") {
                FieldRow("Name", name, { name = it.take(30) })
                FieldRow("Work (s)", work, { work = it.filter(Char::isDigit).take(3) }, keyboard = KeyboardType.Number)
                FieldRow("Rest (s)", rest, { rest = it.filter(Char::isDigit).take(3) }, keyboard = KeyboardType.Number)
                FieldRow("Rounds", rounds, { rounds = it.filter(Char::isDigit).take(2) }, keyboard = KeyboardType.Number)
                FieldRow("Sets", sets, { sets = it.filter(Char::isDigit).take(2) }, keyboard = KeyboardType.Number)
                FieldRow("Set rest (s)", setRest, { setRest = it.filter(Char::isDigit).take(3) }, keyboard = KeyboardType.Number)
                FieldRow("Exercises", exercises, { exercises = it.take(400) }, placeholder = "optional")
            }
        }
    }
}

@Composable
private fun LiveCard(st: JSONObject, control: (String) -> Unit) {
    val c = Hig.colors
    val tool = st.optString("tool")
    val running = st.optBoolean("running")
    val finished = st.optBoolean("finished")
    val phase = st.optJSONObject("phase")
    val big = when (tool) {
        "timer" -> clock(st.optLong("left_ms"))
        "stopwatch" -> clock(st.optLong("elapsed_ms"))
        else -> if (finished || phase == null) "Done" else ((st.optLong("phase_left_ms") + 999) / 1000).toString()
    }
    val title = when (tool) {
        "timer" -> if (finished) "Time's up" else st.optString("label").ifBlank { "Timer" }
        "stopwatch" -> "Stopwatch"
        else -> phase?.optString("label") ?: st.optString("label")
    }
    val line = when {
        finished -> "Finished"
        tool == "workout" && phase != null -> "Round ${phase.optInt("round")} of ${phase.optInt("rounds")}" +
            (if (phase.optInt("sets") > 1) " · Set ${phase.optInt("set")} of ${phase.optInt("sets")}" else "") + if (!running) " · Paused" else ""
        !running -> "Paused"
        else -> "Running on the TV"
    }
    val tint = when (phase?.optString("kind")) { "work" -> c.destructive; "rest" -> c.green; else -> c.tint }
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(c.cell).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title.uppercase(), style = HigType.footnote, color = if (tool == "workout") tint else c.secondaryLabel)
        Text(big, fontSize = 56.sp, fontWeight = FontWeight.Light, color = c.label)
        Text(line, style = HigType.subhead, color = c.secondaryLabel)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!finished) Chip(if (running) "Pause" else "Resume", Modifier.weight(1f)) { control("toggle") }
            when (tool) {
                "timer" -> Chip("+1 min", Modifier.weight(1f)) { control("add_minute") }
                "stopwatch" -> if (!finished) Chip("Lap", Modifier.weight(1f)) { control("lap") }
                "workout" -> if (!finished) Chip("Skip", Modifier.weight(1f)) { control("skip") }
            }
            Chip(if (finished) "Close" else "Stop", Modifier.weight(1f)) { control("stop") }
        }
    }
}

@Composable
private fun Chip(label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Hig.colors
    Text(label, style = HigType.subhead, color = c.tint, textAlign = TextAlign.Center,
        modifier = modifier.clip(CircleShape).background(c.tint.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(vertical = 9.dp))
}

private fun clock(ms: Long): String {
    val t = maxOf(0L, ms) / 1000
    val h = t / 3600; val m = t / 60 % 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun summary(p: JSONObject): String {
    val work = p.optInt("work"); val rest = p.optInt("rest"); val rounds = p.optInt("rounds"); val sets = p.optInt("sets", 1)
    return "${work}s work" + (if (rest > 0) " · ${rest}s rest" else "") + " · $rounds rounds" + if (sets > 1) " × $sets sets" else ""
}

private fun loadCustom(ctx: Context): JSONObject? =
    ctx.getSharedPreferences("tools", Context.MODE_PRIVATE).getString("custom_workout", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

private fun saveCustom(ctx: Context, o: JSONObject) =
    ctx.getSharedPreferences("tools", Context.MODE_PRIVATE).edit().putString("custom_workout", o.toString()).apply()
