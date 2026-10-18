package com.example.pathwise.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.pathwise.data.geocoding.GeocodedPlace
import com.example.pathwise.data.geocoding.GeocodingRepository
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
 * Features 3D perspective tilt, live 1s duration telemetry, real-time Nominatim reverse-geocoding,
 * interactive laser distance measurement, street search, and instant demo mission simulation.
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

    // Map WebView instance
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }

    // 3D Perspective Tilt & Radar Sweep States
    var is3dMode by remember { mutableStateOf(false) }
    var isRadarActive by remember { mutableStateOf(true) }

    // Laser Distance Measurement State
    var isLaserMeasureMode by remember { mutableStateOf(false) }
    var activeMeasurement by remember { mutableStateOf<LaserMeasurementData?>(null) }

    // Live Reverse Geocoded Street Name
    var currentStreetName by remember { mutableStateOf<String?>(null) }

    // Search query & results state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }

    // Reverse geocode whenever user location updates
    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            val name = GeocodingRepository.reverseGeocode(currentLocation.latitude, currentLocation.longitude)
            if (name != null) {
                currentStreetName = name
            }
        }
    }

    // Sync user location to Leaflet map
    LaunchedEffect(currentLocation, isMapReady) {
        if (isMapReady && currentLocation != null) {
            val js = "updateUserLocation(${currentLocation.latitude}, ${currentLocation.longitude}, ${currentLocation.accuracy}, false);"
            webViewInstance?.evaluateJavascript(js, null)
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

    fun startDemoMission() {
        val intent = Intent(context, JourneyTrackingService::class.java).apply {
            action = JourneyTrackingService.ACTION_SIMULATE_DEMO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
    ) {
        // ── Interactive Leaflet Carto Dark Matter Map with 3D Canvas ─────────
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
                                val lat = currentLocation?.latitude ?: points.firstOrNull()?.latitude ?: 12.9716
                                val lon = currentLocation?.longitude ?: points.firstOrNull()?.longitude ?: 77.5946
                                evaluateJavascript("recenterTo($lat, $lon, 15);", null)
                                evaluateJavascript("toggleRadarSweep(true);", null)
                            }
                        }

                        @JavascriptInterface
                        fun onMapClicked(lat: Double, lon: Double) {
                            coroutineScope.launch {
                                selectedPointCoords = lat to lon
                                val resolved = GeocodingRepository.reverseGeocode(lat, lon)
                                prefilledLandmarkName = resolved ?: ""
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                        text = trackingState.displayLabel,
                        isLive = trackingState == TrackingState.RECORDING,
                        color = when (trackingState) {
                            TrackingState.RECORDING -> ColorCyan
                            TrackingState.PAUSED -> ColorWarning
                            TrackingState.WAITING_FOR_GPS -> ColorTeal
                            else -> ColorTextSecondary
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    GpsSatelliteSignal(accuracy = currentLocation?.accuracy)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 3D Tactical Tilt Toggle
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

                    // Recenter on GPS
                    IconButton(
                        onClick = {
                            val lat = currentLocation?.latitude ?: points.lastOrNull()?.latitude ?: 12.9716
                            val lon = currentLocation?.longitude ?: points.lastOrNull()?.longitude ?: 77.5946
                            webViewInstance?.evaluateJavascript("recenterTo($lat, $lon, 16);", null)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ColorSurface.copy(alpha = 0.92f))
                            .border(1.dp, ColorBorderCyan, CircleShape)
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = "Recenter", tint = ColorCyan)
                    }
                }
            }

            // Search Bar Dropdown
            AnimatedVisibility(visible = showSearchBar, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
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
                                placeholder = { Text("Search streets, places, areas…", fontSize = 13.sp, color = ColorTextSecondary) },
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
                                                    "recenterTo(${result.latitude}, ${result.longitude}, 16);",
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

            Spacer(Modifier.height(8.dp))

            // Live Street Name Badge
            if (currentStreetName != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ColorSurface.copy(alpha = 0.90f))
                        .border(1.dp, ColorTeal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = ColorTeal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = currentStreetName ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ColorTextPrimary,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "LIVE HUD",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorCyan,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Laser Measurement Active Card (PRD Section 6.10)
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
                Spacer(Modifier.height(8.dp))
            }

            // Telemetry Floating Panel with Chamfered Brackets
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
                        Text("POINTS", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
                        Text(
                            "${points.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                            color = ColorCyan
                        )
                    }
                }
            }
        }

        // ── Floating Action Tools (Zoom + Laser Measure + Radar) ─────────────
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

        // ── Bottom Control Dock ───────────────────────────────────────────────
        HoloPanel(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            cornerRadius = 22.dp,
            glowColor = ColorCyan,
            withBrackets = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // If not currently recording, show Demo Simulation quick-launch
                if (trackingState == TrackingState.IDLE) {
                    OutlinedButton(
                        onClick = { startDemoMission() },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorCyan),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("⚡ SIMULATE DEMO WALK MISSION", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Landmark CTA
                    OutlinedButton(
                        onClick = {
                            val lat = currentLocation?.latitude ?: points.lastOrNull()?.latitude ?: 12.9716
                            val lon = currentLocation?.longitude ?: points.lastOrNull()?.longitude ?: 77.5946
                            selectedPointCoords = lat to lon
                            prefilledLandmarkName = currentStreetName ?: ""
                            showLandmarkDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorViolet.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorViolet),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Landmark", fontSize = 12.sp)
                    }

                    // Recording Controls: Pause / Resume / Start
                    if (trackingState == TrackingState.RECORDING) {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(ColorWarning.copy(alpha = 0.2f))
                                .border(1.5.dp, ColorWarning, CircleShape)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Pause", tint = ColorWarning, modifier = Modifier.size(26.dp))
                        }
                    } else if (trackingState == TrackingState.PAUSED) {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(ColorCyan.copy(alpha = 0.2f))
                                .border(1.5.dp, ColorCyan, CircleShape)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume", tint = ColorCyan, modifier = Modifier.size(26.dp))
                        }
                    }

                    // Stop Journey Button
                    if (trackingState != TrackingState.IDLE) {
                        Button(
                            onClick = { showStopConfirmDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ColorError,
                                contentColor = ColorTextPrimary
                            ),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Landmark Creation Dialog
    if (showLandmarkDialog) {
        val (lat, lon) = selectedPointCoords ?: (
            (currentLocation?.latitude ?: points.lastOrNull()?.latitude ?: 12.9716) to
            (currentLocation?.longitude ?: points.lastOrNull()?.longitude ?: 77.5946)
        )
        NewLandmarkDialog(
            latitude = lat,
            longitude = lon,
            onDismiss = { showLandmarkDialog = false },
            onSave = { name, cat, notes ->
                onSaveLandmark(name, cat, notes, lat, lon)
                showLandmarkDialog = false
            }
        )
    }

    // Stop Confirmation Dialog
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = { Text("End Journey Recording?", color = ColorTextPrimary) },
            text = { Text("All recorded points will be finalized and saved to your local journey library.", color = ColorTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmDialog = false
                        onStop()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorCyan, contentColor = ColorTextOnCyan)
                ) {
                    Text("Save & End")
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
