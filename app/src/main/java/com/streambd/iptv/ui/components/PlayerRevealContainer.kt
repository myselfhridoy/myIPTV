package com.streambd.iptv.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay

/**
 * Kept for backwards compatibility. Under Approach B the Activity rotates on
 * its own, so dialogs no longer need inline routing; this local stays false.
 */
val LocalPlayerIsRotated = compositionLocalOf { false }

/**
 * Fade-only reveal that covers the Activity's orientation change.
 *
 * Timeline (mobile portrait → landscape):
 *   0ms    → solid black (rotation in progress)
 *   ~380ms → rotation done; content fades in (450ms)
 *
 * Timeline (TV / already landscape):
 *   ~80ms  → content fades in immediately
 */
@Composable
fun PlayerRevealContainer(
    modifier: Modifier = Modifier,
    exitRequested: Boolean = false,
    forceUpright: Boolean = false,
    onExitComplete: () -> Unit = {},
    enterDurationMs: Int = 450,
    exitDurationMs: Int = 400,
    content: @Composable () -> Unit
) {
    val config = LocalConfiguration.current
    val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT

    // Portrait phone → wait for Activity rotation (≈380ms). TV → almost immediate.
    val initialDelayMs = if (isPortrait && !forceUpright) 380L else 80L

    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(initialDelayMs)
        revealed = true
    }

    LaunchedEffect(exitRequested) {
        if (exitRequested) {
            revealed = false
            delay(exitDurationMs.toLong() + 40)
            onExitComplete()
        }
    }

    val duration = if (revealed) enterDurationMs else exitDurationMs

    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        label = "revealAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
        ) {
            content()
        }
    }
}
