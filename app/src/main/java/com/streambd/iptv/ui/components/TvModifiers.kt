package com.streambd.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.TvFocusedBorder
import com.streambd.iptv.ui.theme.TvFocusedSurface
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * Focus decoration modifier for TV + touch.
 *
 * FIXES vs. previous version:
 *  - No `clip = true` on the graphics layer (it was cutting the scaled border).
 *  - Uses `TvFocusedBorder` (softer cyan) instead of the brighter `BioAccent`.
 *  - Only adds a border when focused — no `0.dp` border layout pass while idle.
 *  - Uses `hasFocus` so nested focusable children still light up the parent.
 */
fun Modifier.tvFocusBorder(
    shape: Shape = RoundedCornerShape(8.dp),
    focusScale: Float = 1.05f
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = tween(220),
        label = "tvScale"
    )
    val glowColor by animateColorAsState(
        targetValue = if (isFocused) TvFocusedBorder else Color.Transparent,
        animationSpec = tween(220),
        label = "tvGlow"
    )
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 10.dp else 0.dp,
        animationSpec = tween(220),
        label = "tvElev"
    )

    this
        .onFocusChanged { isFocused = it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = elevation.toPx()
            this.shape = shape
            // clip intentionally NOT enabled — see KDoc
        }
        .then(
            if (isFocused) {
                Modifier.border(width = 3.dp, color = glowColor, shape = shape)
            } else Modifier
        )
}

/**
 * TV icon button with a focus ring.
 *
 * FIXES: previous version relied on `IconButton.onClick` alone — D-pad ENTER
 * isn't honored on every OEM. We add an explicit key handler via `tvTouchClick`.
 */
@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = modifier
            .tvTouchClick(onClick)
            .onFocusChanged { isFocused = it.hasFocus }
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, TvFocusedBorder, CircleShape)
                } else Modifier
            )
    ) {
        icon()
    }
}

/**
 * Tap + D-pad ENTER handler.
 *
 * FIXES vs. previous version:
 *  - Adds `Modifier.focusable()` so `onKeyEvent` actually receives events
 *    (without focusable, the node never gets focus and keys never arrive).
 *  - Consumes only KeyUp for ENTER/CENTER to avoid double-fire with `clickable`.
 *  - Uses `rememberUpdatedState` so callers don't have to remember the lambda.
 */
fun Modifier.tvTouchClick(onClick: () -> Unit): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    this
        .focusable()
        .pointerInput(Unit) {
            detectTapGestures(onTap = { currentOnClick() })
        }
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp &&
                (event.key == Key.Enter ||
                    event.key == Key.DirectionCenter ||
                    event.key == Key.NumPadEnter)
            ) {
                currentOnClick()
                true
            } else false
        }
}
