package com.streambd.iptv.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.components.PlayerIconButton
import com.streambd.iptv.ui.components.PlayerProgress
import com.streambd.iptv.ui.components.PlayerSeekBar
import com.streambd.iptv.ui.components.formatPlayerTime
import com.streambd.iptv.ui.components.PlayerScrim
import com.streambd.iptv.ui.theme.PrimeLive

/**
 * Controls drawn over the live player. Pure UI: everything the user can do
 * is a callback, and all player state comes in as plain values — the screen
 * keeps owning ExoPlayer, focus requesters and every side effect.
 */
@Composable
internal fun LivePlayerOverlay(
    channelName: String,
    isLocked: Boolean,
    isTvModeEnabled: Boolean,
    hasSources: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    progress: PlayerProgress,
    isMediaSeekable: () -> Boolean,
    playPauseFocusRequester: FocusRequester,
    unlockFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    onTurnOffTvMode: () -> Unit,
    onOpenSources: () -> Unit,
    onLock: () -> Unit,
    onCycleAspect: () -> Unit,
    onEnterPiP: () -> Unit,
    onOpenTracks: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        PlayerScrim()

        if (isLocked) {
            PlayerIconButton(
                icon = Icons.Default.Lock,
                contentDescription = "Unlock",
                onClick = onUnlock,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                focusRequester = unlockFocusRequester
            )
        } else {

            // ── Top bar ──
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = channelName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isTvModeEnabled) {
                        PlayerIconButton(
                            icon = Icons.Default.PowerSettingsNew,
                            contentDescription = "Turn Off TV Mode",
                            onClick = onTurnOffTvMode,
                            tint = scheme.primary
                        )
                    }
                    if (hasSources) {
                        PlayerIconButton(Icons.Default.Dns, "Sources", onOpenSources)
                    }
                    PlayerIconButton(Icons.Default.Lock, "Lock", onLock)
                    PlayerIconButton(Icons.Default.AspectRatio, "Aspect Ratio", onCycleAspect)
                    PlayerIconButton(Icons.Default.PictureInPicture, "PiP", onEnterPiP)
                    PlayerIconButton(Icons.Default.Settings, "Tracks", onOpenTracks)
                }
            }

            // ── Centre: prev · play/pause · next ──
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Prev Channel",
                    onClick = onPrev,
                    size = 48.dp,
                    iconSize = 28.dp
                )
                PlayerIconButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    onClick = onTogglePlay,
                    size = 68.dp,
                    iconSize = 38.dp,
                    focusRequester = playPauseFocusRequester
                )
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Channel",
                    onClick = onNext,
                    size = 48.dp,
                    iconSize = 28.dp
                )
            }

            // ── Bottom bar ──
            // `progress` is read HERE (not in the screen), so only this block
            // recomposes while the position ticks.
            val isLiveStream = progress.isLive
            val seekable = !isLiveStream && isMediaSeekable()

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLiveStream) {
                    LiveLabel()
                } else {
                    Text(
                        text = formatPlayerTime(progress.position),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                PlayerSeekBar(
                    positionMs = progress.position,
                    bufferedMs = progress.buffered,
                    durationMs = progress.duration,
                    seekable = seekable,
                    onSeekTo = onSeekTo,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )

                Text(
                    text = if (isLiveStream) "00:00" else formatPlayerTime(progress.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.width(12.dp))
                PlayerIconButton(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                    else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    onClick = onToggleMute
                )
            }
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
