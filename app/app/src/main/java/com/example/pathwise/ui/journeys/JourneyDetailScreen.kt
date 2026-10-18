package com.example.pathwise.ui.journeys

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.model.LocationPoint
import com.example.pathwise.data.repository.LocationUtils
import com.example.pathwise.theme.*
import com.example.pathwise.ui.components.HoloDivider
import com.example.pathwise.ui.components.HoloPanel
import kotlin.math.cos

/**
 * Journey detail and replay screen with interactive timeline slider per PRD Section 6.6.
 */
@Composable
fun JourneyDetailScreen(
    journey: Journey,
    points: List<LocationPoint>,
    onNavigateBack: () -> Unit,
) {
    var replayProgress by remember { mutableFloatStateOf(1.0f) }
    var isPlaying by remember { mutableStateOf(false) }

    // Replay timer loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(100)
            replayProgress += 0.02f
            if (replayProgress >= 1.0f) {
                replayProgress = 1.0f
                isPlaying = false
            }
        }
    }

    val visiblePointCount = (points.size * replayProgress).toInt().coerceIn(0, points.size)
    val currentPoints = points.take(visiblePointCount)

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ColorSurfaceVariant)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = ColorTextPrimary)
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
                    text = "Actual GPS Trace • ${points.size} recorded points",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorCyan
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Trajectory Preview Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(ColorSurface)
                .border(1.dp, ColorBorderCyan, RoundedCornerShape(16.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (points.size < 2) return@Canvas

                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }

                val dLat = (maxLat - minLat).coerceAtLeast(0.0001)
                val dLon = (maxLon - minLon).coerceAtLeast(0.0001)

                val pad = 40f
                val w = size.width - pad * 2
                val h = size.height - pad * 2

                fun project(lat: Double, lon: Double): Offset {
                    val x = pad + ((lon - minLon) / dLon * w).toFloat()
                    val y = pad + ((maxLat - lat) / dLat * h).toFloat()
                    return Offset(x, y)
                }

                // Full route faint background line
                val fullPath = Path()
                fullPath.moveTo(project(points[0].latitude, points[0].longitude).x, project(points[0].latitude, points[0].longitude).y)
                for (p in points) {
                    val off = project(p.latitude, p.longitude)
                    fullPath.lineTo(off.x, off.y)
                }
                drawPath(fullPath, color = ColorSurfaceVariant, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))

                // Replay active path
                if (currentPoints.size >= 2) {
                    val activePath = Path()
                    val first = project(currentPoints[0].latitude, currentPoints[0].longitude)
                    activePath.moveTo(first.x, first.y)
                    for (p in currentPoints) {
                        val off = project(p.latitude, p.longitude)
                        activePath.lineTo(off.x, off.y)
                    }

                    // Glow
                    drawPath(activePath, color = ColorCyan.copy(alpha = 0.25f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    // Core
                    drawPath(activePath, color = ColorCyan, style = Stroke(3.5f.dp.toPx(), cap = StrokeCap.Round))

                    // Replay Current Pin
                    val head = currentPoints.last()
                    val headOff = project(head.latitude, head.longitude)
                    drawCircle(ColorCyan, radius = 7.dp.toPx(), center = headOff)
                    drawCircle(ColorTextPrimary, radius = 3.dp.toPx(), center = headOff)
                }

                // Start / Finish Pins
                val startOff = project(points.first().latitude, points.first().longitude)
                drawCircle(ColorGreen, radius = 5.dp.toPx(), center = startOff)

                val endOff = project(points.last().latitude, points.last().longitude)
                drawCircle(ColorError, radius = 5.dp.toPx(), center = endOff)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Replay Timeline Slider
        HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Route Replay",
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
                            imageVector = Icons.Filled.PlayArrow,
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

        Spacer(Modifier.height(12.dp))

        // Metrics Summary Card
        HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOTAL DISTANCE", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
                    Text(
                        LocationUtils.formatDistance(journey.recordedDistanceMeters),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = ColorCyan
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOTAL DURATION", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
                    Text(
                        LocationUtils.formatDuration(journey.durationSeconds),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = ColorCyan
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GPS POINTS", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
                    Text(
                        "${points.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = ColorCyan
                    )
                }
            }
        }
    }
}
