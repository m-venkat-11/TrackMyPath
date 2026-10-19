package com.example.pathwise.ui.journeys

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pathwise.data.geocoding.GeocodingRepository
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.model.LocationPoint
import com.example.pathwise.data.repository.LocationUtils
import com.example.pathwise.theme.*
import com.example.pathwise.ui.components.HoloDivider
import com.example.pathwise.ui.components.HoloPanel

/**
 * Interactive Journey Replay & City Learning Screen.
 * Renders the recorded GPS route over Google Maps HD tiles, provides an interactive
 * time-scrubber with dynamic street and area identification, and connects directly
 * to the City Recall Arena for spatial memory practice.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JourneyDetailScreen(
    journey: Journey,
    points: List<LocationPoint>,
    onNavigateBack: () -> Unit,
    onOpenRecall: () -> Unit = {}
) {
    var replayProgress by remember { mutableFloatStateOf(1.0f) }
    var isPlaying by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }

    var currentWaypointStreet by remember { mutableStateOf<String?>(null) }

    // Replay timer loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(120)
            replayProgress += 0.025f
            if (replayProgress >= 1.0f) {
                replayProgress = 1.0f
                isPlaying = false
            }
        }
    }

    val visiblePointCount = (points.size * replayProgress).toInt().coerceIn(0, points.size)
    val activePoint = if (visiblePointCount > 0 && visiblePointCount <= points.size) {
        points[visiblePointCount - 1]
    } else points.lastOrNull()

    // Reverse geocode the active point on scrubber movement
    LaunchedEffect(activePoint) {
        if (activePoint != null) {
            val name = GeocodingRepository.reverseGeocode(activePoint.latitude, activePoint.longitude)
            currentWaypointStreet = name ?: "Traversing Path"

            if (isMapReady) {
                webViewInstance?.evaluateJavascript("updateUserLocation(${activePoint.latitude}, ${activePoint.longitude}, 4, false);", null)
                webViewInstance?.evaluateJavascript("recenterTo(${activePoint.latitude}, ${activePoint.longitude}, 16);", null)
            }
        }
    }

    // Load full route polyline into WebView on map ready
    LaunchedEffect(isMapReady, points) {
        if (isMapReady && points.size >= 2) {
            val jsonCoords = points.filter { it.qualityStatus == com.example.pathwise.data.model.PointQuality.ACCEPTED }
                .map { "[${it.latitude},${it.longitude}]" }
                .joinToString(",", "[", "]")
            webViewInstance?.evaluateJavascript("setRoutePoints('$jsonCoords');", null)

            val mid = points[points.size / 2]
            webViewInstance?.evaluateJavascript("recenterTo(${mid.latitude}, ${mid.longitude}, 15);", null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ColorSurfaceVariant)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ColorTextPrimary)
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = journey.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ColorTextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "Recorded Route • ${points.size} GPS fixes",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorCyan
                    )
                }
            }

            // Practice Recall Button
            IconButton(
                onClick = onOpenRecall,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ColorCyan.copy(alpha = 0.2f))
                    .border(1.dp, ColorCyan, CircleShape)
            ) {
                Icon(Icons.Filled.Psychology, contentDescription = "Recall Quiz", tint = ColorCyan, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Interactive Google Maps HD Route View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(ColorSurface)
                .border(1.dp, ColorBorderCyan, RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.allowFileAccessFromFileURLs = true
                        settings.allowUniversalAccessFromFileURLs = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setBackgroundColor(android.graphics.Color.parseColor("#050D1A"))

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onMapReady() {
                                isMapReady = true
                            }
                        }, "AndroidBridge")

                        loadUrl("file:///android_asset/map.html")
                        webViewInstance = this
                    }
                }
            )

            // Dynamic Street & Waypoint Overlay
            if (currentWaypointStreet != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ColorSurface.copy(alpha = 0.92f))
                        .border(1.dp, BrushCyanGlow, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = currentWaypointStreet!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorCyan,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Replay Timeline Scrubber
        HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Route Walkthrough Replay",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ColorTextPrimary
                    )

                    IconButton(
                        onClick = {
                            if (replayProgress >= 1f) replayProgress = 0f
                            isPlaying = !isPlaying
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ColorCyan)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = ColorTextOnCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Slider(
                    value = replayProgress,
                    onValueChange = {
                        replayProgress = it
                        isPlaying = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = ColorCyan,
                        activeTrackColor = ColorCyan,
                        inactiveTrackColor = ColorSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        LocationUtils.formatDistance(journey.recordedDistanceMeters * replayProgress),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorCyan
                    )
                    Text(
                        LocationUtils.formatDuration((journey.durationSeconds * replayProgress).toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Practice Mental Recall Action Button
        Button(
            onClick = onOpenRecall,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ColorCyan)
        ) {
            Icon(Icons.Filled.Psychology, contentDescription = null, tint = ColorTextOnCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("🧠 Test Memory of This Route (City Recall)", color = ColorTextOnCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
