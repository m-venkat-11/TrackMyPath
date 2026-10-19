package com.example.pathwise.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pathwise.data.geocoding.AreaDetails
import com.example.pathwise.data.geocoding.GeocodedPlace
import com.example.pathwise.data.geocoding.GeocodingRepository
import com.example.pathwise.data.geocoding.NearbyPlace
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.model.Landmark
import com.example.pathwise.data.model.LocationPoint
import com.example.pathwise.data.model.TrackingState
import com.example.pathwise.data.repository.LocationUtils
import com.example.pathwise.service.JourneyTrackingService
import com.example.pathwise.theme.*
import com.example.pathwise.ui.components.GpsSatelliteSignal
import com.example.pathwise.ui.components.HoloDivider
import com.example.pathwise.ui.components.HoloPanel
import com.example.pathwise.ui.components.StatusChip
import com.example.pathwise.ui.dialogs.NewLandmarkDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class LaserMeasurementData(
    val distanceMeters: Double,
    val bearingDegrees: Int,
    val walkingMinutes: Int,
    val lat1: Double,
    val lon1: Double,
    val lat2: Double,
    val lon2: Double
)

/**
 * Holographic Map HUD screen per PRD Section 5.4 & 6.4.
 * Features instant real-time live location tracking, Google Maps HD deep zoom,
 * active area & street awareness, nearby landmarks discovery radar,
 * approaching landmark proximity cues, and 3D perspective tilt.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen(
    trackingState: TrackingState,
    currentJourney: Journey?,
    points: List<LocationPoint>,
    landmarks: List<Landmark> = emptyList(),
    currentLocation: android.location.Location? = null,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
    onSaveLandmark: (name: String, cat: String?, notes: String?, lat: Double, lon: Double) -> Unit = { _, _, _, _, _ -> },
    onNavigateBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Dialog states
    var showLandmarkDialog by remember { mutableStateOf(false) }
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var selectedPointCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var prefilledLandmarkName by remember { mutableStateOf("") }
    var prefilledLandmarkCategory by remember { mutableStateOf("Landmark") }

    // Map WebView instance
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var hasInitiallyCentered by remember { mutableStateOf(false) }

    // 3D Perspective Tilt & Radar Sweep States
    var is3dMode by remember { mutableStateOf(false) }
    var isRadarActive by remember { mutableStateOf(true) }

    // Laser Distance Measurement State
    var isLaserMeasureMode by remember { mutableStateOf(false) }
    var activeMeasurement by remember { mutableStateOf<LaserMeasurementData?>(null) }

    // Live Geocoded Area & Street Awareness
    var currentAreaDetails by remember { mutableStateOf<AreaDetails?>(null) }
    var nearbyPlaces by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
    var showNearbyExpanded by remember { mutableStateOf(false) }
    var isScanningNearby by remember { mutableStateOf(false) }

    // Approaching Landmark Banner state
    var approachingLandmarkText by remember { mutableStateOf<String?>(null) }

    // Search query & results state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }

    // Real-time reverse geocoding & nearby landmark radar when location updates
    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            val lat = currentLocation.latitude
            val lon = currentLocation.longitude

            // 1. Fetch Area Details (Street & Suburb)
            val details = GeocodingRepository.getAreaDetails(lat, lon)
            if (details != null) {
                currentAreaDetails = details
            }

            // 2. Fetch prominent nearby landmarks for spatial mental map
            if (nearbyPlaces.isEmpty()) {
                isScanningNearby = true
                val discovered = GeocodingRepository.fetchNearbyLandmarks(lat, lon)
                nearbyPlaces = discovered
                isScanningNearby = false
            }

            // 3. Proximity check for approaching landmarks within 150m
            val closeByUser = landmarks.firstOrNull { lm ->
                GeocodingRepository.calculateDistanceMeters(lat, lon, lm.latitude, lm.longitude) < 150.0
            }
            if (closeByUser != null) {
                val d = GeocodingRepository.calculateDistanceMeters(lat, lon, closeByUser.latitude, closeByUser.longitude).toInt()
                approachingLandmarkText = "Approaching ${closeByUser.name} (~${d}m away)"
            } else {
                val closeNearby = nearbyPlaces.firstOrNull { it.distanceMeters in 1..150 }
                if (closeNearby != null) {
                    approachingLandmarkText = "Near ${closeNearby.name} (~${closeNearby.distanceMeters}m)"
                } else {
                    approachingLandmarkText = null
                }
            }
        }
    }

    // Smoothly fly to user location on first GPS fix, then update marker position
    LaunchedEffect(currentLocation, isMapReady) {
        if (isMapReady && currentLocation != null) {
            val autoPan = !hasInitiallyCentered
            val js = "updateUserLocation(${currentLocation.latitude}, ${currentLocation.longitude}, ${currentLocation.accuracy}, $autoPan);"
            webViewInstance?.evaluateJavascript(js, null)

            if (!hasInitiallyCentered) {
                hasInitiallyCentered = true
                webViewInstance?.evaluateJavascript("recenterTo(${currentLocation.latitude}, ${currentLocation.longitude}, 17);", null)
            }
        }
    }

    // Sync route polyline to Leaflet map
    LaunchedEffect(points, isMapReady) {
        if (isMapReady) {
            val jsonCoords = points.filter { it.qualityStatus == com.example.pathwise.data.model.PointQuality.ACCEPTED }
                .map { "[${it.latitude},${it.longitude}]" }
                .joinToString(",", "[", "]")
            webViewInstance?.evaluateJavascript("setRoutePoints('$jsonCoords');", null)
        }
    }

    // Sync landmarks to Leaflet map
    LaunchedEffect(landmarks, isMapReady) {
        if (isMapReady) {
            val array = JSONArray()
            landmarks.forEach { lm ->
                val obj = JSONObject().apply {
                    put("name", lm.name)
                    put("category", lm.category ?: "")
                    put("notes", lm.notes ?: "")
                    put("latitude", lm.latitude)
                    put("longitude", lm.longitude)
                }
                array.put(obj)
            }
            val escaped = array.toString().replace("'", "\\'")
            webViewInstance?.evaluateJavascript("setLandmarks('$escaped');", null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
    ) {
        // ── Interactive Leaflet Google Maps HD with 3D Stage ─────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.blockNetworkLoads = false
                    settings.blockNetworkImage = false
                    setBackgroundColor(android.graphics.Color.parseColor("#050D1A"))

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onMapReady() {
                            isMapReady = true
                            post {
                                evaluateJavascript("toggleRadarSweep(true);", null)
                                val loc = currentLocation ?: points.firstOrNull()?.let {
                                    val l = android.location.Location("point")
                                    l.latitude = it.latitude
                                    l.longitude = it.longitude
                                    l
                                }
                                if (loc != null) {
                                    hasInitiallyCentered = true
                                    evaluateJavascript("recenterTo(${loc.latitude}, ${loc.longitude}, 17);", null)
                                    evaluateJavascript("updateUserLocation(${loc.latitude}, ${loc.longitude}, ${loc.accuracy}, false);", null)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onMapClicked(lat: Double, lon: Double) {
                            coroutineScope.launch {
                                selectedPointCoords = lat to lon
                                val resolved = GeocodingRepository.reverseGeocode(lat, lon)
                                prefilledLandmarkName = resolved ?: ""
                                prefilledLandmarkCategory = "Landmark"
                                showLandmarkDialog = true
                            }
                        }

                        @JavascriptInterface
                        fun onMeasurementCalculated(dist: Double, bearing: Int, walkMins: Int, lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
                            coroutineScope.launch {
                                activeMeasurement = LaserMeasurementData(dist, bearing, walkMins, lat1, lon1, lat2, lon2)
                            }
                        }
                    }, "AndroidBridge")

                    loadUrl("file:///android_asset/map.html")
                    webViewInstance = this
                }
            }
        )

        // ── Top HUD Bar & Controls ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ColorSurface.copy(alpha = 0.92f))
                        .border(1.dp, ColorBorderCyan, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ColorTextPrimary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        text = if (currentLocation == null) "LOCKING GNSS" else trackingState.displayLabel,
                        isLive = trackingState == TrackingState.RECORDING || currentLocation != null,
                        color = when {
                            currentLocation == null -> ColorWarning
                            trackingState == TrackingState.RECORDING -> ColorCyan
                            trackingState == TrackingState.PAUSED -> ColorWarning
                            else -> ColorTeal
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    GpsSatelliteSignal(accuracy = currentLocation?.accuracy)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 3D Perspective Tilt Toggle
                    IconButton(
                        onClick = {
                            is3dMode = !is3dMode
                            webViewInstance?.evaluateJavascript("toggle3DMode($is3dMode);", null)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (is3dMode) ColorCyan.copy(alpha = 0.25f) else ColorSurface.copy(alpha = 0.92f))
                            .border(1.dp, if (is3dMode) ColorCyan else ColorBorderCyan, CircleShape)
                    ) {
                        Text(if (is3dMode) "3D" else "2D", fontWeight = FontWeight.Bold, color = if (is3dMode) ColorCyan else ColorTextSecondary, fontSize = 11.sp)
                    }

                    // Search Toggle
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ColorSurface.copy(alpha = 0.92f))
                            .border(1.dp, if (showSearchBar) ColorCyan else ColorBorderCyan, CircleShape)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = if (showSearchBar) ColorCyan else ColorTextPrimary)
                    }

                    // Recenter on My Real Location FAB
                    IconButton(
                        onClick = {
                            if (currentLocation != null) {
                                webViewInstance?.evaluateJavascript("recenterTo(${currentLocation.latitude}, ${currentLocation.longitude}, 17);", null)
                                webViewInstance?.evaluateJavascript("updateUserLocation(${currentLocation.latitude}, ${currentLocation.longitude}, ${currentLocation.accuracy}, true);", null)
                            } else {
                                Toast.makeText(context, "Acquiring live GPS satellite lock...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ColorSurface.copy(alpha = 0.92f))
                            .border(1.dp, ColorCyan, CircleShape)
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "Recenter", tint = ColorCyan)
                    }
                }
            }

            // Search Bar Dropdown
            AnimatedVisibility(visible = showSearchBar, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp, glowColor = ColorCyan, withBrackets = true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = ColorCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            TextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    searchQuery = query
                                    searchJob?.cancel()
                                    if (query.length >= 3) {
                                        isSearching = true
                                        searchJob = coroutineScope.launch {
                                            delay(350)
                                            searchResults = GeocodingRepository.searchPlaces(query)
                                            isSearching = false
                                        }
                                    } else {
                                        searchResults = emptyList()
                                        isSearching = false
                                    }
                                },
                                placeholder = { Text("Search streets, places, landmarks…", fontSize = 13.sp, color = ColorTextSecondary) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = ColorTextPrimary,
                                    unfocusedTextColor = ColorTextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = ColorTextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        HoloPanel(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp), cornerRadius = 14.dp) {
                            LazyColumn {
                                items(searchResults) { result ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                focusManager.clearFocus()
                                                showSearchBar = false
                                                webViewInstance?.evaluateJavascript(
                                                    "recenterTo(${result.latitude}, ${result.longitude}, 17);",
                                                    null
                                                )
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                    ) {
                                        Text(result.shortName, fontWeight = FontWeight.Bold, color = ColorCyan, fontSize = 13.sp)
                                        Text(result.displayName, color = ColorTextSecondary, fontSize = 11.sp, maxLines = 1)
                                    }
                                    HoloDivider()
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Live Area, Street & Direction Awareness Banner ────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorSurface.copy(alpha = 0.94f))
                    .border(1.dp, BrushCyanGlow, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, tint = ColorCyan, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = currentAreaDetails?.formattedHeadline
                                    ?: if (currentLocation == null) "Acquiring live GPS fix..." else "Position Acquired",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = ColorCyan,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = currentAreaDetails?.formattedSubtitle
                                ?: if (currentLocation != null) "GPS Locked • Precision ±${currentLocation.accuracy.toInt()}m" else "Stand by for satellite signal",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary,
                            maxLines = 1
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val speedStr = LocationUtils.formatSpeed(currentLocation?.speed)
                        Text(
                            text = speedStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ColorTeal
                        )
                        Text(
                            text = if (currentLocation != null && currentLocation.hasBearing()) "${currentLocation.bearing.toInt()}°" else "ACTIVE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ColorTextSecondary
                        )
                    }
                }
            }

            // Approaching Landmark Proximity Alert
            if (approachingLandmarkText != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorCyan.copy(alpha = 0.18f))
                        .border(1.dp, ColorCyan, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = ColorCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = approachingLandmarkText!!,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorCyan
                        )
                    }
                }
            }
        }

        // ── Right Side Floating Action Tools ─────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Laser Measure Mode Toggle
            IconButton(
                onClick = {
                    isLaserMeasureMode = !isLaserMeasureMode
                    activeMeasurement = null
                    webViewInstance?.evaluateJavascript("setMeasureMode($isLaserMeasureMode);", null)
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isLaserMeasureMode) ColorWarning.copy(alpha = 0.25f) else ColorSurface.copy(alpha = 0.90f))
                    .border(1.dp, if (isLaserMeasureMode) ColorWarning else ColorBorderCyan, CircleShape)
            ) {
                Text("📏", fontSize = 16.sp)
            }

            // Radar Scanline Toggle
            IconButton(
                onClick = {
                    isRadarActive = !isRadarActive
                    webViewInstance?.evaluateJavascript("toggleRadarSweep($isRadarActive);", null)
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isRadarActive) ColorCyan.copy(alpha = 0.2f) else ColorSurface.copy(alpha = 0.90f))
                    .border(1.dp, if (isRadarActive) ColorCyan else ColorBorderCyan, CircleShape)
            ) {
                Text("📡", fontSize = 16.sp)
            }

            // Zoom In
            IconButton(
                onClick = { webViewInstance?.evaluateJavascript("zoomIn();", null) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ColorSurface.copy(alpha = 0.90f))
                    .border(1.dp, ColorBorderCyan, CircleShape)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = ColorCyan)
            }

            // Zoom Out
            IconButton(
                onClick = { webViewInstance?.evaluateJavascript("zoomOut();", null) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ColorSurface.copy(alpha = 0.90f))
                    .border(1.dp, ColorBorderCyan, CircleShape)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", tint = ColorCyan)
            }
        }

        // ── Bottom Panel: Spatial Learning Radar & Tracking Controls ──────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Spatial Learning: Nearby Landmarks Radar Ticker ──────────────
            if (nearbyPlaces.isNotEmpty()) {
                HoloPanel(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    glowColor = ColorTeal,
                    withBrackets = false
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showNearbyExpanded = !showNearbyExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📍", fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "NEARBY LANDMARKS (${nearbyPlaces.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTeal,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                if (showNearbyExpanded) "Collapse ▲" else "View All ▼",
                                fontSize = 11.sp,
                                color = ColorCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!showNearbyExpanded) {
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(nearbyPlaces.take(4)) { place ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ColorSurfaceVariant.copy(alpha = 0.8f))
                                            .border(1.dp, ColorBorderCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(place.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ColorTextPrimary, maxLines = 1)
                                            Spacer(Modifier.width(6.dp))
                                            Text("${place.distanceMeters}m ${place.cardinalDirection}", fontSize = 10.sp, color = ColorCyan, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                nearbyPlaces.take(6).forEach { place ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(place.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorTextPrimary, maxLines = 1)
                                            Text("${place.category} • ${place.distanceMeters}m away (${place.cardinalDirection})", fontSize = 10.sp, color = ColorTextSecondary)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onSaveLandmark(place.name, place.category, "Discovered during live route", place.latitude, place.longitude)
                                                Toast.makeText(context, "Pinned ${place.name} to Mental Map!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, ColorCyan)
                                        ) {
                                            Text("+ Pin", fontSize = 11.sp, color = ColorCyan)
                                        }
                                    }
                                    HoloDivider()
                                }
                            }
                        }
                    }
                }
            }

            // Laser Measurement Active Card
            if (isLaserMeasureMode) {
                HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp, glowColor = ColorWarning, withBrackets = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("LASER DISTANCE TOOL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorWarning, letterSpacing = 1.sp)
                            if (activeMeasurement != null) {
                                val m = activeMeasurement!!
                                Text(
                                    "${LocationUtils.formatDistance(m.distanceMeters)}  •  ${m.bearingDegrees}°  •  ~${m.walkingMinutes} min walk",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text("Tap two points on the map to measure", fontSize = 12.sp, color = ColorTextSecondary)
                            }
                        }

                        IconButton(onClick = {
                            isLaserMeasureMode = false
                            activeMeasurement = null
                            webViewInstance?.evaluateJavascript("setMeasureMode(false);", null)
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = ColorWarning, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Live Telemetry Matrix
            HoloPanel(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                glowColor = ColorCyan,
                withBrackets = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DISTANCE", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
                        Text(
                            LocationUtils.formatDistance(currentJourney?.recordedDistanceMeters ?: 0.0),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = ColorCyan
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DURATION", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
                        Text(
                            LocationUtils.formatDuration(currentJourney?.durationSeconds ?: 0L),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = ColorCyan
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SPEED", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
                        Text(
                            LocationUtils.formatSpeed(currentLocation?.speed),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = ColorCyan
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LANDMARKS", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
                        Text(
                            "${landmarks.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = ColorCyan
                        )
                    }
                }
            }

            // Tracking Control Buttons (Pause, Resume, Stop, Pin Landmark)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pin Landmark FAB
                Button(
                    onClick = {
                        val lat = currentLocation?.latitude ?: points.lastOrNull()?.latitude ?: 0.0
                        val lon = currentLocation?.longitude ?: points.lastOrNull()?.longitude ?: 0.0
                        selectedPointCoords = lat to lon
                        prefilledLandmarkName = currentAreaDetails?.street ?: ""
                        prefilledLandmarkCategory = "Landmark"
                        showLandmarkDialog = true
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderCyan)
                ) {
                    Icon(Icons.Filled.AddLocation, contentDescription = null, tint = ColorCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pin Landmark", color = ColorCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (trackingState == TrackingState.RECORDING) {
                    Button(
                        onClick = onPause,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorWarning.copy(alpha = 0.2f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorWarning)
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, tint = ColorWarning, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause", color = ColorWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showStopConfirmDialog = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorError.copy(alpha = 0.25f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorError)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, tint = ColorError, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop", color = ColorError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (trackingState == TrackingState.PAUSED) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorCyan.copy(alpha = 0.25f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorCyan)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ColorCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Resume", color = ColorCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showStopConfirmDialog = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorError.copy(alpha = 0.25f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorError)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, tint = ColorError, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save & Exit", color = ColorError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // IDLE: Explore Mode CTA
                    Button(
                        onClick = {
                            val intent = Intent(context, JourneyTrackingService::class.java).apply {
                                action = JourneyTrackingService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        },
                        modifier = Modifier.weight(2f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorCyan)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ColorTextOnCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Recording Route", color = ColorTextOnCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // New Landmark Dialog
        if (showLandmarkDialog && selectedPointCoords != null) {
            val (cLat, cLon) = selectedPointCoords!!
            NewLandmarkDialog(
                latitude = cLat,
                longitude = cLon,
                onDismiss = { showLandmarkDialog = false },
                onSave = { name, cat, notes ->
                    showLandmarkDialog = false
                    onSaveLandmark(name, cat, notes, cLat, cLon)
                }
            )
        }

        // Stop Confirmation Dialog
        if (showStopConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showStopConfirmDialog = false },
                title = { Text("Complete Journey HUD?", color = ColorTextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Your route will be saved to your Journey Library and available for City Recall memory challenges.", color = ColorTextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            showStopConfirmDialog = false
                            onStop()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorCyan)
                    ) {
                        Text("Save & Finish", color = ColorTextOnCyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStopConfirmDialog = false }) {
                        Text("Cancel", color = ColorTextSecondary)
                    }
                },
                containerColor = ColorSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
