package com.streambd.iptv.ui.screens.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.PlayerIconButton
import com.streambd.iptv.ui.components.PlayerProgress
import com.streambd.iptv.ui.components.PlayerScrim
import com.streambd.iptv.ui.components.PlayerTimeline

/**
 * Controls drawn over the VOD player. Pure UI — the screen keeps ExoPlayer,
 * focus requesters and every side effect; this only draws and reports taps.
 *
 * (Different from the live overlay on purpose: ±10 s buttons instead of
 * channel zapping, a slider that commits on release, no sources / TV-mode.)
 */
@Composable
internal fun VodPlayerOverlay(
    title: String?,
    isLocked: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    progress: PlayerProgress,
    playPauseFocusRequester: FocusRequester,
    unlockFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onCycleAspect: () -> Unit,
    onEnterPiP: () -> Unit,
    onOpenTracks: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tv = LocalDeviceProfile.current.isTv
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
                        horizontal = if (tv) 16.dp else 12.dp,
                        vertical = if (tv) 12.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
                Spacer(Modifier.width(if (tv) 12.dp else 8.dp))
                if (title != null) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = if (tv) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(if (tv) 8.dp else 2.dp)) {
                    PlayerIconButton(Icons.Default.Lock, "Lock", onLock)
                    PlayerIconButton(Icons.Default.AspectRatio, "Aspect Ratio", onCycleAspect)
                    PlayerIconButton(Icons.Default.PictureInPicture, "PiP", onEnterPiP)
                    PlayerIconButton(Icons.Default.Settings, "Tracks", onOpenTracks)
                }
            }

            // ── Centre: -10 s · play/pause · +10 s ──
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(if (tv) 28.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    icon = Icons.Default.Replay10,
                    contentDescription = "Seek Back 10s",
                    onClick = onSeekBack,
                    size = if (tv) 48.dp else 42.dp,
                    iconSize = if (tv) 28.dp else 26.dp
                )
                PlayerIconButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    onClick = onTogglePlay,
                    size = if (tv) 68.dp else 56.dp,
                    iconSize = if (tv) 38.dp else 32.dp,
                    focusRequester = playPauseFocusRequester,
                    glass = true
                )
                PlayerIconButton(
                    icon = Icons.Default.Forward10,
                    contentDescription = "Seek Forward 10s",
                    onClick = onSeekForward,
                    size = if (tv) 48.dp else 42.dp,
                    iconSize = if (tv) 28.dp else 26.dp
                )
            }

            // ── Bottom: timeline + mute ──
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(
                        horizontal = if (tv) 24.dp else 18.dp,
                        vertical = if (tv) 16.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerTimeline(
                    progress = progress,
                    seekable = true,
                    onSeekTo = onSeekTo,
                    commitOnRelease = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                PlayerIconButton(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                    else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    onClick = onToggleMute,
                    size = if (tv) 44.dp else 36.dp,
                    iconSize = if (tv) 24.dp else 20.dp
                )
            }
        }
    }
}
