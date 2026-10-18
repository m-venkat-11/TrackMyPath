package com.example.pathwise.data.model

import java.util.UUID

/**
 * Status of a journey per PRD FR-05.
 */
enum class JourneyStatus {
    RECORDING,
    PAUSED,
    COMPLETED,
    INTERRUPTED
}

/**
 * Core Journey data model per PRD FR-03 and Section 6.5.
 */
data class Journey(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Journey on " + java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationSeconds: Long = 0L,
    val recordedDistanceMeters: Double = 0.0,
    val pointCount: Int = 0,
    val status: JourneyStatus = JourneyStatus.RECORDING,
    val isDemo: Boolean = false,
)
