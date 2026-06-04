package com.streambd.iptv.ui.components

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
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.TealAccent

/**
 * A custom modifier that adds a TealAccent border when the component is focused.
 * This is essential for Android TV / D-Pad navigation visibility.
 */
fun Modifier.tvFocusBorder(shape: Shape = RoundedCornerShape(8.dp)): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { isFocused = it.isFocused }
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) TealAccent else Color.Transparent,
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
