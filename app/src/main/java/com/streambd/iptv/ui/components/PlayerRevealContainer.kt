package com.streambd.iptv.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay

val LocalPlayerIsRotated = compositionLocalOf { false }

/**
 * ⚠️ DEBUG BUILD — background colors mark each layout layer.
 *    RED   = outer BoxWithConstraints (should be full screen)
 *    GREEN = rotated inner Box       (should visually fill screen after 90° rotate)
 *    BLUE  = inner content wrapper   (should exactly match GREEN)
 *
 * Screenshot what you see and share — we'll know which layer collapses.
 * REMOVE the background colors once we identify the bug.
 */
@Composable
fun PlayerRevealContainer(
    modifier: Modifier = Modifier,
    exitRequested: Boolean = false,
    forceUpright: Boolean = false,
    onExitComplete: () -> Unit = {},
    enterDurationMs: Int = 550,
    exitDurationMs: Int = 420,
    content: @Composable () -> Unit
) {
    val config = LocalConfiguration.current
    val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT
    val shouldRotate = isPortrait && !forceUpright

    var revealed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(16)
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

    val rotation by animateFloatAsState(
        targetValue = if (revealed && shouldRotate) 90f else 0f,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "revealRotation"
    )
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.8f,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "revealScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (revealed) (duration * 0.6f).toInt()
            else (duration * 0.5f).toInt()
        ),
        label = "revealAlpha"
    )

    // ─────────────────────────────────────────────────────────────
    // DEBUG LAYER 1: outer BoxWithConstraints  → RED
    // ─────────────────────────────────────────────────────────────
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Red),
        contentAlignment = Alignment.Center
    ) {
        val parentW = maxWidth
        val parentH = maxHeight

        val innerW = if (shouldRotate) parentH else parentW
        val innerH = if (shouldRotate) parentW else parentH

        // ─────────────────────────────────────────────────────────
        // DEBUG LAYER 2: rotated inner Box  → GREEN
        // ─────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .width(innerW)
                .height(innerH)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(Color.Green)
        ) {
            // ────────────────────────────────────────────────────
            // DEBUG LAYER 3: content wrapper  → BLUE
            // ────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Blue)
            ) {
                CompositionLocalProvider(LocalPlayerIsRotated provides shouldRotate) {
                    content()
                }
            }
        }
    }
}
