package com.example.pathwise.ui.journeys

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.model.JourneyStatus
import com.example.pathwise.data.repository.LocationUtils
import com.example.pathwise.theme.*
import com.example.pathwise.ui.components.HoloDivider
import com.example.pathwise.ui.components.HoloPanel
import com.example.pathwise.ui.components.StatusChip

/**
 * Journey library screen displaying saved recordings per PRD Section 6.5.
 */
@Composable
fun JourneysScreen(
    journeys: List<Journey>,
    onSelectJourney: (Journey) -> Unit,
    onDeleteJourney: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
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
                    text = "Journey Library",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ColorTextPrimary
                )
                Text(
                    text = "${journeys.size} routes recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary
                )
            }
        }

        HoloDivider()
        Spacer(Modifier.height(16.dp))

        if (journeys.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No Recorded Journeys Yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = ColorTextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Start your first route from the Home dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(journeys, key = { it.id }) { journey ->
                    JourneyCard(
                        journey = journey,
                        onClick = { onSelectJourney(journey) },
                        onDelete = { onDeleteJourney(journey.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyCard(
    journey: Journey,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    HoloPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
        glowColor = if (journey.isDemo) ColorViolet else ColorCyan
    ) {
        Column {
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
                        text = java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault()).format(java.util.Date(journey.startTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTextSecondary
                    )
                }

                if (journey.isDemo) {
                    StatusChip("DEMO", isLive = false, color = ColorViolet)
                } else if (journey.status == JourneyStatus.INTERRUPTED) {
                    StatusChip("INTERRUPTED", isLive = false, color = ColorWarning)
                } else {
                    StatusChip("SAVED", isLive = false, color = ColorTeal)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("DISTANCE", LocationUtils.formatDistance(journey.recordedDistanceMeters))
                MetricItem("DURATION", LocationUtils.formatDuration(journey.durationSeconds))
                MetricItem("POINTS", "${journey.pointCount}")

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = ColorTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary, fontSize = 9.sp)
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = ColorCyan
        )
    }
}
