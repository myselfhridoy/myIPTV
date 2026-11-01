package com.streambd.iptv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.streambd.iptv.R

val JostFontFamily = FontFamily(
    Font(R.font.jost_regular, FontWeight.Normal),
    Font(R.font.jost_medium, FontWeight.Medium),
    Font(R.font.jost_bold, FontWeight.Bold)
)

/** UI text (labels, body, small titles) uses the system sans: it stays
 *  readable at small sizes, supports Bengali natively, and does not look
 *  like a "display" font in lists and cards. */
private val UiFontFamily = FontFamily.Default

private fun heading(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = JostFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp
)

private fun ui(weight: FontWeight, size: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = UiFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp
)

val Typography = Typography(
    // Jost — big headings only
    displayLarge   = heading(FontWeight.Bold, 57, 64),
    displayMedium  = heading(FontWeight.Bold, 45, 52),
    displaySmall   = heading(FontWeight.Bold, 36, 44),
    headlineLarge  = heading(FontWeight.Bold, 32, 40),
    headlineMedium = heading(FontWeight.Bold, 28, 36),
    headlineSmall  = heading(FontWeight.Bold, 24, 32),
    titleLarge     = heading(FontWeight.Bold, 20, 26),

    // System sans — everything people read in lists, cards, dialogs
    titleMedium    = ui(FontWeight.Medium, 16, 22, 0.1),
    titleSmall     = ui(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge      = ui(FontWeight.Normal, 15, 22),
    bodyMedium     = ui(FontWeight.Normal, 14, 20),
    bodySmall      = ui(FontWeight.Normal, 12, 16),
    labelLarge     = ui(FontWeight.Medium, 14, 20, 0.1),
    labelMedium    = ui(FontWeight.Medium, 12, 16, 0.2),
    labelSmall     = ui(FontWeight.Medium, 11, 14, 0.2)
)
