package com.example.pathwise.data.repository

import kotlin.math.*

/**
 * Geodesic / Haversine distance calculations and telemetry formatters per PRD FR-06.
 */
object LocationUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two coordinates in metres using the Haversine formula.
     */
    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Formats distance into human-readable string (metres or kilometres).
     */
    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            "${meters.roundToInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.2f km", meters / 1000.0)
        }
    }

    /**
     * Formats duration in seconds into HH:MM:SS or MM:SS format.
     */
    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
        }
    }

    /**
     * Formats speed in m/s to km/h.
     */
    fun formatSpeed(speedMps: Float?): String {
        if (speedMps == null || speedMps < 0.1f) return "0.0 km/h"
        val kmh = speedMps * 3.6f
        return String.format(java.util.Locale.US, "%.1f km/h", kmh)
    }
}
