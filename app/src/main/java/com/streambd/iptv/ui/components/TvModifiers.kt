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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.streambd.iptv.ui.theme.TvFocusedBorder
import com.streambd.iptv.utils.DeviceType

/**
 * Focus decoration modifier for TV (mobile: no-op).
 *
 * FIX: Border was applied via `if (isFocused) Modifier.border(...)` which made
 * the border disappear instantly (before its color animation could finish).
 * Now the border is ALWAYS applied; only its color animates. When unfocused
 * the color is `Color.Transparent`, so it's visually identical to no border.
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
        // Border always present — animated color handles the transition.
        .border(2.dp, borderColor, shape)
}

/**
 * TV icon button (mobile: no focus ring).
 *
 * FIXES:
 *   • Border is now always applied (animated color) — no more vanishing.
 *   • `zIndex` on focus so the scaled button renders above neighbours.
 *   • `contentDescription` parameter sets accessibility semantics explicitly,
 *     instead of relying on the caller's icon() lambda.
 */
@Composable
fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
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
            // Focused item renders above neighbours — important because
            // the 1.08x scale can otherwise be clipped by adjacent items.
            .zIndex(if (showFocus) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(CircleShape)
            .background(bg, CircleShape)
            // Border always present; only its color animates.
            .border(2.dp, borderColor, CircleShape)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            )
            .tvTouchClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Tap + D-pad ENTER handler — unchanged for now.
 * (A future iteration may switch to Modifier.clickable for accessibility.)
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
