package com.streambd.iptv.ui.screens.live

import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.streambd.iptv.R
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.ui.components.PlayerRevealContainer
import com.streambd.iptv.ui.components.PlayerWindowEffect
import com.streambd.iptv.ui.components.rememberPlayerOrientationController
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.utils.PlayerPreferences
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.streambd.iptv.ui.components.ChannelInfoCard
import com.streambd.iptv.ui.components.ChannelNumberBadge
import com.streambd.iptv.ui.components.PlaybackErrorPanel
import com.streambd.iptv.ui.components.PlayerLoadingIndicator
import com.streambd.iptv.ui.components.rememberPlayerProgress
import com.streambd.iptv.ui.components.SourceSelectionDialog
import com.streambd.iptv.ui.components.TrackSelectionDialog
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.primarySourceUrl
import com.streambd.iptv.utils.MediaResolver
import com.streambd.iptv.utils.PlaybackLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VideoScaleMode(val title: String) {
    FILL("Fill Screen"),
    FIT("Original (Fit)"),
    ZOOM("Zoom / Fill to Width"),
    CINEMA("Cinema (18:9)")
}

// ---------- top-level helpers ----------------------------------------------

private data class UiPlaybackError(
    val message: String,
    val causeMessage: String?,
    val isHlsFormat: Boolean = false
)

private fun isHlsFormatError(error: PlaybackException): Boolean {
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> return true

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> return false

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED -> return false
    }
    val text = buildString {
        append(error.message.orEmpty())
        append(' ')
        append(error.cause?.message.orEmpty())
    }.lowercase()
    return text.contains("#extm3u") ||
        text.contains("ext-x-") ||
        text.contains("contentismalformed") ||
        text.contains("does not start with the #extm3u")
}

private fun List<Channel>.indexOfChannel(channel: Channel): Int {
    val exact = indexOf(channel)
    if (exact != -1) return exact
    return indexOfFirst {
        it.name == channel.name &&
            (it.url == channel.url || (it.tokenId != null && it.tokenId == channel.tokenId))
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// ---------- screen ---------------------------------------------------------

@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    viewModel: MainViewModel,
    playerViewModel: com.streambd.iptv.ui.viewmodels.LivePlayerViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(),
    navController: NavController,
    exoPlayer: ExoPlayer,
    onEnterPiP: () -> Unit
) {
    val context = LocalContext.current
    val selectedChannel by viewModel.selectedChannel.collectAsState()

    if (selectedChannel == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) { Text("No channel selected", color = Color.White) }
        return
    }

    val channel = selectedChannel!!
    // Compound key: two channels with the same name but different URLs
    // (or different tokenIds) must be distinct. Previously `tokenId ?: name`
    // collided for same-named channels.
    val channelKey = "${channel.tokenId ?: channel.name}|${channel.primarySourceUrl()}"

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isBuffering by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var showSourceSelector by remember { mutableStateOf(false) }
    var isInPiP by remember { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }

    var currentSourceUrl by remember(channelKey) {
        mutableStateOf(channel.primarySourceUrl())
    }
    var failedSourceUrls by remember(channelKey) { mutableStateOf(setOf<String>()) }

    // Position / buffer / duration: observable holder, polled only while the
    // controls are visible. Only the seek bar reads it, so the screen itself
    // no longer recomposes every 500 ms.
    val progress = rememberPlayerProgress(exoPlayer, active = showOverlay)
    var playerError by remember { mutableStateOf<UiPlaybackError?>(null) }
    var lastHlsError by remember { mutableStateOf(false) }

    val currentMediaSource by playerViewModel.currentMediaSource.collectAsState()
    val isResolving by playerViewModel.isLoading.collectAsState()
    val isResolutionFailed by playerViewModel.resolutionFailed.collectAsState()

    LaunchedEffect(isResolutionFailed) {
        if (isResolutionFailed) {
            PlaybackLogger.logResolutionFailed(
                context = context,
                channelName = channel.name,
                sourceUrl = currentSourceUrl,
                reason = "resolver_returned_null"
            )
        }
    }

    val currentPlaylist by viewModel.currentPlaylist.collectAsState()

    var scaleMode by remember { mutableStateOf(VideoScaleMode.FILL) }
    var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }

    var backPressedTime by remember { mutableStateOf(0L) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }

    var showChannelInfoOverlay by remember { mutableStateOf(false) }
    var channelNumberInput by remember { mutableStateOf("") }
    val isTvModeEnabled by viewModel.isTvModeEnabled.collectAsState()
    val mainFocusRequester = remember { FocusRequester() }

    // ── Portrait player: phones & tablets, user setting "Portrait Player" (never on TV) ──
    //   portrait  → video on top (16:9) + channel list below
    //   landscape → the usual fullscreen player
    val isTvDevice = LocalDeviceProfile.current.isTv
    val portraitEnabled = remember { PlayerPreferences.isPortraitPlayerEnabled(context) }
    val flexible = !isTvDevice && portraitEnabled
    val isLandscapeNow =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val portraitLayout = flexible && !isLandscapeNow && !isInPiP
    val orientationController = rememberPlayerOrientationController()
    // Portrait shows the whole picture (FIT); fullscreen keeps the user's choice.
    val effectiveScaleMode = if (portraitLayout) VideoScaleMode.FIT else scaleMode

    PlayerWindowEffect(flexible = flexible) { inPip ->
        isInPiP = inPip
        if (inPip) showOverlay = false
    }

    DisposableEffect(playerViewModel) {
        onDispose { playerViewModel.stopPrefetch() }
    }

    // ── Real exit ────────────────────────────────────────────────────────
    // Runs on the Activity lifecycleScope (survives recomposition) so the
    // continuation reaches popBackStack even if the screen re-composes
    // between the orientation request and the teardown.
    val finishExit: () -> Unit = {
        val activity = context as? ComponentActivity
        val teardown: () -> Unit = {
            try { exoPlayer.stop() } catch (_: Exception) {}
            try { exoPlayer.clearMediaItems() } catch (_: Exception) {}
            try { navController.popBackStack() } catch (_: Exception) {}
        }
        if (activity == null) {
            teardown()
        } else {
            activity.lifecycleScope.launch {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                delay(400)
                teardown()
            }
        }
    }

    val requestExit: () -> Unit = {
        if (!exitRequested) exitRequested = true
    }

    LaunchedEffect(channelNumberInput) {
        if (channelNumberInput.isNotEmpty()) {
            delay(2000L)
            viewModel.tuneToChannelNumber(channelNumberInput)
            channelNumberInput = ""
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        try { mainFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    LaunchedEffect(channel) {
        showChannelInfoOverlay = true
        delay(4000L)
        showChannelInfoOverlay = false
    }

    LaunchedEffect(channelKey, currentSourceUrl) {
        playerError = null
        lastHlsError = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        playerViewModel.resolveChannel(channel, currentSourceUrl)
    }

    LaunchedEffect(currentMediaSource) {
        val source = currentMediaSource ?: return@LaunchedEffect
        val sourceUri = source.mediaItem.localConfiguration?.uri?.toString().orEmpty()
        if (MediaResolver.isKnownPageUrl(sourceUri)) {
            playerError = UiPlaybackError(
                message = "Stream resolver returned a page URL instead of a video stream.",
                causeMessage = null,
                isHlsFormat = true
            )
            isBuffering = false
            return@LaunchedEffect
        }
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(channel, currentPlaylist) {
        val index = currentPlaylist.indexOfChannel(channel)
        if (index != -1) playerViewModel.prefetchChannels(currentPlaylist, index)
    }

    // ── Player listener ──────────────────────────────────────────────────
    // Keyed on channelKey so a channel switch re-registers the listener
    // with fresh closures over the new failedSourceUrls / currentSourceUrl
    // state. Previously this listener captured state from the first
    // composition and mishandled failover after surfing.
    DisposableEffect(exoPlayer, channelKey) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                    lastHlsError = false
                }
            }

            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }

            override fun onVolumeChanged(volume: Float) {
                isMuted = volume == 0f
            }

            override fun onPlayerError(error: PlaybackException) {
                // If the player fell behind the live window in DASH/HLS, auto-seek to current live edge
                var cause: Throwable? = error
                var isBehindLive = false
                while (cause != null) {
                    if (cause is androidx.media3.exoplayer.source.BehindLiveWindowException) {
                        isBehindLive = true
                        break
                    }
                    cause = cause.cause
                }
                if (isBehindLive) {
                    android.util.Log.d("LivePlayerScreen", "BehindLiveWindowException: auto-recovering to live edge")
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return
                }

                val hls = isHlsFormatError(error)
                PlaybackLogger.logLivePlaybackError(
                    context = context,
                    channelName = channel.name,
                    channelGroup = channel.group,
                    channelUrl = channel.url,
                    sourceUrl = currentSourceUrl,
                    sourcesCount = channel.sources.size,
                    error = error,
                    isHlsFormatError = hls
                )
                val allUrls = (listOf(channel.url) + channel.sources.values.toList())
                    .filter { it.isNotBlank() }
                    .distinct()
                val remaining = allUrls.filter { it !in failedSourceUrls && it != currentSourceUrl }
                if (remaining.isNotEmpty()) {
                    failedSourceUrls = failedSourceUrls + currentSourceUrl
                    currentSourceUrl = remaining.first()
                    isBuffering = true
                    playerError = null
                    lastHlsError = hls
                } else {
                    playerError = UiPlaybackError(
                        message = error.message ?: "Unknown playback error",
                        causeMessage = error.cause?.message,
                        isHlsFormat = hls
                    )
                    isBuffering = false
                    lastHlsError = hls
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(showOverlay, isPlaying, isLocked) {
        if (showOverlay) {
            delay(100)
            try {
                if (isLocked) unlockFocusRequester.requestFocus()
                else playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
        if (showOverlay && isPlaying) {
            delay(5000)
            showOverlay = false
        }
    }

    BackHandler {
        if (isLocked) {
            showOverlay = true
        } else if (isTvModeEnabled) {
            val now = System.currentTimeMillis()
            if (now - backPressedTime < 2000) {
                (context as? ComponentActivity)?.finishAndRemoveTask()
            } else {
                backPressedTime = now
                android.widget.Toast.makeText(
                    context, "Press back again to exit app", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } else if (flexible && isLandscapeNow) {
            // Fullscreen → back to the portrait player first (like video apps).
            orientationController.setFullscreen(false)
        } else {
            requestExit()
        }
    }

    PlayerRevealContainer(
        exitRequested = exitRequested,
        forceUpright = isInPiP || flexible,
        onExitComplete = finishExit
    ) {
        // The whole player (video + overlays + dialogs) as one piece, so it can be
        // placed full-screen OR as the 16:9 video area of the portrait layout.
        val playerSurface: @Composable (Modifier) -> Unit = { surfaceModifier ->
        Box(
            modifier = surfaceModifier
                .background(Color.Black)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                if (!showOverlay) { viewModel.tuneToNextChannel(); true } else false
                            }
                            android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                                viewModel.tuneToNextChannel(); true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (!showOverlay) { viewModel.tuneToPrevChannel(); true } else false
                            }
                            android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                viewModel.tuneToPrevChannel(); true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER -> {
                                if (!showOverlay) { showOverlay = true; true } else false
                            }
                            in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                                channelNumberInput +=
                                    (event.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_0).toString()
                                true
                            }
                            in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> {
                                channelNumberInput +=
                                    (event.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0).toString()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .focusRequester(mainFocusRequester)
                .focusable()
                .pointerInput(Unit) { detectTapGestures(onTap = { showOverlay = !showOverlay }) }
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (totalDrag > 150f) viewModel.tuneToPrevChannel()
                            else if (totalDrag < -150f) viewModel.tuneToNextChannel()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    }
                }
        ) {
            val isCinema = effectiveScaleMode == VideoScaleMode.CINEMA

            Box(
                modifier = if (isCinema) Modifier.fillMaxSize(0.9f).align(Alignment.Center)
                           else Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = if (isCinema)
                        Modifier.aspectRatio(18f / 9f).clip(RoundedCornerShape(16.dp))
                    else Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val parent = FrameLayout(ctx)
                        val view = LayoutInflater.from(ctx)
                            .inflate(R.layout.view_player_texture, parent, false) as PlayerView
                        view.apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        val exoResizeMode = when (effectiveScaleMode) {
                            VideoScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            VideoScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VideoScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            VideoScaleMode.CINEMA -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                        if (playerView.resizeMode != exoResizeMode) playerView.resizeMode = exoResizeMode
                        if (playerView.player != exoPlayer) playerView.player = exoPlayer
                    }
                )
            }

            if (playerError != null || isResolutionFailed) {
                StaticNoiseBackground(Modifier.fillMaxSize())
            }

            if (showOverlay && !isInPiP) {
                LivePlayerOverlay(
                    modifier = Modifier.fillMaxSize(),
                    channelName = channel.name,
                    isLocked = isLocked,
                    isTvModeEnabled = isTvModeEnabled,
                    hasSources = channel.sources.isNotEmpty(),
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    progress = progress,
                    isMediaSeekable = { exoPlayer.isCurrentMediaItemSeekable },
                    playPauseFocusRequester = playPauseFocusRequester,
                    unlockFocusRequester = unlockFocusRequester,
                    onBack = {
                        if (flexible && isLandscapeNow) orientationController.setFullscreen(false)
                        else requestExit()
                    },
                    onUnlock = { isLocked = false },
                    onTurnOffTvMode = {
                        viewModel.toggleTvMode(false)
                        val intent = context.packageManager
                            .getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                        if (intent != null) context.startActivity(intent)
                        (context as? ComponentActivity)?.finishAndRemoveTask()
                    },
                    onOpenSources = { showSourceSelector = true },
                    onLock = { isLocked = true },
                    onCycleAspect = {
                        val modes = VideoScaleMode.entries.toTypedArray()
                        scaleMode = modes[(modes.indexOf(scaleMode) + 1) % modes.size]
                        android.widget.Toast.makeText(
                            context, scaleMode.title, android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    onEnterPiP = {
                        val activity = context as? ComponentActivity
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            val params = android.app.PictureInPictureParams.Builder().build()
                            activity?.enterPictureInPictureMode(params)
                        } else {
                            onEnterPiP()
                        }
                    },
                    onOpenTracks = { showTrackSelector = true },
                    onPrev = { viewModel.tuneToPrevChannel() },
                    onNext = { viewModel.tuneToNextChannel() },
                    onTogglePlay = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                    onSeekTo = { positionMs -> exoPlayer.seekTo(positionMs) },
                    onToggleMute = {
                        val newMuted = !isMuted
                        exoPlayer.volume = if (newMuted) 0f else 1f
                    },
                    compact = portraitLayout,
                    showFullscreenButton = flexible,
                    isFullscreen = isLandscapeNow,
                    onToggleFullscreen = { orientationController.setFullscreen(!isLandscapeNow) }
                )
            }

            val showError = playerError != null || isResolutionFailed

            PlayerLoadingIndicator(
                visible = !showError && (isResolving || isBuffering) && !isLocked,
                modifier = Modifier.align(Alignment.Center)
            )

            if (showError) {
                val primaryText = when {
                    playerError != null -> playerError?.message ?: "Unknown error"
                    isResolutionFailed ->
                        "Stream resolver could not extract a playable URL.\n" +
                            "The provider page may be unreachable, blocked, or behind a captcha."
                    else -> "Unknown error"
                }
                PlaybackErrorPanel(
                    modifier = Modifier.align(Alignment.Center),
                    message = primaryText,
                    hint = if (lastHlsError || isResolutionFailed)
                        "Hint: The resolver could not extract a valid HLS/DASH manifest."
                    else null,
                    cause = playerError?.causeMessage,
                    onRetry = {
                        playerError = null
                        lastHlsError = false
                        isBuffering = true
                        failedSourceUrls = emptySet()
                        // Always reset to the primary source AND bust the
                        // cache. Setting currentSourceUrl to the same
                        // value won't trigger the LaunchedEffect, so we
                        // also call retryResolution to force a re-resolve.
                        val retryUrl = channel.primarySourceUrl()
                        currentSourceUrl = retryUrl
                        playerViewModel.retryResolution(channel, retryUrl)
                    }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showChannelInfoOverlay && !showOverlay && !isInPiP,
                enter = androidx.compose.animation.fadeIn() +
                    androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
                exit = androidx.compose.animation.fadeOut() +
                    androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopStart).padding(if (portraitLayout) 12.dp else 32.dp)
            ) {
                ChannelInfoCard(
                    name = channel.name,
                    group = channel.group,
                    logoUrl = channel.logo
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = channelNumberInput.isNotEmpty(),
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(if (portraitLayout) 12.dp else 32.dp)
            ) {
                ChannelNumberBadge(text = channelNumberInput)
            }

            if (showTrackSelector) {
                TrackSelectionDialog(player = exoPlayer, onDismiss = { showTrackSelector = false })
            }

            if (showSourceSelector) {
                SourceSelectionDialog(
                    sources = channel.sources,
                    currentSourceUrl = currentSourceUrl,
                    onSourceSelected = { _, url ->
                        failedSourceUrls = emptySet()
                        currentSourceUrl = url
                    },
                    onDismiss = { showSourceSelector = false }
                )
            }
        }
        } // end of playerSurface

        if (portraitLayout) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                playerSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                PortraitChannelPane(
                    current = channel,
                    channels = currentPlaylist,
                    onChannelClick = { viewModel.selectChannel(it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        } else {
            playerSurface(Modifier.fillMaxSize())
        }
    }
}

// ---------- Static noise background ---------------------------------------

@Composable
private fun StaticNoiseBackground(modifier: Modifier = Modifier) {
    val w = 96
    val h = 54
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(Unit) {
        val pixels = IntArray(w * h)
        // Two bitmaps ping-ponged: while the previous frame is still being
        // drawn from one bitmap, we write into the other. Prevents tearing
        // that a single reused Bitmap produces.
        val bitmaps = Array(2) {
            android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        }
        var idx = 0
        val rnd = java.util.Random()
        while (isActive) {
            val bmp = bitmaps[idx]
            idx = 1 - idx
            for (i in pixels.indices) {
                val v = rnd.nextInt(256)
                pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            imageBitmap = bmp.asImageBitmap()
            delay(70)
        }
    }

    androidx.compose.foundation.Canvas(modifier) {
        val img = imageBitmap ?: return@Canvas
        drawImage(
            image = img,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None
        )
    }
}

