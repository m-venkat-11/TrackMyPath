package com.example.pathwise.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pathwise.theme.*

// ── Glass panel shapes ─────────────────────────────────────────────────────────
val GlassPanelShape = RoundedCornerShape(16.dp)
val GlassPanelShapeSmall = RoundedCornerShape(12.dp)
val GlassPanelShapeLarge = RoundedCornerShape(22.dp)

/**
 * Draws tactical sci-fi HUD bracket corner markers [  ] around any container.
 */
fun Modifier.hudCornerBrackets(
    color: Color = ColorCyan,
    bracketLength: Dp = 10.dp,
    strokeWidth: Dp = 1.5.dp,
    alpha: Float = 0.8f
): Modifier = this.drawBehind {
    val bLen = bracketLength.toPx()
    val sw = strokeWidth.toPx()
    val strokeColor = color.copy(alpha = alpha)

    // Top-Left bracket
    drawLine(strokeColor, Offset(0f, 0f), Offset(bLen, 0f), sw)
    drawLine(strokeColor, Offset(0f, 0f), Offset(0f, bLen), sw)

    // Top-Right bracket
    drawLine(strokeColor, Offset(size.width, 0f), Offset(size.width - bLen, 0f), sw)
    drawLine(strokeColor, Offset(size.width, 0f), Offset(size.width, bLen), sw)

    // Bottom-Left bracket
    drawLine(strokeColor, Offset(0f, size.height), Offset(bLen, size.height), sw)
    drawLine(strokeColor, Offset(0f, size.height), Offset(0f, size.height - bLen), sw)

    // Bottom-Right bracket
    drawLine(strokeColor, Offset(size.width, size.height), Offset(size.width - bLen, size.height), sw)
    drawLine(strokeColor, Offset(size.width, size.height), Offset(size.width, size.height - bLen), sw)
}

/**
 * Semi-transparent dark panel that mimics holographic glass.
 * Uses a gradient fill, thin cyan border, and optional tactical HUD corner brackets.
 */
@Composable
fun HoloPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    glowColor: Color = ColorCyan,
    withBrackets: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val effects = LocalEffects.current
    val borderAlpha = if (effects.glowEnabled) 0.35f else 0.18f
    val shape = RoundedCornerShape(cornerRadius)

    val bracketModifier = if (withBrackets) Modifier.hudCornerBrackets(glowColor) else Modifier

    Column(
        modifier = modifier
            .clip(shape)
            .background(ColorSurfaceVariant.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = glowColor.copy(alpha = borderAlpha),
                shape = shape,
            )
            .then(bracketModifier)
            .padding(16.dp),
        content = content,
    )
}

// ── Glowing status chip ───────────────────────────────────────────────────────
enum class TrackingStatus {
    RECORDING, PAUSED, WAITING_GPS, STOPPED, INTERRUPTED, CHECKING
}

@Composable
fun StatusChip(
    text: String,
    isLive: Boolean = false,
    color: Color = ColorCyan,
) {
    val effects = LocalEffects.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by if (isLive && effects.glowEnabled) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
    } else {
        remember { mutableFloatStateOf(0.85f) }
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isLive) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = pulseAlpha))
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp),
            color = color.copy(alpha = pulseAlpha),
        )
    }
}

// ── GPS Signal Bars ──────────────────────────────────────────────────────────
@Composable
fun GpsSatelliteSignal(accuracy: Float?, modifier: Modifier = Modifier) {
    val bars = when {
        accuracy == null -> 2
        accuracy < 6f -> 4
        accuracy < 14f -> 3
        accuracy < 30f -> 2
        else -> 1
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..4) {
            val isActive = i <= bars
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((3 + i * 3).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (isActive) ColorCyan else ColorCyan.copy(alpha = 0.2f))
            )
        }
    }
}

// ── Horizontal divider with gradient fade ────────────────────────────────────
@Composable
fun HoloDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ColorCyan.copy(alpha = 0.35f),
                        ColorTeal.copy(alpha = 0.35f),
                        Color.Transparent,
                    )
                )
            )
    )
}

// ── Cyan glow-text ───────────────────────────────────────────────────────────
@Composable
fun GlowLabel(text: String, modifier: Modifier = Modifier) {
    val effects = LocalEffects.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (effects.glowEnabled) ColorCyan else ColorTextSecondary,
        modifier = modifier,
    )
}
