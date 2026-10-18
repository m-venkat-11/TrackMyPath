package com.example.pathwise.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pathwise.theme.*

/**
 * Holographic dialog for creating personal landmarks per PRD Section 6.8.
 */
@Composable
fun NewLandmarkDialog(
    latitude: Double,
    longitude: Double,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String?, notes: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Transit") }
    var notes by remember { mutableStateOf("") }

    val categories = listOf("Transit", "Historic", "Commercial", "Scenic", "Personal")

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ColorSurface)
                .border(1.dp, BrushCyanGlow, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Add Personal Landmark",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ColorTextPrimary
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = String.format(java.util.Locale.US, "Coordinates: %.5f° N, %.5f° E", latitude, longitude),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = ColorCyan.copy(alpha = 0.8f)
                )

                Spacer(Modifier.height(16.dp))

                // Landmark Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Landmark Name (required)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorCyan,
                        unfocusedBorderColor = ColorTextSecondary.copy(alpha = 0.5f),
                        focusedTextColor = ColorTextPrimary,
                        unfocusedTextColor = ColorTextPrimary,
                    )
                )

                Spacer(Modifier.height(12.dp))

                // Category selection row
                Text("Category", style = MaterialTheme.typography.bodySmall, color = ColorTextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(4).forEach { cat ->
                        val isSelected = category == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ColorCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ColorCyan,
                                containerColor = ColorSurfaceVariant,
                                labelColor = ColorTextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) ColorCyan else Color.Transparent
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Notes field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Visual Cues (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorCyan,
                        unfocusedBorderColor = ColorTextSecondary.copy(alpha = 0.5f),
                        focusedTextColor = ColorTextPrimary,
                        unfocusedTextColor = ColorTextPrimary,
                    )
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name.trim(), category, notes.ifBlank { null })
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorCyan,
                            contentColor = ColorTextOnCyan,
                            disabledContainerColor = ColorCyan.copy(alpha = 0.3f),
                            disabledContentColor = ColorTextOnCyan.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Save Landmark", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
