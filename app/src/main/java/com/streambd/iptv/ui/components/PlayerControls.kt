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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.theme.PrimeGold
import com.streambd.iptv.ui.theme.PrimeLive

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

@Composable
fun PlayerSeekBar(
    fraction: Float,
    bufferFraction: Float,
    seekable: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
    val isLive = showLiveLabel && progress.isLive

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val playbackFraction =
        if (duration > 0) (progress.position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferFraction =
        if (duration > 0) (progress.buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = if (dragging) (dragFraction * duration).toLong() else progress.position

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isLive) {
            LiveLabel()
        } else {
            Text(
                text = formatPlayerTime(shownPosition),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
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
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )

        Text(
            text = if (isLive) "00:00" else formatPlayerTime(duration),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium
        )
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
