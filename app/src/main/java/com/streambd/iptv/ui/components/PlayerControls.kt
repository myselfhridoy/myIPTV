package com.streambd.iptv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Fixed height of the whole timeline row. Never changes => nothing can jump. */
private val TimelineRowHeight = 48.dp

/**
 * On a live screen, any media shorter than this window is treated as LIVE.
 * (HLS often reports a tiny fake duration like 00:24 while loading.)
 */
private const val LIVE_WINDOW_MAX_MS = 10 * 60 * 1000L

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
 * A completely custom Seek Bar drawn from scratch.
 * This completely avoids Material3 Slider's internal padding and top-alignment issues,
 * ensuring the thumb ring, buffer bar, and active progress are all perfectly centered.
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
    val density = LocalDensity.current
    val thumbRadius = 7.dp
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .height(TimelineRowHeight)
            .then(
                if (seekable) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val f = ((offset.x - thumbRadiusPx) / (size.width - 2 * thumbRadiusPx))
                                    .coerceIn(0f, 1f)
                                onValueChange(f)
                                onValueChangeFinished()
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val f = ((offset.x - thumbRadiusPx) / (size.width - 2 * thumbRadiusPx))
                                        .coerceIn(0f, 1f)
                                    onValueChange(f)
                                },
                                onDragEnd = { onValueChangeFinished() },
                                onDragCancel = { onValueChangeFinished() }
                            ) { change, _ ->
                                change.consume()
                                val f = ((change.position.x - thumbRadiusPx) / (size.width - 2 * thumbRadiusPx))
                                    .coerceIn(0f, 1f)
                                onValueChange(f)
                            }
                        }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val barWidthPx = constraints.maxWidth

        // 1. Track Background (Grey)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = thumbRadius)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.3f))
        )

        // 2. Buffer Track (Light Grey)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = thumbRadius)
                .height(4.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }

        // 3. Active Progress (Blue)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = thumbRadius)
                .height(4.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(scheme.primary)
            )
        }

        // 4. Thumb (Ring)
        if (barWidthPx > 0) {
            val trackWidth = barWidthPx - 2 * thumbRadiusPx
            val thumbCenterX = thumbRadiusPx + fraction.coerceIn(0f, 1f) * trackWidth
            val thumbX = thumbCenterX - thumbRadiusPx

            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbX.toInt(), 0) }
                    .size(thumbRadius * 2)
                    .border(
                        width = 2.dp,
                        color = if (seekable) Color.White else Color.White.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            )
        }

        // 5. Tooltip (During Drag)
        if (showTooltip && tooltipText != null && seekable && barWidthPx > 0) {
            val trackWidth = barWidthPx - 2 * thumbRadiusPx
            val thumbCenterX = thumbRadiusPx + fraction.coerceIn(0f, 1f) * trackWidth
            val tooltipWidthPx = with(density) { 56.dp.toPx() }
            val xOffset = (thumbCenterX - tooltipWidthPx / 2)
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
                    .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
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

    val isLive = showLiveLabel &&
        (progress.isLive || !seekable || duration <= 0L || duration < LIVE_WINDOW_MAX_MS)

    val timeKnown = isLive || duration > 0L
    val canSeek = seekable && !isLive && duration > 0L

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val rawPlaybackFraction =
        if (duration > 0) (progress.position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val rawBufferFraction =
        if (duration > 0) (progress.buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val animate = !dragging

    val playbackFraction by animateFloatAsState(
        targetValue = if (dragging) dragFraction else rawPlaybackFraction,
        animationSpec = if (animate) tween(300, easing = FastOutSlowInEasing) else snap(),
        label = "playbackFraction"
    )
    val bufferFraction by animateFloatAsState(
        targetValue = rawBufferFraction,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "bufferFraction"
    )

    val shownPosition = if (dragging) (dragFraction * duration).toLong() else progress.position

    Row(
        modifier = modifier.height(TimelineRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .alpha(if (timeKnown) 1f else 0f),
            contentAlignment = Alignment.Center
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
            fraction = playbackFraction,
            bufferFraction = bufferFraction,
            seekable = canSeek,
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
                .padding(horizontal = 12.dp)
        )

        Box(
            modifier = Modifier
                .width(64.dp)
                .alpha(if (timeKnown) 1f else 0f),
            contentAlignment = Alignment.Center
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
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

fun formatPlayerTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}

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
