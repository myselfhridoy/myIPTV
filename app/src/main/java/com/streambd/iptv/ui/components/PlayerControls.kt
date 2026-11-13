package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.theme.PrimeGold
import com.streambd.iptv.ui.theme.PrimeLive

/**
 * Round control over the video.
 *
 * PHONE / TABLET (premium, light): no background at rest — just the icon;
 * the centre play button can opt in to a soft glass disc with `glass = true`.
 * TV (unchanged look): every control sits on a dark glass disc, bigger.
 * Focus: solid white disc with a dark icon (clear from across the room).
 */
@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    iconSize: Dp = Dp.Unspecified,
    tint: Color = Color.White,
    focusRequester: FocusRequester? = null,
    glass: Boolean? = null
) {
    val tv = LocalDeviceProfile.current.isTv
    val buttonSize = if (size != Dp.Unspecified) size else if (tv) 44.dp else 40.dp
    val glyphSize = if (iconSize != Dp.Unspecified) iconSize else if (tv) 24.dp else 22.dp
    val showGlass = glass ?: tv

    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(buttonSize),
        shape = CircleShape,
        containerColor = if (showGlass) Color.Black.copy(alpha = 0.45f) else Color.Transparent,
        focusedContainerColor = Color.White,
        focusScale = if (AppTheme.dimens.focusScale > 1f) 1.12f else 1f,
        showRing = false,
        focusRequester = focusRequester
    ) { focused ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Color.Black else tint,
            modifier = Modifier.size(glyphSize)
        )
    }
}

/** Soft top/bottom darkening so controls stay readable on any picture. */
@Composable
fun PlayerScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.75f),
                        0.3f to Color.Black.copy(alpha = 0.3f),
                        0.7f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
    )
}

/**
 * Centered error card with a Retry button (auto-focused on TV).
 *
 * [compact] — for the small 16:9 video area of the portrait player:
 * single-row layout that fits inside ~200dp of height, so the Retry
 * button is never clipped when the video area is short.
 */
@Composable
fun PlaybackErrorPanel(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    cause: String? = null,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme

    if (compact) {
        Row(
            modifier = modifier
                .widthIn(max = 300.dp)
                .background(Color.Black.copy(alpha = 0.82f), MaterialTheme.shapes.medium)
                .border(1.dp, scheme.outline.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Playback error",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            AppButton(
                text = "Retry",
                onClick = onRetry,
                size = AppButtonSize.Compact,
                modifier = Modifier.initialFocus()
            )
        }
        return
    }

    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .background(Color.Black.copy(alpha = 0.82f), MaterialTheme.shapes.large)
            .border(1.dp, scheme.outline.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = scheme.error,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Playback Error",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        if (!hint.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                color = PrimeGold,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
        if (!cause.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = cause,
                color = Color.LightGray,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(16.dp))
        AppButton(
            text = "Retry",
            onClick = onRetry,
            icon = Icons.Default.Refresh,
            modifier = Modifier.initialFocus()
        )
    }
}

/** Banner shown for a few seconds after switching channel. */
@Composable
fun ChannelInfoCard(
    name: String,
    group: String,
    logoUrl: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f), MaterialTheme.shapes.medium)
            .border(1.dp, Color.White.copy(alpha = 0.08f), MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!logoUrl.isNullOrEmpty()) {
            RemoteImage(
                url = logoUrl,
                label = name,
                describe = false,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.width(14.dp))
        }
        Column {
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (group.isNotEmpty()) {
                Text(
                    text = group,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Digits typed on the remote while "dialling" a channel number. */
@Composable
fun ChannelNumberBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f), MaterialTheme.shapes.medium)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Low-level bar: a buffer track plus a draggable slider ONLY when
 * [seekable].
 *
 * NEW: [showTooltip] + [tooltipText] — when dragging the thumb, a small
 * floating time badge appears above the thumb so the user can preview
 * the target position before releasing.
 */
@Composable
fun PlayerSeekBar(
    fraction: Float,
    bufferFraction: Float,
    seekable: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    showTooltip: Boolean = false,
    tooltipText: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = modifier) {
        val barWidthPx = constraints.maxWidth

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            LinearProgressIndicator(
                progress = { bufferFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                color = Color.White.copy(alpha = 0.4f),
                trackColor = Color.Gray.copy(alpha = 0.3f)
            )
            if (seekable) {
                Slider(
                    value = fraction,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = scheme.primary,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }
        }

        // ── Time tooltip above the thumb (drag only) ──
        if (showTooltip && tooltipText != null && seekable && barWidthPx > 0) {
            val thumbX = fraction * barWidthPx
            val density = LocalDensity.current
            val tooltipWidthPx = with(density) { 56.dp.toPx() }
            val xOffset = (thumbX - tooltipWidthPx / 2)
                .coerceIn(0f, (barWidthPx - tooltipWidthPx).coerceAtLeast(0f))

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = xOffset.toInt(),
                            y = (-46).dp.roundToPx()
                        )
                    }
                    .width(56.dp)
                    .background(
                        Color.Black.copy(alpha = 0.9f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tooltipText,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Time labels + seek bar.
 *
 * [commitOnRelease]
 *  • false (live) — every slider move calls [onSeekTo] straight away
 *  • true  (VOD)  — dragging only previews the time; [onSeekTo] is called
 *    ONCE when the finger lifts, and the polled position is held
 *    ([PlayerProgress.holdPosition]) so the thumb doesn't jump while dragging
 *
 * [showLiveLabel] — show "LIVE" instead of the position when the duration
 * is unknown (live channels).
 */
@Composable
fun PlayerTimeline(
    progress: PlayerProgress,
    seekable: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    commitOnRelease: Boolean = false,
    showLiveLabel: Boolean = false
) {
    val duration = progress.duration
    val isLive = showLiveLabel && progress.isLive

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val playbackFraction =
        if (duration > 0) (progress.position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferFraction =
        if (duration > 0) (progress.buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = if (dragging) (dragFraction * duration).toLong() else progress.position

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Left slot — fixed width so a channel switch (LIVE badge → time text)
        // never shifts the seek bar's position.
        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isLive) {
                LiveLabel()
            } else {
                Text(
                    text = formatPlayerTime(shownPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }

        PlayerSeekBar(
            fraction = if (dragging) dragFraction else playbackFraction,
            bufferFraction = bufferFraction,
            seekable = seekable && duration > 0,
            onValueChange = { value ->
                if (commitOnRelease) {
                    dragging = true
                    progress.holdPosition = true
                    dragFraction = value
                } else {
                    onSeekTo((value * duration).toLong())
                }
            },
            onValueChangeFinished = {
                if (commitOnRelease && dragging) {
                    if (duration > 0) {
                        val target = (dragFraction * duration).toLong()
                        onSeekTo(target)
                        progress.overridePosition(target)
                    }
                    dragging = false
                    progress.holdPosition = false
                }
            },
            showTooltip = dragging && duration > 0,
            tooltipText = if (dragging && duration > 0) formatPlayerTime(shownPosition) else null,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)  // reduced from 16dp — the fixed Box
                                              // widths already add visual spacing
        )

        // Right slot — same fixed width. Prevents the layout jump when
        // duration goes from unknown ("00:00") to a real value like
        // "1:02:05", and keeps things stable on VOD too.
        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = if (isLive) "00:00" else formatPlayerTime(duration),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LiveLabel() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(PrimeLive)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "LIVE",
            color = PrimeLive,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 83_000 ms → "01:23"; 3_725_000 ms → "1:02:05". Non-positive → "00:00". */
fun formatPlayerTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

/**
 * Elapsed seconds since [active] became true. Resets to 0 the moment
 * it goes false — so each retry / phase transition starts a fresh clock.
 */
@Composable
fun rememberLoadingElapsed(active: Boolean): Int {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(active) {
        seconds = 0
        if (active) {
            while (isActive) {
                delay(1000)
                seconds++
            }
        }
    }
    return seconds
}

/**
 * Spinner for "resolving / buffering" that does not flicker.
 *
 * [forceHide] — when true, hides IMMEDIATELY (skipping the 150ms exit
 * delay). Used when the player is confirmed READY + PLAYING, so the user
 * never sees the spinner over already-playing video.
 */
@Composable
fun PlayerLoadingIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onRetry: (() -> Unit)? = null,
    forceHide: Boolean = false
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible, forceHide) {
        if (forceHide) {
            show = false
        } else if (visible) {
            delay(200)
            show = true
        } else {
            delay(150)
            show = false
        }
    }
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(200))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = subtitle,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.65f),
                            MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            if (onRetry != null) {
                Spacer(Modifier.height(14.dp))
                AppButton(
                    text = "Retry",
                    onClick = onRetry,
                    size = AppButtonSize.Compact,
                    modifier = Modifier.initialFocus()
                )
            }
        }
    }
}
