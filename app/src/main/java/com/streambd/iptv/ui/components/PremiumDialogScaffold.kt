package com.streambd.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import com.streambd.iptv.ui.theme.TvFocusedBorder
import com.streambd.iptv.ui.theme.WhiteText

/**
 * Returns `true` when the current screen is a phone-sized window.
 * Threshold: 600dp is Android's standard phone/tablet breakpoint
 * (matches `WindowSizeClass.Compact`).
 */
@Composable
fun rememberIsCompactLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp < 600
}

/**
 * Shared shell for every dialog.
 *
 * Responsive:
 *  - On phones (<600dp wide) the dialog shrinks: narrower width, tighter
 *    paddings, smaller title, capped content height — so it doesn't dominate
 *    the screen.
 *  - On TV / tablet (>=600dp) it keeps the original premium size.
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
    val isCompact = rememberIsCompactLayout()

    // ── Responsive dimensions ──
    val effectiveMaxWidth = if (isCompact) minOf(maxWidth, 380.dp) else maxWidth
    val effectiveContentMaxHeight =
        if (isCompact) minOf(contentMaxHeight, 300.dp) else contentMaxHeight
    val outerPadding = if (isCompact) 12.dp else 16.dp
    val headerStart = if (isCompact) 16.dp else 20.dp
    val headerEnd = if (isCompact) 16.dp else 20.dp
    val headerTop = if (isCompact) 14.dp else 18.dp
    val headerBottom = if (isCompact) 10.dp else 12.dp
    val accentWidth = if (isCompact) 3.dp else 4.dp
    val accentHeight = if (isCompact) 22.dp else 26.dp
    val titleStyle = if (isCompact)
        MaterialTheme.typography.titleMedium
    else
        MaterialTheme.typography.titleLarge

    val shape = RoundedCornerShape(if (isCompact) 16.dp else 20.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = modifier
                .padding(outerPadding)
                .widthIn(max = effectiveMaxWidth)
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(BioCardLight, DarkNavySurface)))
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = headerStart,
                        end = headerEnd,
                        top = headerTop,
                        bottom = headerBottom
                    ),
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

            // ── Content ──
            val contentModifier = if (scrollContent)
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = effectiveContentMaxHeight)
                    .verticalScroll(rememberScrollState())
            else Modifier.fillMaxWidth()

            Column(modifier = contentModifier, content = content)

            // ── Footer ──
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
                if (actions != null) actions() else PremiumButton("Close", onDismiss)
            }
        }
    }
}

/**
 * Focusable base surface for TV + touch.
 * Uses `tvTouchClick` (focusable + D-pad ENTER + tap) instead of raw `clickable`.
 */
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
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(180),
        label = "focusScale"
    )
    val background by animateColorAsState(
        targetValue = if (isFocused) BioAccent.copy(alpha = 0.14f) else restBackground,
        animationSpec = tween(180),
        label = "focusBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) TvFocusedBorder else Color.Transparent,
        animationSpec = tween(180),
        label = "focusBorder"
    )

    Row(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(shape)
            .background(background)
            .border(2.dp, borderColor, shape)
            .tvTouchClick(onClick)
            .padding(contentPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** One selectable row: title, optional subtitle, optional badge, check mark when selected. */
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
