package com.example.pathwise.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.repository.LocationUtils
import com.example.pathwise.theme.*

/**
 * Holographic journey recovery dialog per PRD FR-10.
 */
@Composable
fun RecoveryDialog(
    interruptedJourney: Journey,
    onResume: () -> Unit,
    onFinalise: () -> Unit,
    onDiscard: () -> Unit,
) {
    Dialog(onDismissRequest = onFinalise) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ColorSurface)
                .border(1.dp, ColorWarning.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ColorWarning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = ColorWarning,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Interrupted Journey Detected",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ColorTextPrimary
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "PathWise recovered points saved before the app closed. You can resume recording, finalise the saved route, or discard it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTextSecondary
                )

                Spacer(Modifier.height(16.dp))

                // Stats preview box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorSurfaceVariant.copy(alpha = 0.6f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Distance", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary)
                            Text(
                                LocationUtils.formatDistance(interruptedJourney.recordedDistanceMeters),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = ColorCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Duration", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary)
                            Text(
                                LocationUtils.formatDuration(interruptedJourney.durationSeconds),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = ColorCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Points", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary)
                            Text(
                                "${interruptedJourney.pointCount}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = ColorCyan
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onResume,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorCyan,
                            contentColor = ColorTextOnCyan
                        )
                    ) {
                        Text("Resume Recording", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onFinalise,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTeal)
                    ) {
                        Text("Finalise & Save")
                    }

                    TextButton(
                        onClick = onDiscard,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = ColorError)
                    ) {
                        Text("Discard Journey")
                    }
                }
            }
        }
    }
}
