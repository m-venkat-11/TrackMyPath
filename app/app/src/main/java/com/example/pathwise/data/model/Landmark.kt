package com.example.pathwise.data.model

import java.util.UUID

enum class LandmarkSource {
    USER_CREATED,
    PROVIDER_SUPPLIED
}

/**
 * Personal landmark entity per PRD Section 6.8.
 * User-created labels must not be silently overwritten by provider data.
 */
data class Landmark(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String? = null,
    val notes: String? = null,
    val latitude: Double,
    val longitude: Double,
    val source: LandmarkSource = LandmarkSource.USER_CREATED,
    val createdAt: Long = System.currentTimeMillis(),
)
