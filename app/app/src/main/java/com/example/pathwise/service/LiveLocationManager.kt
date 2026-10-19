package com.example.pathwise.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-precision dual-engine location provider.
 * Combines Google Play Services FusedLocationProviderClient with native LocationManager fallback
 * to guarantee instant, high-accuracy GPS fixes indoors and outdoors.
 */
object LiveLocationManager {

    private const val TAG = "LiveLocationManager"

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private val _isGpsActive = MutableStateFlow(false)
    val isGpsActive: StateFlow<Boolean> = _isGpsActive.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var nativeListener: LocationListener? = null
    private var isWatching = false

    @SuppressLint("MissingPermission")
    fun startWatching(context: Context) {
        if (isWatching) return
        val appContext = context.applicationContext
        isWatching = true

        // 1. Try immediate last known location from cache
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
            fusedClient?.lastLocation?.addOnSuccessListener { loc ->
                if (loc != null && _currentLocation.value == null) {
                    Log.d(TAG, "Cached fused location acquired: ${loc.latitude}, ${loc.longitude}")
                    _currentLocation.value = loc
                    _isGpsActive.value = true
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Fused lastLocation unavailable: ${e.message}")
        }

        // 2. Fallback check on native LocationManager last known
        locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        try {
            val lm = locationManager
            if (lm != null && _currentLocation.value == null) {
                val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val best = lastGps ?: lastNet
                if (best != null) {
                    Log.d(TAG, "Native cached location acquired: ${best.latitude}, ${best.longitude}")
                    _currentLocation.value = best
                    _isGpsActive.value = true
                }
            }
        } catch (_: SecurityException) { }

        // 3. Register Google Play Services Fused Location Updates (High Accuracy 1s interval)
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0.5f)
                .setWaitForAccurateLocation(false)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    _currentLocation.value = loc
                    _isGpsActive.value = true
                    Log.d(TAG, "Fused fix: ${loc.latitude}, ${loc.longitude} (±${loc.accuracy}m)")
                }

                override fun onLocationAvailability(avail: LocationAvailability) {
                    _isGpsActive.value = avail.isLocationAvailable
                }
            }

            fusedClient?.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Fused location error: ${e.message}")
        }

        // 4. Always register native GPS and Network providers in parallel for immediate indoor/outdoor fix
        registerNativeFallback(appContext)
    }

    @SuppressLint("MissingPermission")
    private fun registerNativeFallback(context: Context) {
        val lm = locationManager ?: return
        nativeListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                _currentLocation.value = location
                _isGpsActive.value = true
            }
            override fun onProviderEnabled(provider: String) { _isGpsActive.value = true }
            override fun onProviderDisabled(provider: String) {}
        }

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, nativeListener!!)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500L, 2.0f, nativeListener!!)
            }
        } catch (_: SecurityException) {}
    }

    fun stopWatching() {
        try {
            locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
            nativeListener?.let { locationManager?.removeUpdates(it) }
        } catch (_: Throwable) {}
        isWatching = false
    }

    fun updateManualLocation(location: Location) {
        _currentLocation.value = location
        _isGpsActive.value = true
    }
}
