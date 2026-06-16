package com.streambd.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.TealAccent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType

/**
 * A custom modifier that adds a TealAccent border when the component is focused.
 * Upgraded to Bioscope Premium scale, border glow, and shadow.
 */
fun Modifier.tvFocusBorder(shape: Shape = RoundedCornerShape(8.dp)): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f, tween(250), label = "scale")
    val glowColor by animateColorAsState(if (isFocused) BioAccent else Color.Transparent, tween(250), label = "glowColor")
    val elevation by animateDpAsState(if (isFocused) 12.dp else 0.dp, tween(250), label = "elevation")

    this
        .onFocusChanged { isFocused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = elevation.toPx()
            this.shape = shape
            clip = true
        }
        .border(
            width = if (isFocused) 3.dp else 0.dp,
            color = glowColor,
            shape = shape
        )
}

/**
 * A specialized IconButton for Android TV that shows a focus ring.
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
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) TealAccent else Color.Transparent,
                shape = CircleShape
            )
    ) {
        icon()
    }
}

/**
 * A modifier that bypasses TV component restrictions to manually capture
 * pointer tap events for mobile support AND properly handles D-Pad Enter for TV.
 */
fun Modifier.tvTouchClick(onClick: () -> Unit): Modifier = composed {
    val currentOnClick by androidx.compose.runtime.rememberUpdatedState(onClick)
    this.then(
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnClick() })
            }
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.DirectionCenter || it.key == Key.NumPadEnter)) {
                    currentOnClick()
                    true
                } else {
                    false
                }
            }
    )
}
