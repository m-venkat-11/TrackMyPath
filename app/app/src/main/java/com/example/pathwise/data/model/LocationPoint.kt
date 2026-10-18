package com.example.pathwise.data.model

/**
 * Quality status of a recorded location point per PRD FR-03.
 */
enum class PointQuality {
    ACCEPTED,
    REJECTED_ACCURACY,
    REJECTED_SPEED
}

/**
 * LocationPoint data model persisted incrementally to SQLite per PRD FR-03 & FR-04.
 */
data class LocationPoint(
    val id: Long = 0L,
    val journeyId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speed: Float? = null,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val qualityStatus: PointQuality = PointQuality.ACCEPTED,
)
