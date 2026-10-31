package com.streambd.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.zIndex
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.theme.PrimeFocusRing

/**
 * THE one clickable primitive for the whole app.
 *
 * Every button, card and chip is built on this, so touch, D-pad,
 * keyboard and TalkBack behave the same everywhere:
 *
 *  • Touch      → ripple + click (+ optional long-press)
 *  • D-pad/OK   → focusable; ENTER / DPAD_CENTER trigger [onClick]
 *  • Focus look → animated ring, scale-up (TV only, via Dimens), and
 *                 the item is drawn above its neighbours (zIndex)
 *  • Semantics  → proper [role], so TalkBack can announce & activate it
 *
 * [content] receives `focused` so children (text/icon colours) can react.
 * [onFocused] fires each time the item gains focus — use it to scroll,
 * update a hero banner, or remember the last focused item.
 *
 * NOTE: when this sits inside a LazyRow/LazyColumn, give the list a bit
 * of contentPadding — otherwise the scaled/ringed item is clipped at the
 * list edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    focusedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    focusScale: Float = AppTheme.dimens.focusScale,
    showRing: Boolean = true,
    role: Role = Role.Button,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val currentOnFocused by rememberUpdatedState(onFocused)
    LaunchedEffect(isFocused) {
        if (isFocused) currentOnFocused?.invoke()
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = tween(160),
        label = "focusScale"
    )
    val background by animateColorAsState(
        targetValue = if (isFocused) focusedContainerColor else containerColor,
        animationSpec = tween(160),
        label = "focusContainer"
    )
    val ringColor by animateColorAsState(
        targetValue = if (isFocused && showRing) PrimeFocusRing else Color.Transparent,
        animationSpec = tween(160),
        label = "focusRing"
    )
    val ringWidth = AppTheme.dimens.focusBorderWidth

    Box(
        modifier = modifier
            // focusRequester must come BEFORE the focusable node below.
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clip(shape)
            .background(background)
            .border(ringWidth, ringColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = role,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = contentAlignment
    ) {
        content(isFocused)
    }
}
