package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
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
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.theme.PrimeGold

// ─────────────────────────────────────────────────────────────
// Building blocks shared by the live and VOD players.
// They only draw things — no player logic lives here.
// ─────────────────────────────────────────────────────────────

/**
 * Round control over the video: dark glass at rest, solid white with a dark
 * icon when focused (a clear cue from across the room on TV).
 */
@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    tint: Color = Color.White,
    focusRequester: FocusRequester? = null
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        containerColor = Color.Black.copy(alpha = 0.45f),
        focusedContainerColor = Color.White,
        focusScale = if (AppTheme.dimens.focusScale > 1f) 1.12f else 1f,
        showRing = false,
        focusRequester = focusRequester
    ) { focused ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Color.Black else tint,
            modifier = Modifier.size(iconSize)
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

/** Centered error card with a Retry button (auto-focused on TV). */
@Composable
fun PlaybackErrorPanel(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    cause: String? = null
) {
    val scheme = MaterialTheme.colorScheme
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
 * Seek bar: a buffer track, plus a draggable slider ONLY when [seekable].
 *
 * Pure live streams pass `seekable = false` — dragging on a live window
 * would jump to the DVR start (rewinding the viewer away from the live
 * edge), so only the passive buffer indicator is drawn.
 *
 * The component never touches the player: dragging calls
 * [onSeekTo] with a position in milliseconds and the screen decides what
 * to do (usually `player.seekTo(ms)`).
 */
@Composable
fun PlayerSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    seekable: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bufferFraction = if (durationMs > 0)
            (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

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

        if (seekable && durationMs > 0) {
            Slider(
                value = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f),
                onValueChange = { fraction -> onSeekTo((fraction * durationMs).toLong()) },
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

/** 83_000 ms → "01:23". Non-positive → "00:00". */
fun formatPlayerTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
