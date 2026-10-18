package com.example.pathwise.ui.learn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathwise.data.model.Landmark
import com.example.pathwise.theme.*
import com.example.pathwise.ui.components.HoloDivider
import com.example.pathwise.ui.components.HoloPanel
import kotlin.math.abs
import kotlin.math.roundToInt

enum class QuizMode(val label: String) {
    SEQUENCE("Sequence"),
    STREET_RADAR("Street Radar"),
    DISTANCE_ESTIMATE("Distance Guess")
}

/**
 * City Recall Arena per PRD Section 6.9.
 * Provides 3 interactive memory exercises:
 * 1. Landmark Sequence Recall (spatial chronological order)
 * 2. Street Radar (associating landmarks with road names)
 * 3. Distance Estimation (slider-based spatial intuition)
 */
@Composable
fun LearnScreen(
    landmarks: List<Landmark>,
    onNavigateBack: () -> Unit,
) {
    var activeMode by remember { mutableStateOf(QuizMode.SEQUENCE) }
    var score by remember { mutableIntStateOf(0) }
    var completedCount by remember { mutableIntStateOf(0) }

    // Quiz 1: Sequence Recall State
    var seqSelectedOption by remember { mutableStateOf<Int?>(null) }
    var seqSubmitted by remember { mutableStateOf(false) }

    // Quiz 2: Street Radar State
    var streetSelectedOption by remember { mutableStateOf<Int?>(null) }
    var streetSubmitted by remember { mutableStateOf(false) }

    // Quiz 3: Distance Slider State
    var estimatedMeters by remember { mutableFloatStateOf(800f) }
    var distanceSubmitted by remember { mutableStateOf(false) }
    val actualDistance = 1250f

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Header
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
                        text = "City Recall Arena",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ColorTextPrimary
                    )
                    Text(
                        text = "Spatial memory training & mental mapping",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTeal
                    )
                }
            }

            // XP / Score Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ColorCyan.copy(alpha = 0.15f))
                    .border(1.dp, ColorCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${score * 100} XP",
                    fontWeight = FontWeight.Bold,
                    color = ColorCyan,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HoloDivider()
        Spacer(Modifier.height(16.dp))

        // Quiz Mode Segmented Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ColorSurfaceVariant.copy(alpha = 0.6f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuizMode.entries.forEach { mode ->
                val isSelected = activeMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ColorCyan.copy(alpha = 0.25f) else Color.Transparent)
                        .border(if (isSelected) 1.dp else 0.dp, if (isSelected) ColorCyan else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { activeMode = mode }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = if (isSelected) ColorCyan else ColorTextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when (activeMode) {
            QuizMode.SEQUENCE -> {
                val seqOptions = listOf(
                    "Central Clock Tower",
                    "North Bridge Crossing",
                    "Riverside Promenade",
                    "Heritage Archway"
                )
                val correctIndex = 0

                HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, glowColor = ColorCyan, withBrackets = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = ColorCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("MISSION: LANDMARK SEQUENCE", fontWeight = FontWeight.Bold, color = ColorCyan, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "On your route departing Downtown towards the Waterfront, which landmark comes first in physical sequence?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ColorTextPrimary
                    )
                }

                Spacer(Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    seqOptions.forEachIndexed { index, option ->
                        val isSelected = seqSelectedOption == index
                        val isCorrect = index == correctIndex

                        val borderColor = when {
                            seqSubmitted && isCorrect -> ColorGreen
                            seqSubmitted && isSelected && !isCorrect -> ColorError
                            isSelected -> ColorCyan
                            else -> ColorBorderSubtle
                        }

                        val bgColor = when {
                            seqSubmitted && isCorrect -> ColorGreen.copy(alpha = 0.15f)
                            seqSubmitted && isSelected && !isCorrect -> ColorError.copy(alpha = 0.15f)
                            isSelected -> ColorCyan.copy(alpha = 0.1f)
                            else -> ColorSurface
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .clickable(enabled = !seqSubmitted) { seqSelectedOption = index }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = ColorTextPrimary
                                )
                                if (seqSubmitted && isCorrect) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = ColorGreen)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (!seqSubmitted) {
                    Button(
                        onClick = {
                            if (seqSelectedOption != null) {
                                seqSubmitted = true
                                if (seqSelectedOption == correctIndex) {
                                    score++
                                    completedCount++
                                }
                            }
                        },
                        enabled = seqSelectedOption != null,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorCyan, contentColor = ColorTextOnCyan)
                    ) {
                        Text("Verify Recall", fontWeight = FontWeight.Bold)
                    }
                } else {
                    HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                        val correct = seqSelectedOption == correctIndex
                        Text(
                            text = if (correct) "Target Identified! +100 XP" else "Recall Mismatch",
                            fontWeight = FontWeight.Bold,
                            color = if (correct) ColorGreen else ColorWarning
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (correct)
                                "Affirmative. Central Clock Tower stands at the first major junction."
                            else
                                "Central Clock Tower was encountered first. Reviewing this path in Replay mode reinforces your spatial sequence memory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary
                        )
                    }
                }
            }

            QuizMode.STREET_RADAR -> {
                val streetOptions = listOf(
                    "Grand Promenade Avenue",
                    "Canal Way Boulevard",
                    "Station Road Bypass",
                    "Market Cross Street"
                )
                val correctIndex = 1

                HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, glowColor = ColorCyan, withBrackets = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = ColorCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("MISSION: STREET IDENTIFIER", fontWeight = FontWeight.Bold, color = ColorCyan, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Which road connects the North Bridge Archway to the Riverside Promenade?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ColorTextPrimary
                    )
                }

                Spacer(Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    streetOptions.forEachIndexed { index, option ->
                        val isSelected = streetSelectedOption == index
                        val isCorrect = index == correctIndex

                        val borderColor = when {
                            streetSubmitted && isCorrect -> ColorGreen
                            streetSubmitted && isSelected && !isCorrect -> ColorError
                            isSelected -> ColorCyan
                            else -> ColorBorderSubtle
                        }

                        val bgColor = when {
                            streetSubmitted && isCorrect -> ColorGreen.copy(alpha = 0.15f)
                            streetSubmitted && isSelected && !isCorrect -> ColorError.copy(alpha = 0.15f)
                            isSelected -> ColorCyan.copy(alpha = 0.1f)
                            else -> ColorSurface
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .clickable(enabled = !streetSubmitted) { streetSelectedOption = index }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = ColorTextPrimary
                                )
                                if (streetSubmitted && isCorrect) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = ColorGreen)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (!streetSubmitted) {
                    Button(
                        onClick = {
                            if (streetSelectedOption != null) {
                                streetSubmitted = true
                                if (streetSelectedOption == correctIndex) {
                                    score++
                                    completedCount++
                                }
                            }
                        },
                        enabled = streetSelectedOption != null,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorCyan, contentColor = ColorTextOnCyan)
                    ) {
                        Text("Confirm Road", fontWeight = FontWeight.Bold)
                    }
                } else {
                    HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                        val correct = streetSelectedOption == correctIndex
                        Text(
                            text = if (correct) "Correct Street! +100 XP" else "Check Road Map",
                            fontWeight = FontWeight.Bold,
                            color = if (correct) ColorGreen else ColorWarning
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Canal Way Boulevard spans parallel to the waterway, connecting North Bridge to the southern promenade.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary
                        )
                    }
                }
            }

            QuizMode.DISTANCE_ESTIMATE -> {
                HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, glowColor = ColorCyan, withBrackets = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = ColorCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("MISSION: DISTANCE INTUITION", fontWeight = FontWeight.Bold, color = ColorCyan, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Estimate the straight-line distance between Central Clock Tower and the Riverside Promenade:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = ColorTextPrimary
                    )
                }

                Spacer(Modifier.height(20.dp))

                HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${estimatedMeters.roundToInt()} metres",
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = ColorCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "~${(estimatedMeters / 83.3).roundToInt()} minutes walk",
                            fontSize = 12.sp,
                            color = ColorTextSecondary
                        )

                        Spacer(Modifier.height(16.dp))

                        Slider(
                            value = estimatedMeters,
                            onValueChange = { estimatedMeters = it },
                            valueRange = 200f..3000f,
                            steps = 27,
                            enabled = !distanceSubmitted,
                            colors = SliderDefaults.colors(
                                thumbColor = ColorCyan,
                                activeTrackColor = ColorCyan,
                                inactiveTrackColor = ColorSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (!distanceSubmitted) {
                    Button(
                        onClick = {
                            distanceSubmitted = true
                            val errorPct = abs(estimatedMeters - actualDistance) / actualDistance
                            if (errorPct <= 0.25f) {
                                score++
                                completedCount++
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorCyan, contentColor = ColorTextOnCyan)
                    ) {
                        Text("Lock In Estimation", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val diff = abs(estimatedMeters - actualDistance).roundToInt()
                    val accuracyPct = ((1f - (diff / actualDistance)).coerceIn(0f, 1f) * 100).roundToInt()

                    HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp, glowColor = if (accuracyPct >= 80) ColorGreen else ColorWarning) {
                        Text(
                            text = "Actual Distance: ${actualDistance.roundToInt()} metres ($accuracyPct% Accuracy)",
                            fontWeight = FontWeight.Bold,
                            color = if (accuracyPct >= 80) ColorGreen else ColorCyan,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Your estimate was off by $diff metres. Spatial distance judgment improves rapidly with regular route reviews.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ColorTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
