package com.streambd.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.TvFocusedBorder

/**
 * Focus decoration modifier for TV + touch.
 *
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
 * TV icon button with a CLEAR focus ring.
 *
 *  - Custom composable (not Material `IconButton`) so we control the entire
 *    focus pipeline. Nested focusable nodes inside `IconButton` were confusing
 *    focus tracking on some OEMs (TX3 Mini, MXQ, etc.).
 *  - On focus: scales 1.08x + tinted background + 2dp accent border — very
 *    visible on any TV.
 *  - `defaultMinSize` guarantees the 48dp Material touch target; callers can
 *    still override the size via their own `.size(...)` modifier.
 */
@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "tvIconScale"
    )
    val bg by animateColorAsState(
        targetValue = if (isFocused) TvFocusedBorder.copy(alpha = 0.22f)
        else Color.Transparent,
        animationSpec = tween(180),
        label = "tvIconBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) TvFocusedBorder else Color.Transparent,
        animationSpec = tween(180),
        label = "tvIconBorder"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(CircleShape)
            .background(bg, CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .tvTouchClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Tap + D-pad ENTER handler.
 *
 *  - Adds `Modifier.focusable()` so `onKeyEvent` actually receives events.
 *  - Consumes only KeyUp for ENTER/CENTER to avoid double-firing with `clickable`.
 *  - Uses `rememberUpdatedState` so callers don't need to remember the lambda.
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
