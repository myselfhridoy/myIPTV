package com.streambd.iptv.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// Prime-Video-inspired palette (dark navy + electric blue)
// ─────────────────────────────────────────────────────────────

// ── Surfaces (darkest → lightest) ────────────────────────────
val PrimeBackground      = Color(0xFF0F171E)   // app background
val PrimeSurface         = Color(0xFF1A242F)   // cards, sheets
val PrimeSurfaceHigh     = Color(0xFF243242)   // focused / elevated card
val PrimeSurfaceHighest  = Color(0xFF2F4054)   // chips, pressed state
val PrimeOutline         = Color(0xFF3A4A5C)   // dividers, borders

// ── Brand ────────────────────────────────────────────────────
val PrimeBlue            = Color(0xFF1A98FF)   // primary accent
val PrimeBlueDark        = Color(0xFF0B6BB5)   // pressed / container
val PrimeCyan            = Color(0xFF00A8E1)   // secondary accent
val PrimeGold            = Color(0xFFF5C518)   // ratings, "premium" badges
val PrimeError           = Color(0xFFFF5A5F)
val PrimeLive            = Color(0xFFE53935)   // LIVE badge

// ── Text ─────────────────────────────────────────────────────
val PrimeTextPrimary     = Color(0xFFFFFFFF)
val PrimeTextSecondary   = Color(0xFFB8C4D0)
val PrimeTextTertiary    = Color(0xFF8A99A8)

// ── TV focus ─────────────────────────────────────────────────
val PrimeFocusRing       = Color(0xFFE6EEF5)   // near-white ring, like Prime TV

// ─────────────────────────────────────────────────────────────
// BACKWARD-COMPATIBLE ALIASES
// Old screens still reference these names. They now point to the
// new palette so every existing screen picks up the new theme
// automatically. Removed in the final clean-up step.
// ─────────────────────────────────────────────────────────────
val DarkNavyBackground  = PrimeBackground
val DarkNavySurface     = PrimeSurface
val BioCardLight        = PrimeSurfaceHigh
val BioAccent           = PrimeBlue
val BioBrand            = PrimeCyan

val BioTextPrimary      = PrimeTextPrimary
val BioTextSecondary    = PrimeTextSecondary
val BioTextTertiary     = PrimeTextTertiary

val PrimaryDark         = PrimeBackground
val SurfaceDark         = PrimeSurface
val WhiteText           = PrimeTextPrimary
val GrayText            = PrimeTextSecondary
val TealAccent          = PrimeBlue
val TealAccentDark      = PrimeBlueDark

val BottomNavBackground = Color(0xFF121B24)
val SelectedItemColor   = PrimeBlue
val UnselectedItemColor = PrimeTextTertiary

val TvFocusedBorder     = PrimeFocusRing
val TvFocusedSurface    = PrimeSurfaceHigh
