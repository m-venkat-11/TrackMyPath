package com.example.pathwise.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathwise.theme.*
import com.example.pathwise.ui.components.HoloDivider
import com.example.pathwise.ui.components.HoloPanel

/**
 * Settings and visual intensity configuration per PRD Section 6.11.
 */
@Composable
fun SettingsScreen(
    currentMode: EffectsMode,
    onModeChange: (EffectsMode) -> Unit,
    onSeedDemo: () -> Unit,
    onNavigateBack: () -> Unit,
) {
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
                    text = "Settings & HUD Config",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ColorTextPrimary
                )
                Text(
                    text = "Display, privacy, and map providers",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HoloDivider()
        Spacer(Modifier.height(20.dp))

        // Visual Intensity Section
        Text(
            "VISUAL INTENSITY (PRD 5.8)",
            style = MaterialTheme.typography.labelSmall,
            color = ColorCyan
        )
        Spacer(Modifier.height(10.dp))

        val modes: List<Triple<EffectsLevel, String, String>> = listOf(
            Triple(EffectsLevel.CINEMATIC, "Cinematic Holographic", "Luminous route glows, animated radar pulses, and glass refraction."),
            Triple(EffectsLevel.BALANCED, "Balanced Mode (Default)", "Optimal balance of futuristic visual flair, fluid performance, and battery efficiency."),
            Triple(EffectsLevel.REDUCED, "Reduced Effects / High Contrast", "Solid high-contrast lines, disabled background animations for maximum battery life.")
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (mode, title, desc) ->
                val isSelected = currentMode == mode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) ColorCyan.copy(alpha = 0.12f) else ColorSurface)
                        .border(1.dp, if (isSelected) ColorCyan else ColorBorderSubtle, RoundedCornerShape(12.dp))
                        .clickable { onModeChange(mode) }
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) ColorCyan else ColorTextPrimary
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = { onModeChange(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ColorCyan,
                                    unselectedColor = ColorTextSecondary
                                )
                            )
                        }
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Map and Geocoding Provider
        Text(
            "MAP & GEOCODING PROVIDER",
            style = MaterialTheme.typography.labelSmall,
            color = ColorCyan
        )
        Spacer(Modifier.height(10.dp))

        HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Column {
                Text("OpenStreetMap & Nominatim", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ColorTextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Selected from GitHub public-apis. Provides open street data, global forward/reverse geocoding, and zero third-party telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorTextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Data © OpenStreetMap contributors under ODbL licence.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = ColorTeal
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Privacy Guarantee
        Text(
            "PRIVACY PROMISE",
            style = MaterialTheme.typography.labelSmall,
            color = ColorCyan
        )
        Spacer(Modifier.height(10.dp))

        HoloPanel(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = ColorGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("100% On-Device Storage", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ColorTextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your GPS coordinates and personal landmarks stay on your device inside a local SQLite database. No account required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ColorTextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Demo Data Generator
        Text(
            "DEVELOPER & TESTING TOOLS",
            style = MaterialTheme.typography.labelSmall,
            color = ColorCyan
        )
        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onSeedDemo,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ColorViolet,
                contentColor = ColorTextPrimary
            )
        ) {
            Text("Load Demo Route & Landmarks", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(30.dp))
    }
}
