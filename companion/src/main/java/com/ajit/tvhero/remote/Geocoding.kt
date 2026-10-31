package com.ajit.tvhero.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * City search for the TV's weather (2.11), on the phone: Open-Meteo's free geocoding (no account). The TV
 * only ever gets the chosen city's name and position.
 */
object Geocoding {
    data class City(val name: String, val region: String, val country: String, val lat: Double, val lon: Double) {
        val label get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
    }

    suspend fun search(query: String): List<City> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&format=json&name=" + URLEncoder.encode(q, "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("The city search answered ${c.responseCode}.")
            parse(c.inputStream.use { String(it.readBytes().take(256 * 1024).toByteArray()) })
        } finally {
            c.disconnect()
        }
    }

    fun parse(body: String): List<City> {
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            City(it.optString("name"), it.optString("admin1"), it.optString("country"), it.optDouble("latitude"), it.optDouble("longitude"))
        }.filter { it.name.isNotBlank() && !it.lat.isNaN() && !it.lon.isNaN() }
    }
}
