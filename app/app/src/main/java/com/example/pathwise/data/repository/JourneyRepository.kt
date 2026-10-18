package com.example.pathwise.data.repository

import android.content.Context
import com.example.pathwise.data.local.JourneyDao
import com.example.pathwise.data.local.LocationPointDao
import com.example.pathwise.data.local.PathWiseDatabaseHelper
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.model.JourneyStatus
import com.example.pathwise.data.model.LocationPoint
import com.example.pathwise.data.model.PointQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Main repository orchestrating journey lifecycle, point writes, and interrupted recovery.
 */
class JourneyRepository(context: Context) {

    private val dbHelper = PathWiseDatabaseHelper.getInstance(context)
    private val journeyDao = JourneyDao(dbHelper)
    private val pointDao = LocationPointDao(dbHelper)

    private val _activeJourney = MutableStateFlow<Journey?>(null)
    val activeJourney: StateFlow<Journey?> = _activeJourney.asStateFlow()

    private val _activePoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val activePoints: StateFlow<List<LocationPoint>> = _activePoints.asStateFlow()

    private val _journeysList = MutableStateFlow<List<Journey>>(emptyList())
    val journeysList: StateFlow<List<Journey>> = _journeysList.asStateFlow()

    suspend fun refreshJourneys() = withContext(Dispatchers.IO) {
        val list = journeyDao.getAllJourneys()
        _journeysList.value = list
    }

    suspend fun startNewJourney(customTitle: String? = null): Journey = withContext(Dispatchers.IO) {
        val journey = Journey(
            title = customTitle ?: ("Journey #" + (journeyDao.getAllJourneys().size + 1))
        )
        journeyDao.insertJourney(journey)
        _activeJourney.value = journey
        _activePoints.value = emptyList()
        refreshJourneys()
        journey
    }

    suspend fun addPoint(
        journeyId: String,
        latitude: Double,
        longitude: Double,
        altitude: Double?,
        speed: Float?,
        accuracy: Float?
    ): LocationPoint = withContext(Dispatchers.IO) {
        // Basic quality filter per PRD FR-03: discard wildly noisy fixes (> 50m accuracy error)
        val quality = if (accuracy != null && accuracy > 50f) {
            PointQuality.REJECTED_ACCURACY
        } else {
            PointQuality.ACCEPTED
        }

        val point = LocationPoint(
            journeyId = journeyId,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            speed = speed,
            accuracy = accuracy,
            timestamp = System.currentTimeMillis(),
            qualityStatus = quality
        )

        pointDao.insertPoint(point)

        if (quality == PointQuality.ACCEPTED) {
            val currentJourney = _activeJourney.value
            if (currentJourney != null && currentJourney.id == journeyId) {
                val previousPoints = _activePoints.value
                val newPoints = previousPoints + point
                _activePoints.value = newPoints

                // Incremental distance calculation
                val lastPoint = previousPoints.lastOrNull { it.qualityStatus == PointQuality.ACCEPTED }
                val deltaDistance = if (lastPoint != null) {
                    LocationUtils.haversineMeters(lastPoint.latitude, lastPoint.longitude, latitude, longitude)
                } else 0.0

                val updatedDistance = currentJourney.recordedDistanceMeters + deltaDistance
                val updatedDuration = (System.currentTimeMillis() - currentJourney.startTime) / 1000
                val updatedPointCount = newPoints.count { it.qualityStatus == PointQuality.ACCEPTED }

                journeyDao.updateMetrics(journeyId, updatedDuration, updatedDistance, updatedPointCount)
                _activeJourney.value = currentJourney.copy(
                    recordedDistanceMeters = updatedDistance,
                    durationSeconds = updatedDuration,
                    pointCount = updatedPointCount
                )
            }
        }

        point
    }

    suspend fun pauseJourney(journeyId: String) = withContext(Dispatchers.IO) {
        journeyDao.updateStatus(journeyId, JourneyStatus.PAUSED)
        val cur = _activeJourney.value
        if (cur != null && cur.id == journeyId) {
            _activeJourney.value = cur.copy(status = JourneyStatus.PAUSED)
        }
        refreshJourneys()
    }

    suspend fun resumeJourney(journeyId: String) = withContext(Dispatchers.IO) {
        journeyDao.updateStatus(journeyId, JourneyStatus.RECORDING)
        val cur = _activeJourney.value
        if (cur != null && cur.id == journeyId) {
            _activeJourney.value = cur.copy(status = JourneyStatus.RECORDING)
        }
        refreshJourneys()
    }

    suspend fun stopJourney(journeyId: String): Journey? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        journeyDao.updateStatus(journeyId, JourneyStatus.COMPLETED, endTime = now)
        val updated = journeyDao.getJourneyById(journeyId)
        _activeJourney.value = null
        _activePoints.value = emptyList()
        refreshJourneys()
        updated
    }

    suspend fun discardJourney(journeyId: String) = withContext(Dispatchers.IO) {
        journeyDao.deleteJourney(journeyId)
        if (_activeJourney.value?.id == journeyId) {
            _activeJourney.value = null
            _activePoints.value = emptyList()
        }
        refreshJourneys()
    }

    suspend fun checkInterruptedJourney(): Journey? = withContext(Dispatchers.IO) {
        journeyDao.getInterruptedJourney()
    }

    suspend fun recoverInterruptedJourney(journeyId: String): Pair<Journey?, List<LocationPoint>> = withContext(Dispatchers.IO) {
        val journey = journeyDao.getJourneyById(journeyId)
        val points = pointDao.getPointsForJourney(journeyId)
        if (journey != null) {
            _activeJourney.value = journey
            _activePoints.value = points
        }
        journey to points
    }

    suspend fun getJourneyDetails(journeyId: String): Pair<Journey?, List<LocationPoint>> = withContext(Dispatchers.IO) {
        val journey = journeyDao.getJourneyById(journeyId)
        val points = pointDao.getPointsForJourney(journeyId)
        journey to points
    }

    suspend fun renameJourney(journeyId: String, newTitle: String) = withContext(Dispatchers.IO) {
        journeyDao.renameJourney(journeyId, newTitle)
        refreshJourneys()
    }

    /**
     * Seeds a demo journey per PRD 5.9 ("Any demo route must be visibly marked 'Demo'").
     */
    suspend fun seedDemoJourney(): Journey = withContext(Dispatchers.IO) {
        val demoJourney = Journey(
            title = "Demo: Downtown to Waterfront Walk",
            startTime = System.currentTimeMillis() - 1800_000,
            endTime = System.currentTimeMillis() - 300_000,
            durationSeconds = 1500,
            recordedDistanceMeters = 1850.0,
            pointCount = 8,
            status = JourneyStatus.COMPLETED,
            isDemo = true
        )
        journeyDao.insertJourney(demoJourney)

        // Seed realistic sample coordinates along a walk
        val baseLat = 12.9716
        val baseLon = 77.5946
        val coords = listOf(
            0.0 to 0.0,
            0.0020 to 0.0015,
            0.0042 to 0.0031,
            0.0068 to 0.0045,
            0.0091 to 0.0070,
            0.0115 to 0.0094,
            0.0135 to 0.0120,
            0.0150 to 0.0145
        )
        val points = coords.mapIndexed { index, (dLat, dLon) ->
            LocationPoint(
                journeyId = demoJourney.id,
                latitude = baseLat + dLat,
                longitude = baseLon + dLon,
                altitude = 920.0 + (index * 2.5),
                speed = 1.35f,
                accuracy = 4.2f,
                timestamp = demoJourney.startTime + (index * 180_000),
                qualityStatus = PointQuality.ACCEPTED
            )
        }
        pointDao.insertBatch(points)
        refreshJourneys()
        demoJourney
    }
}
