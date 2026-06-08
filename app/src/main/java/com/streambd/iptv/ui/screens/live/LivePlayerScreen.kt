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
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.TrackSelectionDialog
import com.streambd.iptv.ui.components.SourceSelectionDialog
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay

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
    var isLoading by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var showSourceSelector by remember { mutableStateOf(false) }
    var isInPiP by remember { mutableStateOf(false) }
    var currentSourceUrl by remember(channel) { mutableStateOf(channel.url) }

    var currentPosition by remember { mutableStateOf(0L) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playerError by remember { mutableStateOf<androidx.media3.common.PlaybackException?>(null) }

    var scaleMode by remember { mutableStateOf(VideoScaleMode.FILL) }
    var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }

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
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            if (window != null) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Handle ExoPlayer Media Setup
    LaunchedEffect(channel, currentSourceUrl) {
        isLoading = true // Show loading while resolving
        
        val parsedSource = com.streambd.iptv.utils.M3UParser.parseUrlHeaders(currentSourceUrl)
        val rawSourceUrl = parsedSource.cleanUrl

        // Build initial headers from channel
        val initialHeaders = mutableMapOf<String, String>()
        channel.userAgent?.let { initialHeaders["User-Agent"] = it }
        channel.cookie?.let { initialHeaders["Cookie"] = it }
        channel.httpReferer?.let { initialHeaders["Referer"] = it }
        channel.origin?.let { initialHeaders["Origin"] = it }
        
        parsedSource.headers.forEach { (k, v) -> initialHeaders[k] = v }
        
        val currentTokenUrl = parsedSource.custom["tokenurl"] ?: channel.tokenUrl
        val currentTokenMatch = parsedSource.custom["tokenmatch"] ?: channel.tokenMatch
        val currentTokenReplace = parsedSource.custom["tokenreplace"] ?: channel.tokenReplace
        val currentTokenId = parsedSource.custom["tokenid"]?.toIntOrNull() ?: channel.tokenId
        
        var sourceDrm = channel.drm
        if (parsedSource.custom.containsKey("drm-type")) {
            val drmType = parsedSource.custom["drm-type"]
            val drmKey = parsedSource.custom["drm-url"] ?: parsedSource.custom["drm-key"]
            if (drmKey != null && Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$").matches(drmKey)) {
                sourceDrm = com.streambd.iptv.data.models.DrmConfig(type = "clearkey", rawKeyPair = drmKey)
            } else {
                sourceDrm = com.streambd.iptv.data.models.DrmConfig(type = drmType, licenseServer = drmKey)
            }
        }

        // Resolve token if present, or pre-resolve generic links (like .php) to find actual media URL and MimeType
        val resolved = if (!currentTokenUrl.isNullOrEmpty()) {
            com.streambd.iptv.utils.TokenParser.resolveTokenForUrl(
                context = context,
                baseUrl = rawSourceUrl,
                tokenUrl = currentTokenUrl,
                tokenId = currentTokenId?.toString(),
                headers = initialHeaders,
                tokenMatch = currentTokenMatch,
                tokenReplace = currentTokenReplace
            )
        } else {
            val extRegex = Regex("\\.(m3u8|mpd|mp4|mkv|ts|webm|flv|avi|mov|mpeg|mpg|m4s|f4m|ism|sdp)(\\?|$)", RegexOption.IGNORE_CASE)
            val protoRegex = Regex("^(rtmp|rtsp|udp|srt)://", RegexOption.IGNORE_CASE)
            if (!extRegex.containsMatchIn(rawSourceUrl) && !protoRegex.containsMatchIn(rawSourceUrl)) {
                // Pre-resolve non-standard extensions (like .php) to find actual media URL and Content-Type
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val client = okhttp3.OkHttpClient.Builder()
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .build()
                        val req = okhttp3.Request.Builder().url(rawSourceUrl).apply {
                            initialHeaders.forEach { (k, v) -> header(k, v) }
                        }.head().build() // Use HEAD first
                        
                        var res = client.newCall(req).execute()
                        if (!res.isSuccessful && res.code == 405) {
                            res.close()
                            val getReq = req.newBuilder().get().build()
                            res = client.newCall(getReq).execute()
                        }
                        
                        res.use { response ->
                            if (response.isSuccessful) {
                                val finalUrl = response.request.url.toString()
                                val contentType = response.header("Content-Type")?.lowercase()
                                var mimeType: String? = null
                                if (contentType?.contains("mpegurl") == true) mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8
                                else if (contentType?.contains("dash") == true || finalUrl.contains(".mpd")) mimeType = androidx.media3.common.MimeTypes.APPLICATION_MPD
                                else if (finalUrl.contains(".m3u8")) mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8
                                
                                com.streambd.iptv.utils.ResolvedToken(url = finalUrl, headers = initialHeaders, mimeType = mimeType)
                            } else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                null
            }
        }

        val finalUrl = resolved?.url ?: rawSourceUrl
        val finalHeaders = resolved?.headers ?: initialHeaders
        val finalDrm = resolved?.drm ?: sourceDrm

        if (exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != finalUrl) {
            val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory().apply {
                setAllowCrossProtocolRedirects(true)
                finalHeaders["User-Agent"]?.let { setUserAgent(it) }
                val requestHeaders = finalHeaders.filterKeys { it.lowercase() != "user-agent" }
                if (requestHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(requestHeaders)
                }
            }
            
            val mediaItemBuilder = MediaItem.Builder().setUri(finalUrl)
            resolved?.mimeType?.let { mediaItemBuilder.setMimeType(it) }

            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

            // DRM Setup
            if (finalDrm != null && finalDrm.type != null) {
                val drmScheme = when (finalDrm.type.lowercase()) {
                    "widevine" -> androidx.media3.common.C.WIDEVINE_UUID
                    "playready" -> androidx.media3.common.C.PLAYREADY_UUID
                    "clearkey" -> androidx.media3.common.C.CLEARKEY_UUID
                    else -> androidx.media3.common.C.WIDEVINE_UUID
                }

                val drmBuilder = MediaItem.DrmConfiguration.Builder(drmScheme)

                if (finalDrm.type.lowercase() == "clearkey" && finalDrm.rawKeyPair != null) {
                    // Handle Raw ClearKey (kid:key) pair format natively for ExoPlayer
                    val parts = finalDrm.rawKeyPair.split(":")
                    if (parts.size == 2) {
                        try {
                            val kidBytes = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            val keyBytes = parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()

                            val kidB64 = android.util.Base64.encodeToString(kidBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
                            val keyB64 = android.util.Base64.encodeToString(keyBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)

                            val clearkeyJson = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"$keyB64\",\"kid\":\"$kidB64\"}],\"type\":\"temporary\"}"
                            
                            val localMediaDrmCallback = androidx.media3.exoplayer.drm.LocalMediaDrmCallback(clearkeyJson.toByteArray())
                            val drmSessionManager = androidx.media3.exoplayer.drm.DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(androidx.media3.common.C.CLEARKEY_UUID, androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                                .build(localMediaDrmCallback)
                            
                            mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else if (finalDrm.licenseServer != null) {
                    if (finalDrm.type.lowercase() == "clearkey" && finalDrm.licenseServer.trim().startsWith("{")) {
                        // The license "server" is actually a JSON ClearKey payload
                        try {
                            val localMediaDrmCallback = androidx.media3.exoplayer.drm.LocalMediaDrmCallback(finalDrm.licenseServer.trim().toByteArray())
                            val drmSessionManager = androidx.media3.exoplayer.drm.DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(androidx.media3.common.C.CLEARKEY_UUID, androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                                .build(localMediaDrmCallback)
                            mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        drmBuilder.setLicenseUri(finalDrm.licenseServer)
                        drmBuilder.setLicenseRequestHeaders(finalHeaders.filterKeys { it.lowercase() != "user-agent" })
                    }
                }

                mediaItemBuilder.setDrmConfiguration(drmBuilder.build())
            }

            val mediaItem = mediaItemBuilder.build()
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            isLoading = exoPlayer.playbackState == Player.STATE_BUFFERING
            playerError = exoPlayer.playerError
        }
    }

    // ExoPlayer Listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
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
                    isLoading = true
                    playerError = null
                } else {
                    playerError = error
                    isLoading = false
                }
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
            bufferedPosition = exoPlayer.bufferedPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    
    // Fallback for when paused but buffering
    LaunchedEffect(isLoading) {
        while (isLoading) {
            bufferedPosition = exoPlayer.bufferedPosition
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
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showOverlay) {
                                viewModel.selectNextChannel()
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                            viewModel.selectNextChannel()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showOverlay) {
                                viewModel.selectPreviousChannel()
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            viewModel.selectPreviousChannel()
                            true
                        }
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
                            if (channel.sources.isNotEmpty()) {
                                TvIconButton(onClick = { showSourceSelector = true }) {
                                    Icon(Icons.Default.Dns, "Sources", tint = Color.White)
                                }
                            }
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
                            onClick = { viewModel.selectPreviousChannel() },
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
                            onClick = { viewModel.selectNextChannel() },
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
                                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
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

        if (isLoading && !isLocked) {
            CircularProgressIndicator(
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (playerError != null) {
            Column(
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Error, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Playback Error", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        playerError = null
                        isLoading = true
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry", color = Color.White)
                }
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
