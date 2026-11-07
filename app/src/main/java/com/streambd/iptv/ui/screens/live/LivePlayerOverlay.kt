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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import com.streambd.iptv.ui.components.PlayerTimeline
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
    modifier: Modifier = Modifier,
    /** Portrait player: the video area is small → fewer, smaller controls. */
    compact: Boolean = false,
    /** Phones with the portrait player: show a fullscreen / exit-fullscreen button. */
    showFullscreenButton: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
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
                    .padding(
                        horizontal = if (compact) 8.dp else 16.dp,
                        vertical = if (compact) 6.dp else 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
                Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
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
                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
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
                    // Lock and aspect ratio only make sense in fullscreen.
                    if (!compact) {
                        PlayerIconButton(Icons.Default.Lock, "Lock", onLock)
                        PlayerIconButton(Icons.Default.AspectRatio, "Aspect Ratio", onCycleAspect)
                    }
                    PlayerIconButton(Icons.Default.PictureInPicture, "PiP", onEnterPiP)
                    PlayerIconButton(Icons.Default.Settings, "Tracks", onOpenTracks)
                }
            }

            // ── Centre: prev · play/pause · next ──
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Prev Channel",
                    onClick = onPrev,
                    size = if (compact) 40.dp else 48.dp,
                    iconSize = if (compact) 24.dp else 28.dp
                )
                PlayerIconButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    onClick = onTogglePlay,
                    size = if (compact) 52.dp else 68.dp,
                    iconSize = if (compact) 30.dp else 38.dp,
                    focusRequester = playPauseFocusRequester
                )
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Channel",
                    onClick = onNext,
                    size = if (compact) 40.dp else 48.dp,
                    iconSize = if (compact) 24.dp else 28.dp
                )
            }

            // ── Bottom bar ──
            // `progress` is read inside PlayerTimeline, so only the timeline
            // recomposes while the position ticks.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(
                        horizontal = if (compact) 12.dp else 24.dp,
                        vertical = if (compact) 6.dp else 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerTimeline(
                    progress = progress,
                    seekable = !progress.isLive && isMediaSeekable(),
                    onSeekTo = onSeekTo,
                    showLiveLabel = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                PlayerIconButton(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                    else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    onClick = onToggleMute,
                    size = if (compact) 40.dp else 44.dp
                )
                if (showFullscreenButton) {
                    Spacer(Modifier.width(if (compact) 6.dp else 12.dp))
                    PlayerIconButton(
                        icon = if (isFullscreen) Icons.Default.FullscreenExit
                        else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        onClick = onToggleFullscreen,
                        size = if (compact) 40.dp else 44.dp
                    )
                }
            }
        }
    }
}
