package com.streambd.iptv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Wraps player content so it fades in + slides up while the Activity finishes
 * rotating to landscape.
 *
 * Timeline:
 *   0ms    → root is solid black (rotation in progress)
 *   280ms  → rotation done, content starts revealing
 *   280–600ms → alpha 0→1, translationY 24→0
 *
 * The root Box is always opaque black, so nothing behind the player shows
 * through during the rotation frame.
 */
@Composable
fun PlayerRevealContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(280)   // wait for Activity rotation
        revealed = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "playerRevealAlpha"
    )

    val translationY by animateFloatAsState(
        targetValue = if (revealed) 0f else 24f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "playerRevealY"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = alpha
                    this.translationY = translationY
                }
        ) {
            content()
        }
    }
}
