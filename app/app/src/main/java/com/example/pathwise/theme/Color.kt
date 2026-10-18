package com.example.pathwise.theme

import androidx.compose.ui.graphics.Color

// ── Base backgrounds ──────────────────────────────────────────────────────────
/** Near-black midnight navy — the main app canvas */
val ColorBackground        = Color(0xFF050D1A)
/** Dark blue-grey surface for panels and cards */
val ColorSurface           = Color(0xFF0D1B2E)
/** Slightly lighter surface for elevated panels */
val ColorSurfaceVariant    = Color(0xFF122140)
/** Thin low-opacity border drawn on glass panels */
val ColorOutline           = Color(0x2600E5FF)   // 15% cyan

// ── Primary accents ───────────────────────────────────────────────────────────
/** Electric cyan — route lines, FAB, active controls */
val ColorCyan              = Color(0xFF00E5FF)
val ColorCyanDim           = Color(0xFF00B8D4)
val ColorCyanGlow          = Color(0x4000E5FF)   // 25% alpha glow halo

/** Teal — secondary interactive elements */
val ColorTeal              = Color(0xFF00BCD4)
val ColorTealDim           = Color(0xFF0097A7)

/** Violet — selected/secondary states, used sparingly */
val ColorViolet            = Color(0xFF7C4DFF)
val ColorVioletDim         = Color(0xFF651FFF)

// ── Semantic colours ──────────────────────────────────────────────────────────
/** Readable green for success/active states */
val ColorSuccess           = Color(0xFF00E676)
/** Amber warning */
val ColorWarning           = Color(0xFFFFAB00)
/** Accessible red-orange for errors / permission issues */
val ColorError             = Color(0xFFFF5252)
/** Paused / standby indicator */
val ColorPaused            = Color(0xFFFFAB00)

// ── Text ──────────────────────────────────────────────────────────────────────
val ColorTextPrimary       = Color(0xFFF0F8FF)   // near-white
val ColorTextSecondary     = Color(0xFF8EACBD)   // cool blue-grey
val ColorTextDisabled      = Color(0xFF3D5268)
val ColorTextOnCyan        = Color(0xFF050D1A)   // text on cyan FAB

// ── Route & map overlays ──────────────────────────────────────────────────────
val ColorRouteLine         = Color(0xFF00E5FF)
val ColorRouteGlow         = Color(0x6600E5FF)   // 40% alpha outer glow
val ColorLandmarkBeacon    = Color(0xFF7C4DFF)
val ColorAccuracyCircle    = Color(0x1A00E5FF)   // 10% alpha fill

// ── Bottom-nav inactive tint ──────────────────────────────────────────────────
val ColorNavInactive       = Color(0xFF2E4A64)

// ── Aliases & Brushes for holographic HUD ────────────────────────────────────
val ColorGreen             = ColorSuccess
val ColorBorderCyan        = ColorOutline
val ColorBorderSubtle      = Color(0x3300E5FF)
val BrushCyanGlow          = androidx.compose.ui.graphics.SolidColor(ColorCyan.copy(alpha = 0.6f))
