package com.example.pathwise.data.model

/**
 * Honest tracking state per PRD FR-13.
 * UI state must be driven by actual tracking/service state, not animation state.
 */
enum class TrackingState {
    IDLE,
    WAITING_FOR_GPS,
    RECORDING,
    PAUSED,
    PERMISSION_NEEDED,
    INTERRUPTED,
    ERROR;

    val displayLabel: String
        get() = when (this) {
            IDLE -> "Standby"
            WAITING_FOR_GPS -> "Acquiring GPS Signal…"
            RECORDING -> "Recording Path"
            PAUSED -> "Tracking Paused"
            PERMISSION_NEEDED -> "Location Permission Required"
            INTERRUPTED -> "Interrupted — Recovery Available"
            ERROR -> "GPS Hardware Unavailable"
        }
}
