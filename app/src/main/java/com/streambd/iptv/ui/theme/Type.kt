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

private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    spacing: Double = 0.0
) = TextStyle(
    fontFamily = JostFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp
)

/**
 * Hierarchy: headings are Bold, titles Medium/Bold, body Normal.
 * Sizes follow the Material 3 type scale so existing screens keep
 * their layout; only weights are tuned for a stronger, cleaner look.
 */
val Typography = Typography(
    displayLarge   = style(FontWeight.Bold,   57, 64, -0.25),
    displayMedium  = style(FontWeight.Bold,   45, 52),
    displaySmall   = style(FontWeight.Bold,   36, 44),

    headlineLarge  = style(FontWeight.Bold,   32, 40),
    headlineMedium = style(FontWeight.Bold,   28, 36),
    headlineSmall  = style(FontWeight.Bold,   24, 32),

    titleLarge     = style(FontWeight.Bold,   22, 28),
    titleMedium    = style(FontWeight.Medium, 16, 24, 0.15),
    titleSmall     = style(FontWeight.Medium, 14, 20, 0.10),

    bodyLarge      = style(FontWeight.Normal, 16, 24, 0.50),
    bodyMedium     = style(FontWeight.Normal, 14, 20, 0.25),
    bodySmall      = style(FontWeight.Normal, 12, 16, 0.40),

    labelLarge     = style(FontWeight.Medium, 14, 20, 0.10),
    labelMedium    = style(FontWeight.Medium, 12, 16, 0.50),
    labelSmall     = style(FontWeight.Medium, 11, 16, 0.50)
)
