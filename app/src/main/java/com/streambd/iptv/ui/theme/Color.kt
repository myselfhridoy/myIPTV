package com.streambd.iptv.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Premium Bioscope Theme Colors
// ─────────────────────────────────────────────────────────────
val DarkNavyBackground = Color(0xFF0A0E17)      // Deep Navy/Black
val DarkNavySurface    = Color(0xFF111723)      // Card surface
val BioCardLight       = Color(0xFF1C2436)      // Hover/Focus card bg
val BioAccent          = Color(0xFF48E5E5)      // Cyan primary accent
val BioBrand           = Color(0xFF4361EE)      // Deep blue brand

val BioTextPrimary     = Color(0xFFFFFFFF)
val BioTextSecondary   = Color(0xFFE5E7EB)
val BioTextTertiary    = Color(0xFFD1D5DB)

// Aliases (backward compatibility)
val PrimaryDark        = DarkNavyBackground
val SurfaceDark        = DarkNavySurface
val WhiteText          = BioTextPrimary
val GrayText           = BioTextTertiary
val TealAccent         = BioAccent                // same value as BioAccent
val TealAccentDark     = Color(0xFF008D94)

val BottomNavBackground = Color(0xFF141920)
val SelectedItemColor   = BioAccent
val UnselectedItemColor = Color(0xFF555555)

// TV-specific tones — softer than BioAccent so focus rings don't shout on TV
val TvFocusedBorder  = Color(0xFF00ADB5)
val TvFocusedSurface = Color(0xFF252D36)
