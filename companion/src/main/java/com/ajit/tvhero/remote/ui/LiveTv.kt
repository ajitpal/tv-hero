package com.ajit.tvhero.remote.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ajit.tvhero.remote.AppState
import com.ajit.tvhero.remote.Geocoding
import com.ajit.tvhero.remote.TvWeather
import com.ajit.tvhero.remote.ui.hig.ButtonRow
import com.ajit.tvhero.remote.ui.hig.Hig
import com.ajit.tvhero.remote.ui.hig.HigScreen
import com.ajit.tvhero.remote.ui.hig.HigType
import com.ajit.tvhero.remote.ui.hig.Row
import com.ajit.tvhero.remote.ui.hig.SearchField
import com.ajit.tvhero.remote.ui.hig.ToggleRow
import com.ajit.tvhero.remote.ui.hig.section
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

// ---------- 2.11 Live and free TV: weather and festivals ----------

/**
 * Settings → Weather & Festivals. The city search runs here on the phone; the TV gets only the chosen
 * city's name and position and asks Open-Meteo about once an hour.
 */
@Composable
fun WeatherPage(state: AppState, preview: TvWeather? = null, previewFestival: Pair<Boolean, String>? = null, onBack: () -> Unit) {
    val c = Hig.colors
    var weather by remember { mutableStateOf(preview) }
    var festivals by remember { mutableStateOf(previewFestival) }
    var old by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Geocoding.City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    if (preview == null) LaunchedEffect(Unit) {
        state.run {
            weather = state.client!!.weather().also { if (it == null) old = true }
            festivals = state.client!!.festivals()
        }
    }
    LaunchedEffect(query) {
        if (preview != null) return@LaunchedEffect
        results = emptyList(); searchError = null
        if (query.trim().length < 2) return@LaunchedEffect
        delay(400)   // search when typing pauses
        searching = true
        try { results = Geocoding.search(query) } catch (e: Exception) { searchError = e.message ?: "The city search didn't answer." }
        searching = false
    }
    HigScreen("Weather & Festivals", onBack = onBack, backLabel = "Settings") {
        if (old) { section("old", footer = "Update the TV app (Settings → About → TV App) to set these.") { Row("This TV's app is older than 2.11") }; return@HigScreen }
        val w = weather
        if (w == null) { section("l") { Row("Loading…") }; return@HigScreen }
        if (w.on) {
            section("now", "Weather on the TV", "The TV asks ${w.host.ifBlank { "api.open-meteo.com" }} about once an hour while it's on, " +
                "sending only this city's position. It's the TV's only connection to the internet. ${w.credit}.") {
                Row(w.city, subtitle = when {
                    w.temp != null -> "${w.temp}° · ${w.text}\n${w.outlook}"
                    w.error.isNotBlank() -> "Couldn't fetch it yet: ${w.error}"
                    else -> "Waiting for the first reading"
                }, value = if (w.fetchedAt > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(w.fetchedAt)) else null)
                ToggleRow("On the Screensaver", w.screensaver, subtitle = "Now and tomorrow under the watch, and a sky that matches") { on ->
                    state.run { weather = state.client!!.setWeatherScreensaver(on) }
                }
                ButtonRow("Update Now") { state.run { weather = state.client!!.refreshWeather() } }
                ButtonRow("Turn Off Weather", destructive = true) { state.run { weather = state.client!!.weatherOff() } }
            }
        }
        item("search") {
            androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp).padding(top = 24.dp)) {
                Text((if (w.on) "Change City" else "Choose a City").uppercase(), style = HigType.footnote, color = c.secondaryLabel,
                    modifier = Modifier.padding(start = 16.dp, bottom = 7.dp))
                SearchField(query, { query = it }, "City or town")
            }
        }
        if (searching || searchError != null || results.isNotEmpty()) section("results") {
            if (searching) Row("Searching…")
            searchError?.let { Row(it) }
            results.forEach { city ->
                Row(city.name, subtitle = city.label) {
                    query = ""; results = emptyList()
                    state.run { weather = state.client!!.setCity(city) }
                }
            }
        }
        item("credit") {
            Text("A city, not your location: no GPS. City search by Open-Meteo, run on this phone.", style = HigType.footnote,
                color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        }
        val f = festivals
        if (f != null) section("festivals", "Festivals", "Under the date on Home: India's gazetted holidays (Government of India list, 2026 and 2027). " +
            "Diwali and Dussehra also dress up Home for the day. Dates of Eid and Muharram depend on the moon.") {
            ToggleRow("Festival Line", f.first, subtitle = f.second.ifBlank { "Nothing in the next 10 days" }) { on ->
                state.run { festivals = state.client!!.setFestivals(on) }
            }
        }
    }
}
