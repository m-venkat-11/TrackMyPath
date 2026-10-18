package com.example.pathwise.data.geocoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class GeocodedPlace(
    val displayName: String,
    val shortName: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * OpenStreetMap Nominatim Geocoding and Reverse-Geocoding service per PRD FR-07 & 6.7.
 * Uses community OpenStreetMap public API with user-agent attribution and memory caching.
 */
object GeocodingRepository {

    private const val USER_AGENT = "PathWise-Android-Holographic/1.0 (contact: support@pathwise.local)"
    private val reverseCache = ConcurrentHashMap<String, String>()

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        val cacheKey = String.format(java.util.Locale.US, "%.4f,%.4f", latitude, longitude)
        reverseCache[cacheKey]?.let { return@withContext it }

        try {
            val urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val address = json.optJSONObject("address")
                val road = address?.optString("road")?.takeIf { it.isNotBlank() }
                val suburb = (address?.optString("suburb")?.takeIf { it.isNotBlank() })
                    ?: (address?.optString("neighbourhood")?.takeIf { it.isNotBlank() })
                val city = (address?.optString("city")?.takeIf { it.isNotBlank() })
                    ?: (address?.optString("town")?.takeIf { it.isNotBlank() })

                val formatted = when {
                    road != null && suburb != null -> "$road, $suburb"
                    road != null && city != null -> "$road, $city"
                    road != null -> road
                    suburb != null && city != null -> "$suburb, $city"
                    else -> json.optString("display_name", "Unknown Area").split(",").take(2).joinToString(", ").trim()
                }

                if (formatted.isNotBlank()) {
                    reverseCache[cacheKey] = formatted
                    return@withContext formatted
                }
            }
        } catch (e: Exception) {
            // Offline or rate-limited; gracefully fallback
        }
        null
    }

    suspend fun searchPlaces(query: String): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<GeocodedPlace>()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlStr = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5&addressdetails=1"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(response)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val lat = obj.getDouble("lat")
                    val lon = obj.getDouble("lon")
                    val fullDisplay = obj.getString("display_name")
                    val short = fullDisplay.split(",").take(2).joinToString(", ").trim()
                    results.add(GeocodedPlace(fullDisplay, short, lat, lon))
                }
            }
        } catch (e: Exception) {
            // Network failure or timeout
        }
        results
    }
}
