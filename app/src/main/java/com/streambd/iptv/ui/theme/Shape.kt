package com.streambd.iptv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Crisp, restrained corners (Prime / Netflix style): cards 8dp, buttons
 * and chips 6dp, sheets/dialogs 12dp. Nothing pill-shaped except icons.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small      = RoundedCornerShape(6.dp),
    medium     = RoundedCornerShape(8.dp),
    large      = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)
