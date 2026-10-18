package com.example.pathwise.data.repository

import android.content.Context
import com.example.pathwise.data.local.LandmarkDao
import com.example.pathwise.data.local.PathWiseDatabaseHelper
import com.example.pathwise.data.model.Landmark
import com.example.pathwise.data.model.LandmarkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Repository for user and provider landmarks per PRD Section 6.8.
 */
class LandmarkRepository(context: Context) {

    private val dbHelper = PathWiseDatabaseHelper.getInstance(context)
    private val landmarkDao = LandmarkDao(dbHelper)

    private val _landmarks = MutableStateFlow<List<Landmark>>(emptyList())
    val landmarks: StateFlow<List<Landmark>> = _landmarks.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val list = landmarkDao.getAllLandmarks()
        _landmarks.value = list
    }

    suspend fun addLandmark(
        name: String,
        category: String? = null,
        notes: String? = null,
        latitude: Double,
        longitude: Double,
        source: LandmarkSource = LandmarkSource.USER_CREATED
    ): Landmark = withContext(Dispatchers.IO) {
        val lm = Landmark(
            name = name,
            category = category,
            notes = notes,
            latitude = latitude,
            longitude = longitude,
            source = source
        )
        landmarkDao.insertLandmark(lm)
        refresh()
        lm
    }

    suspend fun deleteLandmark(id: String) = withContext(Dispatchers.IO) {
        landmarkDao.deleteLandmark(id)
        refresh()
    }

    suspend fun seedDemoLandmarks() = withContext(Dispatchers.IO) {
        if (landmarkDao.getAllLandmarks().isEmpty()) {
            addLandmark(
                name = "Central Clock Tower",
                category = "Historic",
                notes = "Prominent brick tower at main square junction",
                latitude = 12.9716,
                longitude = 77.5946
            )
            addLandmark(
                name = "North Bridge Crossing",
                category = "Transit",
                notes = "Pedestrian archway over canal",
                latitude = 12.9784,
                longitude = 77.5991
            )
            addLandmark(
                name = "Riverside Promenade",
                category = "Scenic",
                notes = "Scenic pedestrian pathway with stone benches",
                latitude = 12.9866,
                longitude = 77.6091
            )
        }
    }
}
