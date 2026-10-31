package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Shared shell for every dialog.
 *
 * Public API is UNCHANGED (all existing call sites compile as-is).
 *
 * When [LocalPlayerIsRotated] is true the dialog renders INLINE as a
 * full-screen overlay instead of a separate Dialog window. (Today that local
 * is always false — kept for backwards compatibility.)
 *
 * NEW — TV focus safety net: on a TV, if 300 ms after the dialog opened no
 * element inside it holds focus (the caller forgot to request one), focus is
 * moved to the first focusable child. A TV user can therefore never be left
 * with a dialog that ignores the D-pad.
 */
@Composable
fun PremiumDialogScaffold(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    maxWidth: Dp = 560.dp,
    contentMaxHeight: Dp = 420.dp,
    scrollContent: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isPlayerRotated = LocalPlayerIsRotated.current

    if (isPlayerRotated) {
        // Inline overlay — inherits rotation from PlayerRevealContainer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Swallow taps inside the dialog card so clicking it doesn't dismiss.
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* no-op */ }
            ) {
                PremiumDialogCard(
                    title = title,
                    subtitle = subtitle,
                    modifier = modifier,
                    maxWidth = maxWidth,
                    contentMaxHeight = contentMaxHeight,
                    scrollContent = scrollContent,
                    autoFocusFallback = false,
                    actions = actions,
                    content = content
                )
            }
        }
    } else {
        // Standard Dialog window (mobile dialogs, non-player contexts).
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PremiumDialogCard(
                title = title,
                subtitle = subtitle,
                modifier = modifier,
                maxWidth = maxWidth,
                contentMaxHeight = contentMaxHeight,
                scrollContent = scrollContent,
                autoFocusFallback = true,
                actions = actions,
                content = content
            )
        }
    }
}

/** The visual card — same in both paths. */
@Composable
private fun PremiumDialogCard(
    title: String,
    subtitle: String?,
    modifier: Modifier,
    maxWidth: Dp,
    contentMaxHeight: Dp,
    scrollContent: Boolean,
    autoFocusFallback: Boolean,
    actions: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val config = LocalConfiguration.current
    val isCompact = config.screenWidthDp < 600
    val isShortScreen = config.screenHeightDp < 480

    val effectiveMaxWidth = if (isCompact) minOf(maxWidth, 380.dp) else maxWidth
    val outerPadding = when {
        isCompact && isShortScreen -> 6.dp
        isCompact -> 12.dp
        else -> 16.dp
    }
    val headerHorizontal = if (isCompact) 16.dp else 20.dp
    val headerTop = if (isCompact) 14.dp else 18.dp
    val headerBottom = if (isCompact) 8.dp else 12.dp
    val titleStyle = if (isCompact)
        MaterialTheme.typography.titleMedium
    else
        MaterialTheme.typography.titleLarge

    val chromeHeight = when {
        isCompact && isShortScreen -> 110.dp
        isCompact -> 150.dp
        else -> 180.dp
    }
    val screenBoundedContent = (config.screenHeightDp.dp - chromeHeight)
        .coerceAtLeast(100.dp)
    val effectiveContentMaxHeight = minOf(
        if (isCompact) minOf(contentMaxHeight, 320.dp) else contentMaxHeight,
        screenBoundedContent
    )

    val shape = MaterialTheme.shapes.large

    // ── TV focus safety net ──────────────────────────────────
    val isTv = rememberIsTv()
    val focusManager = LocalFocusManager.current
    var hasFocus by remember { mutableStateOf(false) }
    if (autoFocusFallback && isTv) {
        LaunchedEffect(Unit) {
            delay(300)
            if (!hasFocus) {
                // FocusDirection.Next is stable API (Enter is experimental).
                if (!focusManager.moveFocus(FocusDirection.Next)) {
                    focusManager.moveFocus(FocusDirection.Down)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .padding(outerPadding)
            .widthIn(max = effectiveMaxWidth)
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surface)
            .border(1.dp, scheme.outline.copy(alpha = 0.5f), shape)
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusGroup()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = headerHorizontal,
                    end = headerHorizontal,
                    top = headerTop,
                    bottom = headerBottom
                )
        ) {
            Text(
                text = title,
                color = scheme.onSurface,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val contentModifier = if (scrollContent)
            Modifier
                .fillMaxWidth()
                .heightIn(max = effectiveContentMaxHeight)
                .verticalScroll(rememberScrollState())
        else Modifier.fillMaxWidth()

        Column(modifier = contentModifier, content = content)

        if (actions != null) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = scheme.outline.copy(alpha = 0.4f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isCompact) 10.dp else 14.dp,
                        vertical = if (isCompact) 8.dp else 10.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}
