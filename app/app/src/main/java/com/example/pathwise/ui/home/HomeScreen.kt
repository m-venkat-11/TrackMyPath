package com.example.pathwise.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.repository.LocationUtils
import com.example.pathwise.theme.*
import com.example.pathwise.ui.components.*
import kotlin.math.sin

/**
 * PathWise Home Dashboard — Holographic HUD launch screen.
 */
@Composable
fun HomeScreen(
    journeysCount: Int = 0,
    landmarksCount: Int = 0,
    totalDistanceMeters: Double = 0.0,
    activeJourney: Journey? = null,
    recentJourneys: List<Journey> = emptyList(),
    onSelectJourney: (Journey) -> Unit = {},
    onStartJourney: () -> Unit = {},
    onStartDemo: () -> Unit = {},
    onExploreMap: () -> Unit = {},
    onOpenJourneys: () -> Unit = {},
    onOpenLearn: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val effects = LocalEffects.current

    val infiniteTransition = rememberInfiniteTransition(label = "homeAnim")
    val arcAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12_000, easing = LinearEasing),
        ),
        label = "arcRotation",
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
    ) {
        // Decorative background glow
        if (effects.glowEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.width > 20f && size.height > 20f) {
                    val r1 = (size.width * 0.65f).coerceAtLeast(20f)
                    val r2 = (size.width * 0.5f).coerceAtLeast(20f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ColorCyan.copy(alpha = 0.08f * glowPulse),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.45f),
                            radius = r1,
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ColorViolet.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.1f),
                            radius = r2,
                        )
                    )
                }
            }
        }

        // Main scroll content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.statusBarsPadding())
            Spacer(Modifier.height(12.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text  = "PATHWISE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 4.sp,
                            fontWeight    = FontWeight.Bold,
                        ),
                        color = ColorCyan,
                    )
                    Text(
                        text  = "Holographic HUD",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ColorTextPrimary,
                    )
                }

                IconButton(
                    onClick  = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ColorSurfaceVariant.copy(alpha = 0.8f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = ColorTextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Active tracking alert banner if recording
            if (activeJourney != null) {
                HoloPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExploreMap),
                    cornerRadius = 14.dp,
                    glowColor = ColorCyan
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusChip("RECORDING ACTIVE", isLive = true, color = ColorCyan)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                LocationUtils.formatDistance(activeJourney.recordedDistanceMeters),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ColorCyan
                            )
                        }
                        Text("View HUD →", style = MaterialTheme.typography.bodySmall, color = ColorTeal)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Start Journey Primary CTA
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (effects.glowEnabled) {
                    Box(
                        modifier = Modifier
                            .size((160 + 40 * glowPulse).dp)
                            .background(
                                color = ColorCyan.copy(alpha = 0.09f * glowPulse),
                                shape = CircleShape,
                            )
                    )
                }

                Button(
                    onClick  = if (activeJourney != null) onExploreMap else onStartJourney,
                    modifier = Modifier.size(148.dp),
                    shape    = CircleShape,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = ColorCyan,
                        contentColor   = ColorTextOnCyan,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier    = Modifier.size(36.dp),
                            tint        = ColorTextOnCyan,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = if (activeJourney != null) "Resume\nHUD" else "Start\nJourney",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 17.sp,
                            ),
                            color = ColorTextOnCyan,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Explore Map secondary action
            OutlinedButton(
                onClick  = onExploreMap,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp),
                border   = BorderStroke(1.dp, ColorTeal.copy(alpha = 0.7f)),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = ColorTeal,
                ),
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Explore Live Map HUD", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(10.dp))

            // Virtual Demo Walk Simulation CTA
            OutlinedButton(
                onClick  = onStartDemo,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(14.dp),
                border   = BorderStroke(1.dp, ColorCyan.copy(alpha = 0.85f)),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = ColorCyan),
            ) {
                Text("⚡ Launch Virtual Walk Simulation", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
            }

            Spacer(Modifier.height(28.dp))

            // Quick Stats Row
            HoloDivider()
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    modifier    = Modifier.weight(1f).clickable(onClick = onOpenJourneys),
                    label       = "Journeys",
                    value       = "$journeysCount",
                    icon        = Icons.Filled.PlayArrow,
                )
                StatCard(
                    modifier    = Modifier.weight(1f).clickable(onClick = onExploreMap),
                    label       = "Landmarks",
                    value       = "$landmarksCount",
                    icon        = Icons.Filled.Place,
                )
                StatCard(
                    modifier    = Modifier.weight(1f),
                    label       = "Distance",
                    value       = LocationUtils.formatDistance(totalDistanceMeters),
                    icon        = Icons.Filled.Star,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Hint card
            HoloPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint     = ColorCyan.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            if (journeysCount == 0) "Record your first route" else "City Recall Ready",
                            style = MaterialTheme.typography.titleSmall,
                            color = ColorTextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (journeysCount == 0)
                                "Press Start Journey when travelling. PathWise logs GPS fixes in the background with zero cloud dependencies."
                            else
                                "Review your recorded paths in the Journey Library or test your landmark order recall in the Learn tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary,
                        )
                    }
                }
            }

            // Recent Journeys List per PRD Section 6.3
            if (recentJourneys.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT LOGGED ROUTES",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
                        color = ColorCyan
                    )
                    Text(
                        text = "View Library →",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTeal,
                        modifier = Modifier.clickable { onOpenJourneys() }
                    )
                }
                Spacer(Modifier.height(10.dp))
                recentJourneys.take(3).forEach { journey ->
                    HoloPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectJourney(journey) }
                            .padding(bottom = 8.dp),
                        cornerRadius = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = journey.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ColorTextPrimary,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${LocationUtils.formatDistance(journey.recordedDistanceMeters)} • ${LocationUtils.formatDuration(journey.durationSeconds)} • ${journey.pointCount} pts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ColorTextSecondary
                                )
                            }
                            Text(
                                text = "REPLAY ▶",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ColorCyan
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bottom Navigation Dock
            BottomNavBar(
                onNavigate = { route ->
                    when (route) {
                        "Home" -> { /* Already home */ }
                        "Map" -> onExploreMap()
                        "Journeys" -> onOpenJourneys()
                        "Learn" -> onOpenLearn()
                        "Settings" -> onOpenSettings()
                    }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    HoloPanel(modifier = modifier, cornerRadius = 14.dp) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = ColorCyan.copy(alpha = 0.7f),
                modifier           = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = value,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
                color = ColorTextPrimary,
            )
            Text(
                text  = label,
                style = MaterialTheme.typography.bodySmall,
                color = ColorTextSecondary,
            )
        }
    }
}

@Composable
private fun BottomNavBar(onNavigate: (String) -> Unit) {
    val items: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>> = listOf(
        Icons.Filled.Home        to "Home",
        Icons.Filled.LocationOn  to "Map",
        Icons.Filled.PlayArrow   to "Journeys",
        Icons.Filled.Star        to "Learn",
        Icons.Filled.Settings    to "Settings",
    )
    HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, (icon, label) ->
                val isActive = index == 0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onNavigate(label) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = label,
                        tint               = if (isActive) ColorCyan else ColorNavInactive,
                        modifier           = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (isActive) ColorCyan else ColorNavInactive,
                    )
                }
            }
        }
    }
}
