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

/**
 * True when we are inside the rotated player container.
 *
 * Dialogs read this via `LocalPlayerIsRotated.current`:
 *   • true  → render INLINE (as an overlay inside this container) so the dialog
 *             inherits the 90° rotation and appears in landscape like the player.
 *   • false → render as a normal Dialog window (default behaviour on mobile
 *             outside the player, and always on TV which is not rotated).
 */
val LocalPlayerIsRotated = compositionLocalOf { false }

/**
 * Rotates the player content 90° inside a PORTRAIT Activity instead of
 * requesting an Activity-level orientation change.
 *
 * Why: on phones, letting the Activity rotate spins the whole screen
 * (including the previous screen that is sliding away) → looks messy.
 * Rotating the player content ourselves keeps the Activity in portrait,
 * so only the player appears to "turn sideways".
 *
 * Size trick: inner Box gets LANDSCAPE logical dimensions
 * (width = parent height, height = parent width). After 90° rotation it
 * visually fills the portrait parent exactly.
 *
 * @param exitRequested caller sets true when back was pressed; container runs
 *                      the reverse animation then invokes onExitComplete.
 * @param forceUpright  disable rotation (used in PiP where the OS owns layout).
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

    // Flip after the first frame so 0° is painted as the starting state.
    LaunchedEffect(Unit) {
        delay(16)
        revealed = true
    }

    // Reverse on exit, then notify the caller.
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

        // Swap logical size only when we're going to rotate, so after rotation
        // the content exactly covers the parent.
        val innerW = if (shouldRotate) parentH else parentW
        val innerH = if (shouldRotate) parentW else parentH

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
        ) {
            // Expose rotation state to any child (dialogs) via CompositionLocal.
            CompositionLocalProvider(LocalPlayerIsRotated provides shouldRotate) {
                content()
            }
        }
    }
}
