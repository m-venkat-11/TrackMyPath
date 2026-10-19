package com.example.pathwise.data.geocoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

data class GeocodedPlace(
    val displayName: String,
    val shortName: String,
    val latitude: Double,
    val longitude: Double
)

data class AreaDetails(
    val street: String,
    val neighborhood: String,
    val city: String,
    val state: String,
    val formattedHeadline: String,
    val formattedSubtitle: String
)

data class NearbyPlace(
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val bearingDegrees: Int,
    val cardinalDirection: String
)

/**
 * Spatial Intelligence & Geocoding Service.
 * Provides real-time reverse-geocoding, live area awareness, and prominent landmark discovery
 * for active city learning and mental map retention.
 */
object GeocodingRepository {

    private const val USER_AGENT = "PathWise-Spatial-Intelligence/2.0 (contact: support@pathwise.local)"
    private val reverseCache = ConcurrentHashMap<String, String>()
    private val areaCache = ConcurrentHashMap<String, AreaDetails>()
    private val nearbyCache = ConcurrentHashMap<String, List<NearbyPlace>>()

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        val cacheKey = String.format(java.util.Locale.US, "%.4f,%.4f", latitude, longitude)
        reverseCache[cacheKey]?.let { return@withContext it }

        val details = getAreaDetails(latitude, longitude)
        if (details != null) {
            val res = "${details.street}${if (details.neighborhood.isNotBlank()) ", " + details.neighborhood else ""}"
            reverseCache[cacheKey] = res
            return@withContext res
        }
        null
    }

    suspend fun getAreaDetails(latitude: Double, longitude: Double): AreaDetails? = withContext(Dispatchers.IO) {
        val cacheKey = String.format(java.util.Locale.US, "%.4f,%.4f", latitude, longitude)
        areaCache[cacheKey]?.let { return@withContext it }

        try {
            val urlStr = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val address = json.optJSONObject("address")

                val road = address?.optString("road")?.takeIf { it.isNotBlank() }
                    ?: address?.optString("pedestrian")?.takeIf { it.isNotBlank() }
                    ?: address?.optString("highway")?.takeIf { it.isNotBlank() }
                    ?: "Live Path"

                val suburb = address?.optString("suburb")?.takeIf { it.isNotBlank() }
                    ?: address?.optString("neighbourhood")?.takeIf { it.isNotBlank() }
                    ?: address?.optString("residential")?.takeIf { it.isNotBlank() }
                    ?: ""

                val city = address?.optString("city")?.takeIf { it.isNotBlank() }
                    ?: address?.optString("town")?.takeIf { it.isNotBlank() }
                    ?: address?.optString("municipality")?.takeIf { it.isNotBlank() }
                    ?: address?.optString("county")?.takeIf { it.isNotBlank() }
                    ?: ""

                val state = address?.optString("state") ?: ""

                val headline = if (suburb.isNotBlank()) "$road • $suburb" else road
                val subtitle = if (city.isNotBlank() && state.isNotBlank()) "$city, $state" else city.ifBlank { "GPS Active" }

                val details = AreaDetails(
                    street = road,
                    neighborhood = suburb,
                    city = city,
                    state = state,
                    formattedHeadline = headline,
                    formattedSubtitle = subtitle
                )
                areaCache[cacheKey] = details
                return@withContext details
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun fetchNearbyLandmarks(latitude: Double, longitude: Double): List<NearbyPlace> = withContext(Dispatchers.IO) {
        val cacheKey = String.format(java.util.Locale.US, "%.3f,%.3f", latitude, longitude)
        nearbyCache[cacheKey]?.let { return@withContext it }

        val places = mutableListOf<NearbyPlace>()

        // 1. Try Overpass API for real prominent POIs in 1.2km radius
        try {
            val query = """
                [out:json][timeout:4];
                (
                  node["amenity"~"hospital|college|university|bus_station|place_of_worship"](around:1200,$latitude,$longitude);
                  node["railway"~"station|subway_entrance"](around:1200,$latitude,$longitude);
                  node["tourism"~"attraction|museum|monument|viewpoint"](around:1200,$latitude,$longitude);
                  node["leisure"~"park|stadium"](around:1200,$latitude,$longitude);
                );
                out center 12;
            """.trimIndent()

            val encoded = URLEncoder.encode(query, "UTF-8")
            val conn = (URL("https://overpass-api.de/api/interpreter?data=$encoded").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 3500
                readTimeout = 3500
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val elements = root.optJSONArray("elements") ?: JSONArray()

                for (i in 0 until elements.length()) {
                    val elem = elements.getJSONObject(i)
                    val tags = elem.optJSONObject("tags") ?: continue
                    val name = tags.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val pLat = elem.optDouble("lat", Double.NaN)
                    val pLon = elem.optDouble("lon", Double.NaN)
                    if (pLat.isNaN() || pLon.isNaN()) continue

                    val category = when {
                        tags.has("railway") -> "Transit Hub"
                        tags.has("amenity") && tags.getString("amenity").contains("hospital") -> "Medical"
                        tags.has("amenity") && (tags.getString("amenity").contains("college") || tags.getString("amenity").contains("university")) -> "Education"
                        tags.has("tourism") -> "Monument & Culture"
                        tags.has("leisure") -> "Park & Leisure"
                        else -> "Landmark"
                    }

                    val dist = calculateDistanceMeters(latitude, longitude, pLat, pLon).toInt()
                    val bearing = calculateBearingDegrees(latitude, longitude, pLat, pLon)
                    val cardinal = bearingToCardinal(bearing)

                    places.add(NearbyPlace(name, category, pLat, pLon, dist, bearing, cardinal))
                }
            }
        } catch (_: Exception) {}

        // 2. If Overpass failed or returned empty (e.g. offline/timeout), use Nominatim reverse nearby search
        if (places.isEmpty()) {
            try {
                val conn = (URL("https://nominatim.openstreetmap.org/search?format=json&q=landmark&bounded=1&viewbox=${longitude - 0.015},${latitude + 0.015},${longitude + 0.015},${latitude - 0.015}&limit=6").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    val array = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val name = obj.optString("display_name").split(",").firstOrNull()?.trim() ?: continue
                        val pLat = obj.optDouble("lat")
                        val pLon = obj.optDouble("lon")
                        val dist = calculateDistanceMeters(latitude, longitude, pLat, pLon).toInt()
                        val bearing = calculateBearingDegrees(latitude, longitude, pLat, pLon)
                        val cardinal = bearingToCardinal(bearing)
                        places.add(NearbyPlace(name, "Point of Interest", pLat, pLon, dist, bearing, cardinal))
                    }
                }
            } catch (_: Exception) {}
        }

        val sorted = places.sortedBy { it.distanceMeters }
        if (sorted.isNotEmpty()) {
            nearbyCache[cacheKey] = sorted
        }
        sorted
    }

    suspend fun searchPlaces(query: String): List<GeocodedPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<GeocodedPlace>()

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlStr = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5&addressdetails=1"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 5000
                readTimeout = 5000
            }

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
        } catch (_: Exception) {}
        results
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
        val y = sin(Math.toRadians(lon2 - lon1)) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(lon2 - lon1))
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).roundToInt()
    }

    fun bearingToCardinal(bearing: Int): String {
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((bearing + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }
}
