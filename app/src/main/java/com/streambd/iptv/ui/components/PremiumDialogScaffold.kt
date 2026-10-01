package com.streambd.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.BioCardLight
import com.streambd.iptv.ui.theme.DarkNavySurface
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.WhiteText

@Composable
fun rememberIsCompactLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp < 600
}

/**
 * Shared shell for every dialog.
 *
 * When [LocalPlayerIsRotated] is true (i.e. we're inside the rotated player
 * container), this renders INLINE as a full-screen Box overlay instead of a
 * separate Dialog window — so the dialog inherits the container's 90° rotation
 * and appears landscape like the player.
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
    actions: (@Composable RowScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    val config = LocalConfiguration.current
    val isCompact = config.screenWidthDp < 600
    val isShortScreen = config.screenHeightDp < 480

    val effectiveMaxWidth = if (isCompact) minOf(maxWidth, 380.dp) else maxWidth
    val outerPadding = when {
        isCompact && isShortScreen -> 6.dp
        isCompact -> 12.dp
        else -> 16.dp
    }
    val headerStart = if (isCompact) 16.dp else 20.dp
    val headerEnd = if (isCompact) 16.dp else 20.dp
    val headerTop = if (isCompact) 12.dp else 18.dp
    val headerBottom = if (isCompact) 8.dp else 12.dp
    val accentWidth = if (isCompact) 3.dp else 4.dp
    val accentHeight = if (isCompact) 22.dp else 26.dp
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

    val shape = RoundedCornerShape(if (isCompact) 16.dp else 20.dp)

    Column(
        modifier = modifier
            .padding(outerPadding)
            .widthIn(max = effectiveMaxWidth)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(BioCardLight, DarkNavySurface)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = headerStart, end = headerEnd, top = headerTop, bottom = headerBottom),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(accentWidth)
                    .height(accentHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BioAccent)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WhiteText,
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = GrayText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                color = Color.White.copy(alpha = 0.06f)
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

// ─────────────────────────────────────────────────────────────
// Rest of the file — TvFocusableItem, PremiumOptionRow, PremiumBadge, PremiumButton
// (these stay the same as your current file — just re-use your existing code)
// ─────────────────────────────────────────────────────────────

@Composable
fun TvFocusableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    restBackground: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = if (isFocused) BioAccent.copy(alpha = 0.14f) else restBackground,
        animationSpec = tween(180),
        label = "focusBg"
    )

    Row(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(shape)
            .background(background)
            .tvTouchClick(onClick)
            .padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun PremiumOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    focusRequester: FocusRequester? = null
) {
    val isCompact = rememberIsCompactLayout()
    val rowPadding = if (isCompact)
        PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    else
        PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    val titleStyle = if (isCompact)
        MaterialTheme.typography.bodyLarge
    else
        MaterialTheme.typography.titleMedium

    TvFocusableItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        contentPadding = rowPadding,
        restBackground = if (selected) BioAccent.copy(alpha = 0.08f) else Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (isCompact) 22.dp else 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) BioAccent else Color.Transparent)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (selected) BioAccent else WhiteText,
                style = titleStyle,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = GrayText.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (badge != null) {
            PremiumBadge(badge)
            Spacer(Modifier.width(8.dp))
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = BioAccent,
                modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
            )
        }
    }
}

@Composable
fun PremiumBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = BioAccent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BioAccent.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val isCompact = rememberIsCompactLayout()
    val padding = if (isCompact)
        PaddingValues(horizontal = 18.dp, vertical = 8.dp)
    else
        PaddingValues(horizontal = 22.dp, vertical = 10.dp)

    TvFocusableItem(
        onClick = onClick,
        modifier = modifier,
        focusRequester = focusRequester,
        shape = RoundedCornerShape(10.dp),
        restBackground = if (primary) BioAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
        contentPadding = padding,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            color = if (primary) BioAccent else WhiteText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
