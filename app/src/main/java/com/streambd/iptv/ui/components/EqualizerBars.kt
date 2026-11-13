package com.streambd.iptv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tiny animated "now playing" indicator — 3 vertical bars that pulse at
 * staggered speeds, like Spotify's / Apple Music's equaliser.
 *
 * Only composed when a row IS currently playing, so at most 1–2 instances
 * exist at a time. Each bar has a slightly longer cycle than the previous
 * one, so they never sync up and the effect looks natural.
 *
 * The animation uses [rememberInfiniteTransition] — it auto-stops when the
 * composable leaves composition, so nothing runs in the background.
 */
@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    color: Color,
    barCount: Int = 3,
    barWidth: Dp = 3.dp,
    maxHeight: Dp = 14.dp,
    minHeight: Dp = 4.dp
) {
    val transition = rememberInfiniteTransition(label = "eq")

    val heights = (0 until barCount).map { i ->
        transition.animateFloat(
            initialValue = minHeight.value,
            targetValue = maxHeight.value,
            animationSpec = infiniteRepeatable(
                // Longer cycle per bar → out-of-phase pulsing
                animation = tween(
                    durationMillis = 380 + i * 130,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eqBar$i"
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(h.value.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}
