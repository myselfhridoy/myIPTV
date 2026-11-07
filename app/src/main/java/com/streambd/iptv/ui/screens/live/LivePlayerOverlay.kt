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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
 * Controls drawn over the live player. Pure UI: everything the user can do
 * is a callback, and all player state comes in as plain values.
 *
 * Looks:
 *  • TV                  — unchanged (big buttons on dark discs)
 *  • phone / tablet      — lighter: small plain icons, a soft disc only on Play
 *  • compact (portrait)  — NO back button / title (the system Back works and the
 *                          list below names the channel); only a few actions on
 *                          the right, centre controls, timeline.
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
    /** Portrait player: the video area is small. */
    compact: Boolean = false,
    /** Phones with the portrait player: show a fullscreen / exit-fullscreen button. */
    showFullscreenButton: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val tv = LocalDeviceProfile.current.isTv

    // Cutout insets only matter in fullscreen. In the portrait layout the system
    // already places the video below the status bar, and applying them again made
    // the controls jump around (extra top/left offset appearing and disappearing).
    val insetsModifier = if (compact) Modifier else Modifier.windowInsetsPadding(WindowInsets.displayCutout)

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
                focusRequester = unlockFocusRequester,
                glass = true
            )
        } else {
            // ── Top bar ──
            Row(
                modifier = Modifier
                    .align(if (compact) Alignment.TopEnd else Alignment.TopStart)
                    .then(if (compact) Modifier else Modifier.fillMaxWidth())
                    .then(insetsModifier)
                    .padding(
                        horizontal = if (tv) 16.dp else if (compact) 6.dp else 12.dp,
                        vertical = if (tv) 12.dp else if (compact) 4.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!compact) {
                    PlayerIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack
                    )
                    Spacer(Modifier.width(if (tv) 12.dp else 8.dp))
                    Text(
                        text = channelName,
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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(if (tv) 8.dp else 2.dp)) {
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
                horizontalArrangement = Arrangement.spacedBy(
                    if (tv) 28.dp else if (compact) 18.dp else 24.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Prev Channel",
                    onClick = onPrev,
                    size = if (tv) 48.dp else if (compact) 36.dp else 42.dp,
                    iconSize = if (tv) 28.dp else if (compact) 22.dp else 26.dp
                )
                PlayerIconButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    onClick = onTogglePlay,
                    size = if (tv) 68.dp else if (compact) 46.dp else 56.dp,
                    iconSize = if (tv) 38.dp else if (compact) 26.dp else 32.dp,
                    focusRequester = playPauseFocusRequester,
                    glass = true
                )
                PlayerIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Channel",
                    onClick = onNext,
                    size = if (tv) 48.dp else if (compact) 36.dp else 42.dp,
                    iconSize = if (tv) 28.dp else if (compact) 22.dp else 26.dp
                )
            }

            // ── Bottom bar ──
            // `progress` is read inside PlayerTimeline, so only the timeline
            // recomposes while the position ticks.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(insetsModifier)
                    .padding(
                        horizontal = if (tv) 24.dp else if (compact) 10.dp else 18.dp,
                        vertical = if (tv) 16.dp else if (compact) 2.dp else 8.dp
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
                Spacer(Modifier.width(if (tv) 12.dp else 4.dp))
                PlayerIconButton(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                    else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    onClick = onToggleMute,
                    size = if (tv) 44.dp else 36.dp,
                    iconSize = if (tv) 24.dp else 20.dp
                )
                if (showFullscreenButton) {
                    Spacer(Modifier.width(2.dp))
                    PlayerIconButton(
                        icon = if (isFullscreen) Icons.Default.FullscreenExit
                        else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                        onClick = onToggleFullscreen,
                        size = if (tv) 44.dp else 36.dp,
                        iconSize = if (tv) 24.dp else 22.dp
                    )
                }
            }
        }
    }
}
