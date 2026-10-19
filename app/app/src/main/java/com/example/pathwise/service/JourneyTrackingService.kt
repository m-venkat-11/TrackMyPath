package com.example.pathwise.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.pathwise.MainActivity
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.model.TrackingState
import com.example.pathwise.data.repository.JourneyRepository
import com.example.pathwise.data.repository.LocationUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Native Android foreground location service per PRD FR-02.
 * Maintains persistent notification with Stop/Pause actions, 1-second live ticker,
 * instant location acquisition, and realistic virtual mission simulator.
 */
class JourneyTrackingService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "com.example.pathwise.ACTION_START"
        const val ACTION_SIMULATE_DEMO = "com.example.pathwise.ACTION_SIMULATE_DEMO"
        const val ACTION_PAUSE = "com.example.pathwise.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.pathwise.ACTION_RESUME"
        const val ACTION_STOP = "com.example.pathwise.ACTION_STOP"

        const val CHANNEL_ID = "pathwise_tracking_channel"
        const val NOTIFICATION_ID = 1001

        private val _trackingState = MutableStateFlow(TrackingState.IDLE)
        val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

        private val _currentJourney = MutableStateFlow<Journey?>(null)
        val currentJourney: StateFlow<Journey?> = _currentJourney.asStateFlow()

        private val _currentLocation = MutableStateFlow<Location?>(null)
        val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

        fun isRunning(): Boolean = _trackingState.value == TrackingState.RECORDING ||
                _trackingState.value == TrackingState.PAUSED ||
                _trackingState.value == TrackingState.WAITING_FOR_GPS
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: JourneyRepository
    private var locationManager: LocationManager? = null
    private var activeJourneyId: String? = null
    private var tickerJob: Job? = null
    private var demoSimJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = JourneyRepository(applicationContext)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()

        // Immediate acquisition of cached location so map centers instantly
        fetchImmediateLastKnownLocation()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundTracking(isDemo = false)
            }
            ACTION_SIMULATE_DEMO -> {
                startForegroundTracking(isDemo = true)
            }
            ACTION_PAUSE -> {
                pauseTracking()
            }
            ACTION_RESUME -> {
                resumeTracking()
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_NOT_STICKY
    }

    private fun fetchImmediateLastKnownLocation(): Location? {
        val lm = locationManager ?: return null
        try {
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            if (last != null) {
                _currentLocation.value = last
                return last
            }
        } catch (_: SecurityException) { }
        return null
    }

    private fun startForegroundTracking(isDemo: Boolean) {
        val title = if (isDemo) "Demo HUD Mission Active" else "Recording Route HUD"
        val notification = buildNotification(title, "Acquiring precision GPS…")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            android.util.Log.e("PathWise", "startForeground handled: ${e.message}", e)
        }

        _trackingState.value = TrackingState.RECORDING

        serviceScope.launch {
            val titleName = if (isDemo) "Demo: Downtown to Waterfront Walk" else null
            val journey = repository.startNewJourney(titleName)
            activeJourneyId = journey.id
            _currentJourney.value = journey

            startDurationTicker()

            if (isDemo) {
                startDemoSimulation(journey.id)
            } else {
                LiveLocationManager.startWatching(applicationContext)
                val immediateLoc = LiveLocationManager.currentLocation.value ?: fetchImmediateLastKnownLocation()
                if (immediateLoc != null) {
                    _currentLocation.value = immediateLoc
                    processNewLocation(immediateLoc)
                }
                registerLocationUpdates()
            }
        }
    }

    private fun startDurationTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (_trackingState.value == TrackingState.RECORDING) {
                    val cur = _currentJourney.value
                    if (cur != null) {
                        val newDur = (System.currentTimeMillis() - cur.startTime) / 1000
                        val updated = cur.copy(durationSeconds = newDur)
                        _currentJourney.value = updated
                        val distText = LocationUtils.formatDistance(updated.recordedDistanceMeters)
                        val durText = LocationUtils.formatDuration(newDur)
                        updateNotification("Recording Route", "$distText • $durText (${updated.pointCount} pts)")
                    }
                }
            }
        }
    }

    private fun startDemoSimulation(journeyId: String) {
        demoSimJob?.cancel()
        demoSimJob = serviceScope.launch {
            val baseLat = _currentLocation.value?.latitude ?: 12.9716
            val baseLon = _currentLocation.value?.longitude ?: 77.5946

            // 15 realistic coordinates along an urban walking route
            val deltas = listOf(
                0.0 to 0.0,
                0.0008 to 0.0006,
                0.0017 to 0.0013,
                0.0026 to 0.0020,
                0.0034 to 0.0028,
                0.0044 to 0.0033,
                0.0055 to 0.0041,
                0.0067 to 0.0048,
                0.0078 to 0.0058,
                0.0090 to 0.0069,
                0.0102 to 0.0079,
                0.0113 to 0.0090,
                0.0125 to 0.0102,
                0.0138 to 0.0115,
                0.0150 to 0.0128
            )

            for ((dLat, dLon) in deltas) {
                if (!isActive || _trackingState.value != TrackingState.RECORDING) break

                val loc = Location("demo_gps").apply {
                    latitude = baseLat + dLat
                    longitude = baseLon + dLon
                    altitude = 920.0 + (dLat * 500)
                    speed = 1.38f + (kotlin.random.Random.nextFloat() * 0.2f) // ~5.0 km/h walking pace
                    accuracy = 3.5f
                    time = System.currentTimeMillis()
                }

                _currentLocation.value = loc
                processNewLocation(loc)
                delay(2000L) // emit every 2 seconds
            }
        }
    }

    private var liveLocJob: Job? = null

    private fun registerLocationUpdates() {
        liveLocJob?.cancel()
        liveLocJob = serviceScope.launch {
            LiveLocationManager.currentLocation.collect { loc ->
                if (loc != null) {
                    _currentLocation.value = loc
                    if (_trackingState.value == TrackingState.WAITING_FOR_GPS) {
                        _trackingState.value = TrackingState.RECORDING
                    }
                    if (_trackingState.value == TrackingState.RECORDING) {
                        processNewLocation(loc)
                    }
                }
            }
        }

        val lm = locationManager ?: return
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1200L,
                    1.0f,
                    this
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2.0f,
                    this
                )
            }
        } catch (e: SecurityException) {
            _trackingState.value = TrackingState.PERMISSION_NEEDED
            updateNotification("Permission Needed", "Location permission was revoked.")
        }
    }

    private fun pauseTracking() {
        val journeyId = activeJourneyId ?: return
        _trackingState.value = TrackingState.PAUSED
        locationManager?.removeUpdates(this)
        liveLocJob?.cancel()
        demoSimJob?.cancel()
        serviceScope.launch {
            repository.pauseJourney(journeyId)
            updateNotification("Tracking Paused", "Tap to resume or stop")
        }
    }

    private fun resumeTracking() {
        val journeyId = activeJourneyId ?: return
        _trackingState.value = TrackingState.RECORDING
        registerLocationUpdates()
        serviceScope.launch {
            repository.resumeJourney(journeyId)
            updateNotification("Recording Route", "Acquiring points…")
        }
    }

    private fun stopTracking() {
        val journeyId = activeJourneyId
        locationManager?.removeUpdates(this)
        liveLocJob?.cancel()
        demoSimJob?.cancel()
        tickerJob?.cancel()
        _trackingState.value = TrackingState.IDLE
        activeJourneyId = null

        serviceScope.launch {
            if (journeyId != null) {
                repository.stopJourney(journeyId)
            }
            _currentJourney.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun processNewLocation(location: Location) {
        val journeyId = activeJourneyId ?: return

        serviceScope.launch {
            repository.addPoint(
                journeyId = journeyId,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                accuracy = if (location.hasAccuracy()) location.accuracy else null
            )

            val journey = repository.activeJourney.value
            if (journey != null) {
                _currentJourney.value = journey
                val distanceText = LocationUtils.formatDistance(journey.recordedDistanceMeters)
                val durationText = LocationUtils.formatDuration(journey.durationSeconds)
                updateNotification("Recording Route", "$distanceText • $durationText (${journey.pointCount} points)")
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        _currentLocation.value = location
        if (_trackingState.value == TrackingState.WAITING_FOR_GPS) {
            _trackingState.value = TrackingState.RECORDING
        }
        if (_trackingState.value != TrackingState.RECORDING) return
        processNewLocation(location)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PathWise Journey Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live GPS journey recording stats and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JourneyTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
        tickerJob?.cancel()
        demoSimJob?.cancel()
        serviceScope.cancel()
        _trackingState.value = TrackingState.IDLE
        _currentJourney.value = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
