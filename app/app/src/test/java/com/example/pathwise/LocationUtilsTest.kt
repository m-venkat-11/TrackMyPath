package com.example.pathwise

import com.example.pathwise.data.repository.LocationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationUtilsTest {

    @Test
    fun testHaversineDistanceZeroForSamePoint() {
        val dist = LocationUtils.haversineMeters(12.9716, 77.5946, 12.9716, 77.5946)
        assertEquals(0.0, dist, 0.01)
    }

    @Test
    fun testHaversineDistanceKnownPoints() {
        // Distance between Bangalore (12.9716, 77.5946) and Mysore (12.2958, 76.6394) is ~128 km
        val dist = LocationUtils.haversineMeters(12.9716, 77.5946, 12.2958, 76.6394)
        assertTrue(dist > 120_000 && dist < 140_000)
    }

    @Test
    fun testFormatDistanceMeters() {
        val formatted = LocationUtils.formatDistance(450.0)
        assertEquals("450 m", formatted)
    }

    @Test
    fun testFormatDistanceKilometers() {
        val formatted = LocationUtils.formatDistance(3450.0)
        assertEquals("3.45 km", formatted)
    }

    @Test
    fun testFormatDurationSeconds() {
        val mmSs = LocationUtils.formatDuration(125)
        assertEquals("02:05", mmSs)

        val hhMmSs = LocationUtils.formatDuration(3665)
        assertEquals("01:01:05", hhMmSs)
    }

    @Test
    fun testFormatSpeed() {
        val formatted = LocationUtils.formatSpeed(10.0f) // 10 m/s = 36 km/h
        assertEquals("36.0 km/h", formatted)
    }
}
