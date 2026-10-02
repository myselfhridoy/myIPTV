package com.streambd.iptv.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.TvFocusedBorder
import com.streambd.iptv.utils.DeviceType

/**
 * Focus decoration modifier.
 *   • TV  → cyan border + subtle scale on focus (D-pad users need this).
 *   • Mobile → no-op (touch has no focus ring; keeps UI clean).
 */
fun Modifier.tvFocusBorder(
    shape: Shape = RoundedCornerShape(8.dp),
    focusScale: Float = 1.05f
): Modifier = composed {
    val context = LocalContext.current
    val isTv = remember { DeviceType.isTv(context) }
    if (!isTv) return@composed this

    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = tween(200),
        label = "tvFocusScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) TvFocusedBorder else Color.Transparent,
        animationSpec = tween(200),
        label = "tvFocusBorderColor"
    )

    this
        .onFocusChanged { isFocused = it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (isFocused) Modifier.border(2.dp, borderColor, shape)
            else Modifier
        )
}

/**
 * TV icon button.
 *   • TV  → scale + tinted bg + cyan border on focus.
 *   • Mobile → clean, no focus ring.
 *
 * Still fully functional on both:
 *   • D-pad ENTER / DPAD_CENTER fires onClick (via tvTouchClick)
 *   • Tap fires onClick
 */
@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isTv = remember { DeviceType.isTv(context) }

    var isFocused by remember { mutableStateOf(false) }
    val showFocus = isTv && isFocused

    val scale by animateFloatAsState(
        targetValue = if (showFocus) 1.08f else 1f,
        animationSpec = tween(180),
        label = "tvIconScale"
    )
    val bg by animateColorAsState(
        targetValue = if (showFocus) TvFocusedBorder.copy(alpha = 0.22f)
        else Color.Transparent,
        animationSpec = tween(180),
        label = "tvIconBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (showFocus) TvFocusedBorder else Color.Transparent,
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
            .then(
                if (showFocus) Modifier.border(2.dp, borderColor, CircleShape)
                else Modifier
            )
            .tvTouchClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Tap + D-pad ENTER handler.
 *   • Adds `focusable()` so D-pad focus can land here.
 *   • Consumes KeyUp for ENTER/CENTER (no double-fire).
 *   • Uses `rememberUpdatedState` so callers don't need to remember the lambda.
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
