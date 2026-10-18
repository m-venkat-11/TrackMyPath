package com.example.pathwise.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Sans-serif font family (system default is fine; bundled fonts can be added
//    in a later milestone without changing downstream references) ─────────────
val PathWiseFontFamily = FontFamily.Default

// ── Monospaced for data readouts (coords, distances, elapsed time) ───────────
val PathWiseMonoFamily = FontFamily.Monospace

// ── PathWise Typography scale ────────────────────────────────────────────────
val PathWiseTypography = Typography(

    // Screen titles, holographic header text
    displayLarge = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.Light,
        fontSize     = 40.sp,
        lineHeight   = 48.sp,
        letterSpacing = (-0.5).sp,
    ),

    // Section headers
    headlineLarge = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 28.sp,
        lineHeight   = 34.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 22.sp,
        lineHeight   = 28.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 18.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.sp,
    ),

    // Card / panel titles
    titleLarge = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 17.sp,
        lineHeight   = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 15.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 13.sp,
        lineHeight   = 18.sp,
        letterSpacing = 0.1.sp,
    ),

    // Body copy
    bodyLarge = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.4.sp,
    ),

    // Labels and nav items
    labelLarge = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 13.sp,
        lineHeight   = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily   = PathWiseFontFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 11.sp,
        lineHeight   = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily   = PathWiseMonoFamily,   // monospaced for data readouts
        fontWeight   = FontWeight.Medium,
        fontSize     = 10.sp,
        lineHeight   = 13.sp,
        letterSpacing = 1.sp,
    ),
)
