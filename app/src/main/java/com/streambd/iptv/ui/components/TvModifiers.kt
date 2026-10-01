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
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.TvFocusedBorder

/**
 * Focus decoration modifier for TV + touch.
 *
 * NOTE: Now intentionally VISUAL-FREE on both TV and mobile.
 *   • No scale animation.
 *   • No border.
 *   • No shadow elevation.
 * Kept as a callable no-op so existing call sites continue to compile —
 * but if you want to fully remove it later, simply delete all usages.
 *
 * The element is still focusable (D-pad navigation works) because the
 * `tvTouchClick` inside the button provides the `focusable()` modifier.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.tvFocusBorder(
    shape: Shape = RoundedCornerShape(8.dp),
    focusScale: Float = 1.05f
): Modifier = this
// ↑ no-op: no visual focus ring anywhere.

/**
 * TV icon button — no focus ring, still fully functional.
 *
 *   • D-pad navigation still moves focus into the button.
 *   • ENTER / CENTER on the remote still fires `onClick` (via `tvTouchClick`).
 *   • Tap works on mobile.
 *   • `defaultMinSize` preserves the 48dp touch target.
 */
@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .tvTouchClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Tap + D-pad ENTER handler.
 *
 *  - Adds `Modifier.focusable()` so `onKeyEvent` actually receives events and
 *    so D-pad focus can land on the element at all.
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
