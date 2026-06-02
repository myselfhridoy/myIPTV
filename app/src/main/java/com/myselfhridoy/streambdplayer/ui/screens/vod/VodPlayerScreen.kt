package com.myselfhridoy.streambdplayer.ui.screens.vod

import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.myselfhridoy.streambdplayer.data.models.StreamSource
import com.myselfhridoy.streambdplayer.ui.components.TvIconButton
import com.myselfhridoy.streambdplayer.ui.components.TrackSelectionDialog
import com.myselfhridoy.streambdplayer.ui.screens.live.VideoScaleMode
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector
) {
    val context = LocalContext.current
    val selectedStream by mainViewModel.selectedStream.collectAsState()
    val selectedMedia by tmdbViewModel.selectedMedia.collectAsState()

    if (selectedStream == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No stream selected", color = Color.White)
        }
        return
    }

    val stream = selectedStream!!
    val videoTitle = selectedMedia?.displayTitle ?: "VOD Stream"

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isLoading by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var isInPiP by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }

    val seekAmount = 10_000L // 10 seconds

    // Lock Screen Orientation to Landscape and hide system UI
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity

        // Setup PiP listener
        val pipObserver = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPiP = info.isInPictureInPictureMode
            if (isInPiP) {
                showOverlay = false
            }
        }
        activity?.addOnPictureInPictureModeChangedListener(pipObserver)

        val originalOrientation = activity?.requestedOrientation
        val window = activity?.window

        if (window != null) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }

        // Set landscape immediately
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(pipObserver)
            if (originalOrientation != null) {
                activity?.requestedOrientation = originalOrientation
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Handle ExoPlayer Media Setup
    LaunchedEffect(stream) {
        isLoading = true

        val headers = stream.headers?.toMutableMap() ?: mutableMapOf()

        val finalUrl = stream.url

        if (exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != finalUrl) {
            val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                setAllowCrossProtocolRedirects(true)
                headers["User-Agent"]?.let { setUserAgent(it) }
                val requestHeaders = headers.filterKeys { it.lowercase() != "user-agent" }
                if (requestHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(requestHeaders)
                }
            }

            val mediaItemBuilder = MediaItem.Builder().setUri(finalUrl)

            // Add subtitles from stream source if available
            val subtitleConfigs = stream.subtitles?.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(
                        when {
                            sub.url.endsWith(".vtt", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_VTT
                            sub.url.endsWith(".srt", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                            sub.url.endsWith(".ass", ignoreCase = true) || sub.url.endsWith(".ssa", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_SSA
                            else -> androidx.media3.common.MimeTypes.TEXT_VTT
                        }
                    )
                    .setLanguage(sub.language)
                    .setLabel(sub.label ?: sub.language)
                    .build()
            } ?: emptyList()

            if (subtitleConfigs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val mediaItem = mediaItemBuilder.build()
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            isLoading = exoPlayer.playbackState == Player.STATE_BUFFERING
        }
    }

    // ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Progress Tracker
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide overlay and manage focus
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

    // Back Handler
    BackHandler {
        if (isLocked) {
            showOverlay = true
        } else {
            val activity = context as? ComponentActivity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            navController.popBackStack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.nativeKeyEvent.keyCode) {
                        // D-Pad Left = Seek Back
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!showOverlay) {
                                val newPos = (exoPlayer.currentPosition - seekAmount).coerceAtLeast(0)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                showOverlay = true
                                true
                            } else false
                        }
                        // D-Pad Right = Seek Forward
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!showOverlay) {
                                val newPos = (exoPlayer.currentPosition + seekAmount).coerceAtMost(duration)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                showOverlay = true
                                true
                            } else false
                        }
                        // D-Pad Center = Toggle Overlay
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                            if (!showOverlay) {
                                showOverlay = true
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showOverlay = !showOverlay
            }
    ) {
        val isCinema = scaleMode == VideoScaleMode.CINEMA

        Box(
            modifier = if (isCinema) Modifier
                .fillMaxSize(0.9f)
                .align(Alignment.Center) else Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = if (isCinema) Modifier
                    .aspectRatio(18f / 9f)
                    .clip(RoundedCornerShape(16.dp)) else Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    val exoResizeMode = when (scaleMode) {
                        VideoScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        VideoScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        VideoScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        VideoScaleMode.CINEMA -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                    if (playerView.resizeMode != exoResizeMode) {
                        playerView.resizeMode = exoResizeMode
                    }
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                }
            )
        }

        // Overlay UI
        if (showOverlay && !isInPiP) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
                if (isLocked) {
                    TvIconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .focusRequester(unlockFocusRequester)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color.White)
                    }
                } else {
                    // ── Top Bar ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvIconButton(
                            onClick = {
                                val activity = context as? ComponentActivity
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                exoPlayer.stop()
                                exoPlayer.clearMediaItems()
                                navController.popBackStack()
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))

                        // Title
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = videoTitle,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Top Right Icons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvIconButton(onClick = { isLocked = true }) {
                                Icon(Icons.Default.Lock, "Lock", tint = Color.White)
                            }
                            TvIconButton(onClick = {
                                val modes = VideoScaleMode.values()
                                val nextIndex = (modes.indexOf(scaleMode) + 1) % modes.size
                                scaleMode = modes[nextIndex]
                                android.widget.Toast.makeText(context, scaleMode.title, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.AspectRatio, "Aspect Ratio", tint = Color.White)
                            }
                            TvIconButton(onClick = {
                                val activity = context as? ComponentActivity
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    val params = android.app.PictureInPictureParams.Builder().build()
                                    activity?.enterPictureInPictureMode(params)
                                }
                            }) {
                                Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White)
                            }
                            TvIconButton(onClick = { showTrackSelector = true }) {
                                Icon(Icons.Default.Settings, "Tracks", tint = Color.White)
                            }
                        }
                    }

                    // ── Center Controls: Seek Back, Play/Pause, Seek Forward ──
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Seek Back -10s
                        TvIconButton(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition - seekAmount).coerceAtLeast(0)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Seek Back 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        // Play/Pause
                        TvIconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                .focusRequester(playPauseFocusRequester)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Seek Forward +10s
                        TvIconButton(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition + seekAmount).coerceAtMost(duration)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Seek Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // ── Bottom Bar ──
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatVodTime(currentPosition), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { value ->
                                exoPlayer.seekTo((value * duration).toLong())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.Gray
                            )
                        )
                        Text(formatVodTime(duration), color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        TvIconButton(onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        }) {
                            Icon(
                                if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        if (isLoading && !isLocked) {
            CircularProgressIndicator(
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    if (showTrackSelector) {
        TrackSelectionDialog(
            trackSelector = trackSelector,
            currentTracks = exoPlayer.currentTracks,
            onDismiss = { showTrackSelector = false }
        )
    }
}

private fun formatVodTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
