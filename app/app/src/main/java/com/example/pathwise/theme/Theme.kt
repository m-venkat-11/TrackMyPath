package com.example.pathwise.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

// ── PathWise always uses the dark holographic scheme ─────────────────────────
private val HolographicColorScheme = darkColorScheme(
    primary            = ColorCyan,
    onPrimary          = ColorTextOnCyan,
    primaryContainer   = ColorCyanDim,
    onPrimaryContainer = ColorTextPrimary,

    secondary          = ColorTeal,
    onSecondary        = ColorTextOnCyan,
    secondaryContainer = ColorTealDim,
    onSecondaryContainer = ColorTextPrimary,

    tertiary           = ColorViolet,
    onTertiary         = ColorTextPrimary,
    tertiaryContainer  = ColorVioletDim,
    onTertiaryContainer = ColorTextPrimary,

    background         = ColorBackground,
    onBackground       = ColorTextPrimary,

    surface            = ColorSurface,
    onSurface          = ColorTextPrimary,
    surfaceVariant     = ColorSurfaceVariant,
    onSurfaceVariant   = ColorTextSecondary,

    outline            = ColorOutline,

    error              = ColorError,
    onError            = ColorTextPrimary,
)

// ── Visual-effects intensity ──────────────────────────────────────────────────
enum class EffectsLevel { CINEMATIC, BALANCED, REDUCED }
typealias EffectsMode = EffectsLevel

data class PathWiseEffects(val level: EffectsLevel = EffectsLevel.BALANCED) {
    val glowEnabled: Boolean      get() = level != EffectsLevel.REDUCED
    val scanEnabled: Boolean      get() = level == EffectsLevel.CINEMATIC
    val parallaxEnabled: Boolean  get() = level != EffectsLevel.REDUCED
    val animDurationMs: Int
        get() = when (level) {
            EffectsLevel.CINEMATIC -> 220
            EffectsLevel.BALANCED  -> 180
            EffectsLevel.REDUCED   -> 80
        }
}

val LocalEffects = staticCompositionLocalOf { PathWiseEffects() }

// ── Theme entry-point ─────────────────────────────────────────────────────────
@Composable
fun PathWiseTheme(
    effects: PathWiseEffects = PathWiseEffects(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalEffects provides effects) {
        MaterialTheme(
            colorScheme = HolographicColorScheme,
            typography  = PathWiseTypography,
            content     = content,
        )
    }
}
