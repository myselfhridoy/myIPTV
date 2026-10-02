package com.streambd.iptv.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
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
 * Rotates the player content 90° inside a PORTRAIT Activity instead of
 * requesting an Activity-level orientation change.
 *
 * KEY FIX:
 *   The inner box is created with `requiredWidth` / `requiredHeight` instead
 *   of `width` / `height`. Compose normally clamps children to their parent's
 *   constraints — so a child declared `width(800.dp)` inside a 360dp-wide
 *   parent would actually be clamped to 360dp, making the whole rotated
 *   content appear as a small square in the middle of the screen.
 *   `required*` bypasses the parent's constraints, so the child really is
 *   800×360 before rotation → fills the screen after rotation.
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val parentW = maxWidth
        val parentH = maxHeight

        // Landscape logical dimensions for the inner box when we're going to
        // rotate. requiredWidth/Height bypass the parent constraints so the
        // inner box really is (parentH × parentW).
        val innerW = if (shouldRotate) parentH else parentW
        val innerH = if (shouldRotate) parentW else parentH

        Box(
            modifier = Modifier
                .requiredWidth(innerW)
                .requiredHeight(innerH)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
        ) {
            CompositionLocalProvider(LocalPlayerIsRotated provides shouldRotate) {
                content()
            }
        }
    }
}
