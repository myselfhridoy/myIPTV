package com.streambd.iptv.ui.screens.live

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
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.key.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.TrackSelectionDialog
import com.streambd.iptv.ui.components.SourceSelectionDialog
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter

enum class VideoScaleMode(val title: String) {
    FILL("Fill Screen"),
    FIT("Original (Fit)"),
    ZOOM("Zoom / Fill to Width"),
    CINEMA("Cinema (18:9)")
}

@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    viewModel: MainViewModel, 
    playerViewModel: com.streambd.iptv.ui.viewmodels.LivePlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    navController: NavController,
    exoPlayer: ExoPlayer,
    trackSelector: DefaultTrackSelector,
    onEnterPiP: () -> Unit
) {
    val context = LocalContext.current
    val selectedChannel by viewModel.selectedChannel.collectAsState()

    if (selectedChannel == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No channel selected", color = Color.White)
        }
        return
    }

    val channel = selectedChannel!!
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isBuffering by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var showSourceSelector by remember { mutableStateOf(false) }
    var isInPiP by remember { mutableStateOf(false) }
    var currentSourceUrl by remember(channel.tokenId ?: channel.name) { mutableStateOf(channel.url) }

    var currentPosition by remember { mutableStateOf(0L) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playerError by remember { mutableStateOf<androidx.media3.common.PlaybackException?>(null) }
    
    val currentMediaSource by playerViewModel.currentMediaSource.collectAsState()
    val isResolving by playerViewModel.isLoading.collectAsState()
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

    LaunchedEffect(Unit) {
        snapshotFlow { channelNumberInput }
            .filter { it.isNotEmpty() }
            .debounce(2000L)
            .collect { input ->
                viewModel.tuneToChannelNumber(input)
                channelNumberInput = ""
            }
    }

    LaunchedEffect(Unit) {
        delay(100)
        try { mainFocusRequester.requestFocus() } catch (e: Exception) {}
    }

    LaunchedEffect(channel) {
        showChannelInfoOverlay = true
        delay(4000L)
        showChannelInfoOverlay = false
    }

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
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        // Set landscape immediately
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(pipObserver)
            if (originalOrientation != null) {
                activity?.requestedOrientation = originalOrientation
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Handle ExoPlayer Media Setup
    LaunchedEffect(channel.tokenId ?: channel.name, currentSourceUrl) {
        playerError = null
        exoPlayer.stop() // Immediately stop the previous channel
        exoPlayer.clearMediaItems() // Clear previous media state
        playerViewModel.resolveChannel(channel, currentSourceUrl)
    }

    // Handle Media Source updates
    LaunchedEffect(currentMediaSource) {
        val source = currentMediaSource ?: return@LaunchedEffect
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Auto Prefetch next/prev channels
    LaunchedEffect(channel, currentPlaylist) {
        val index = currentPlaylist.indexOf(channel)
        if (index != -1) {
            playerViewModel.prefetchChannels(currentPlaylist, index)
        }
    }

    // ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                }
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val sourcesList = channel.sources.values.toList()
                val currentIndex = sourcesList.indexOf(currentSourceUrl)
                if (sourcesList.isNotEmpty() && currentIndex != -1 && currentIndex < sourcesList.size - 1) {
                    // Auto play next source
                    currentSourceUrl = sourcesList[currentIndex + 1]
                    isBuffering = true
                    playerError = null
                } else {
                    playerError = error
                    isBuffering = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Progress Tracker
    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            bufferedPosition = exoPlayer.bufferedPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide overlay and manage focus
    LaunchedEffect(showOverlay, isPlaying, isLocked) {
        if (showOverlay) {
            delay(100) // Wait for layout to settle
            try { 
                if (isLocked) unlockFocusRequester.requestFocus() 
                else playPauseFocusRequester.requestFocus() 
            } catch (e: Exception) {}
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
        } else if (isTvModeEnabled) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) {
                val activity = context as? ComponentActivity
                activity?.finishAndRemoveTask()
            } else {
                backPressedTime = currentTime
                android.widget.Toast.makeText(context, "Press back again to exit app", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            val activity = context as? ComponentActivity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showOverlay) {
                                viewModel.tuneToNextChannel()
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                            viewModel.tuneToNextChannel()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showOverlay) {
                                viewModel.tuneToPrevChannel()
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            viewModel.tuneToPrevChannel()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                            if (!showOverlay) {
                                showOverlay = true
                                true
                            } else false
                        }
                        in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> {
                            val digit = event.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_0
                            channelNumberInput += digit.toString()
                            true
                        }
                        in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> {
                            val digit = event.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0
                            channelNumberInput += digit.toString()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusRequester(mainFocusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showOverlay = !showOverlay }
                )
            }
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (totalDrag > 150f) {
                            // Swiped Right -> Previous Channel
                            viewModel.tuneToPrevChannel()
                        } else if (totalDrag < -150f) {
                            // Swiped Left -> Next Channel
                            viewModel.tuneToNextChannel()
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                }
            }
    ) {
        val isCinema = scaleMode == VideoScaleMode.CINEMA
        
        Box(
            modifier = if (isCinema) Modifier.fillMaxSize(0.9f).align(Alignment.Center) else Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = if (isCinema) Modifier.aspectRatio(18f / 9f).clip(RoundedCornerShape(16.dp)) else Modifier.fillMaxSize(),
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
                    // Locked State UI
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
                    // Unlocked State UI
                    
                    // Top Bar
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
                        
                        // Title Area
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
                                    text = channel.name, 
                                    color = Color.White, 
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Top Right Icons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isTvModeEnabled) {
                                TvIconButton(onClick = {
                                    viewModel.toggleTvMode(false)
                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    if (intent != null) context.startActivity(intent)
                                    val activity = context as? ComponentActivity
                                    activity?.finishAndRemoveTask()
                                }) {
                                    Icon(Icons.Default.PowerSettingsNew, "Turn Off TV Mode", tint = TealAccent)
                                }
                            }
                            if (channel.sources.isNotEmpty()) {
                                TvIconButton(onClick = { showSourceSelector = true }) {
                                    Icon(Icons.Default.Dns, "Sources", tint = Color.White)
                                }
                            }
                            TvIconButton(onClick = { isLocked = true }) {
                                Icon(Icons.Default.Lock, "Lock", tint = Color.White)
                            }
                            TvIconButton(onClick = {
                                val modes = VideoScaleMode.entries.toTypedArray()
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
                                } else {
                                    onEnterPiP()
                                }
                            }) {
                                Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White)
                            }
                            TvIconButton(onClick = { showTrackSelector = true }) {
                                Icon(Icons.Default.Settings, "Tracks", tint = Color.White) // Mockup has multiple icons, using Settings for track
                            }
                        }
                    }

                    // Center Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvIconButton(
                            onClick = { viewModel.tuneToPrevChannel() },
                            modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev Channel", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        
                        TvIconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape).focusRequester(playPauseFocusRequester)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        TvIconButton(
                            onClick = { viewModel.tuneToNextChannel() },
                            modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Channel", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Bottom Bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatTime(currentPosition), color = Color.White, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val bufferFraction = if (duration > 0) (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { bufferFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp) // align with slider track
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = Color.White.copy(alpha = 0.4f),
                                trackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                            Slider(
                                value = (if (duration > 0) currentPosition.toFloat() / duration else 0f).coerceIn(0f, 1f),
                                onValueChange = { value ->
                                    exoPlayer.seekTo((value * duration).toLong())
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.Red,
                                    inactiveTrackColor = Color.Transparent
                                )
                            )
                        }
                        Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
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

        if ((isResolving || isBuffering) && !isLocked) {
            // Spinner only — no "Resolving stream..." / "Buffering..." text
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Red
            )
        } else if (playerError != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Error, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Playback Error", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(playerError?.message ?: "Unknown Error", color = Color.White, fontSize = 12.sp)
                Text(playerError?.cause?.message ?: "", color = Color.LightGray, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        playerError = null
                        isBuffering = true
                        val retryUrl = if (channel.sources.isNotEmpty()) {
                            channel.sources.values.firstOrNull() ?: channel.url
                        } else {
                            channel.url
                        }
                        if (currentSourceUrl != retryUrl) {
                            currentSourceUrl = retryUrl // This will trigger LaunchedEffect
                        } else {
                            playerViewModel.retryResolution(channel, currentSourceUrl)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry", color = Color.White)
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showChannelInfoOverlay && !showOverlay && !isInPiP,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopStart).padding(32.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channel.logo.isNotEmpty()) {
                    coil.compose.AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column {
                    Text(text = channel.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (channel.group.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = channel.group, color = Color.LightGray, fontSize = 16.sp)
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = channelNumberInput.isNotEmpty(),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(TealAccent.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = channelNumberInput,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }


    if (showTrackSelector) {
        TrackSelectionDialog(
            trackSelector = trackSelector,
            currentTracks = exoPlayer.currentTracks,
            onDismiss = { showTrackSelector = false }
        )
    }
    
    if (showSourceSelector) {
        SourceSelectionDialog(
            sources = channel.sources,
            currentSourceUrl = currentSourceUrl,
            onSourceSelected = { _, url -> currentSourceUrl = url },
            onDismiss = { showSourceSelector = false }
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
