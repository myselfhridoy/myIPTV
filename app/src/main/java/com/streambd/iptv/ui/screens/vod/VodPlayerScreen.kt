package com.streambd.iptv.ui.screens.vod

import android.content.pm.ActivityInfo
import android.view.LayoutInflater
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.streambd.iptv.R
import com.streambd.iptv.ui.components.PlayerRevealContainer
import com.streambd.iptv.ui.components.PlayerWindowEffect
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.TrackSelectionDialog
import com.streambd.iptv.ui.screens.live.VideoScaleMode
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import com.streambd.iptv.utils.MediaResolver
import com.streambd.iptv.utils.PlaybackLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_SNIFF_BYTES = 2L * 1024 * 1024

@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController,
    exoPlayer: ExoPlayer
) {
    val context = LocalContext.current
    val exitScope = rememberCoroutineScope()
    val selectedStream by mainViewModel.selectedStream.collectAsState()
    val selectedMedia by tmdbViewModel.selectedMedia.collectAsState()

    if (selectedStream == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) { Text("No stream selected", color = Color.White) }
        return
    }

    val stream = selectedStream!!
    val videoTitle = selectedMedia?.displayTitle

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isLoading by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var isInPiP by remember { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableStateOf(0L) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playerError by remember { mutableStateOf<androidx.media3.common.PlaybackException?>(null) }

    var resolveFailed by remember { mutableStateOf(false) }
    var retryToken by remember { mutableStateOf(0) }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }

    val seekAmount = 10_000L

    PlayerWindowEffect { inPip ->
        isInPiP = inPip
        if (inPip) showOverlay = false
    }

    LaunchedEffect(stream, retryToken) {
        isLoading = true
        resolveFailed = false

        try {
            val initialHeaders = stream.headers?.toMutableMap() ?: mutableMapOf()
            if (initialHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                initialHeaders["User-Agent"] = com.streambd.iptv.utils.UnsafeOkHttpClient.DEFAULT_USER_AGENT
            }

            val currentPlayRef = stream.headers?.entries?.firstOrNull {
                val l = it.key.lowercase()
                l == "playref" || l == "play-ref" || l == "streamref" || l == "stream-ref"
            }?.value
            val isStalkerOrPortal = stream.url.contains("portal.php", ignoreCase = true) ||
                stream.url.contains("action=create_link", ignoreCase = true)

            var resolved: com.streambd.iptv.utils.ResolvedToken? = null
            if (!stream.tokenUrl.isNullOrEmpty() || isStalkerOrPortal ||
                com.streambd.iptv.utils.YouTubeExtractor.isYouTubeUrl(stream.url)
            ) {
                resolved = com.streambd.iptv.utils.TokenParser.resolveTokenForUrl(
                    context = context,
                    baseUrl = stream.url,
                    tokenUrl = stream.tokenUrl,
                    tokenId = stream.tokenId?.toString(),
                    headers = initialHeaders,
                    tokenMatch = stream.tokenMatch,
                    tokenReplace = stream.tokenReplace,
                    playRef = currentPlayRef
                )
            }
            if (resolved == null) {
                val rawSourceUrl = stream.url
                val decodedUrl = try {
                    java.net.URLDecoder.decode(stream.url, "UTF-8")
                } catch (e: Exception) { stream.url }
                val extRegex = Regex(
                    "\\.(m3u8|mpd|mp4|mkv|ts|webm|flv|avi|mov|mpeg|mpg|m4s|f4m|ism|sdp)(\\?|$|[\"'])",
                    RegexOption.IGNORE_CASE
                )
                val protoRegex = Regex("^(rtmp|rtsp|udp|srt)://", RegexOption.IGNORE_CASE)

                val isPhpHlsUrl = rawSourceUrl.contains("live.php", ignoreCase = true) ||
                    rawSourceUrl.contains("extension=m3u8", ignoreCase = true)
                val hasKnownExtensionOrProto = extRegex.containsMatchIn(decodedUrl) ||
                    protoRegex.containsMatchIn(rawSourceUrl) || isPhpHlsUrl
                val redirectShortenerRegex = Regex("(is\\.gd|tinyurl|redirect|url=)", RegexOption.IGNORE_CASE)
                val isKnownFileHost = rawSourceUrl.contains("googleusercontent.com", ignoreCase = true)
                val shouldPreResolve = redirectShortenerRegex.containsMatchIn(rawSourceUrl) ||
                    (!isKnownFileHost && !hasKnownExtensionOrProto)

                resolved = if (shouldPreResolve) {
                    kotlinx.coroutines.withContext<com.streambd.iptv.utils.ResolvedToken?>(
                        kotlinx.coroutines.Dispatchers.IO
                    ) {
                        try {
                            val client = MediaResolver.sharedOkHttpClient
                            val req = okhttp3.Request.Builder().url(stream.url).apply {
                                initialHeaders.forEach { (k, v) -> header(k, v) }
                            }.head().build()

                            var res = client.newCall(req).execute()
                            try {
                                val isPlaylistOrJsonType = isPhpHlsUrl ||
                                    res.header("Content-Type")?.lowercase()?.let {
                                        it.contains("mpegurl") || it.contains("text/") ||
                                            it.contains("application/vnd.apple.mpegurl") ||
                                            it.contains("json") || it.contains("javascript")
                                    } == true

                                var isMasterPlaylist = false
                                if (!res.isSuccessful || isPlaylistOrJsonType) {
                                    res.close()
                                    val getReq = okhttp3.Request.Builder().url(stream.url).apply {
                                        initialHeaders.forEach { (k, v) -> header(k, v) }
                                    }.build()
                                    res = client.newCall(getReq).execute()

                                    val isGetPlaylistOrJsonType = isPhpHlsUrl ||
                                        res.header("Content-Type")?.lowercase()?.let {
                                            it.contains("mpegurl") || it.contains("text/") ||
                                                it.contains("application/vnd.apple.mpegurl") ||
                                                it.contains("json") || it.contains("javascript")
                                        } == true
                                    val contentLength = res.header("Content-Length")?.toLongOrNull() ?: 0L

                                    if (res.isSuccessful && isGetPlaylistOrJsonType && contentLength < 10_000_000L) {
                                        val bodyStr = res.peekBody(MAX_SNIFF_BYTES).string()
                                        isMasterPlaylist = bodyStr.contains("#EXT-X-STREAM-INF")
                                        if (bodyStr.trim().startsWith("{")) {
                                            val stalkerResolved = com.streambd.iptv.utils.TokenParser
                                                .resolveStalkerPortalOrJson(stream.url, initialHeaders, bodyStr)
                                            if (stalkerResolved != null) {
                                                return@withContext stalkerResolved
                                            }
                                        }
                                        val isHlsStream = bodyStr.contains("#EXTINF:") ||
                                            bodyStr.contains("#EXT-X-STREAM-INF")
                                        if (bodyStr.contains("#EXTM3U") && !isHlsStream) {
                                            val parsedChannels = com.streambd.iptv.utils.M3UParser
                                                .parsePlaylist(bodyStr)
                                            if (parsedChannels.isNotEmpty()) {
                                                val first = parsedChannels.first()
                                                val mergedHeaders = initialHeaders.toMutableMap()
                                                first.httpHeaders?.forEach { (k, v) -> mergedHeaders[k] = v }
                                                first.userAgent?.let { mergedHeaders["User-Agent"] = it }
                                                first.cookie?.let { mergedHeaders["Cookie"] = it }
                                                first.httpReferer?.let { mergedHeaders["Referer"] = it }
                                                first.origin?.let { mergedHeaders["Origin"] = it }

                                                val innerParsed = com.streambd.iptv.utils.M3UParser
                                                    .parseUrlHeaders(first.url)
                                                innerParsed.headers.forEach { (k, v) -> mergedHeaders[k] = v }

                                                return@withContext com.streambd.iptv.utils.ResolvedToken(
                                                    url = innerParsed.cleanUrl,
                                                    headers = mergedHeaders,
                                                    mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8,
                                                    isMasterPlaylist = false
                                                )
                                            }
                                        }
                                    }
                                }

                                if (res.isSuccessful) {
                                    val redirectedUrl = res.request.url.toString()
                                    val contentType = res.header("Content-Type")?.lowercase()
                                    val mimeType: String? =
                                        if (contentType?.contains("mpegurl") == true)
                                            androidx.media3.common.MimeTypes.APPLICATION_M3U8
                                        else if (contentType?.contains("dash") == true ||
                                            redirectedUrl.contains(".mpd"))
                                            androidx.media3.common.MimeTypes.APPLICATION_MPD
                                        else com.streambd.iptv.utils.TokenParser
                                            .detectMimeType(redirectedUrl)

                                    val finalUrl = if (redirectedUrl != stream.url || isMasterPlaylist) {
                                        stream.url
                                    } else {
                                        redirectedUrl
                                    }

                                    com.streambd.iptv.utils.ResolvedToken(
                                        url = finalUrl,
                                        headers = initialHeaders,
                                        mimeType = mimeType,
                                        isMasterPlaylist = isMasterPlaylist
                                    )
                                } else null
                            } finally {
                                res.close()
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                } else {
                    null
                }
            }

            var finalUrl = resolved?.url ?: stream.url
            val rawFinalHeaders = resolved?.headers ?: initialHeaders
            val finalHeaders = com.streambd.iptv.utils.TokenParser
                .applyPlayRefToHeaders(finalUrl, rawFinalHeaders, currentPlayRef)
            val finalDrm = resolved?.drm ?: stream.drm

            val isMacUrl = MediaResolver.isStalkerMacUrl(stream.url) ||
                MediaResolver.isStalkerMacUrl(finalUrl)
            val isTsUrl = finalUrl.contains("extension=ts", ignoreCase = true) ||
                finalUrl.contains("output=ts", ignoreCase = true) ||
                finalUrl.endsWith(".ts", ignoreCase = true)
            val shouldWrapMac = isMacUrl && !isTsUrl

            val shouldWrapInMasterPlaylist = !finalUrl.startsWith("data:", ignoreCase = true) &&
                shouldWrapMac

            if (shouldWrapInMasterPlaylist) {
                val bandwidth = 2_000_000
                val masterPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n" +
                    "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=$bandwidth\n$finalUrl"
                finalUrl = "data:application/x-mpegURL;base64," +
                    android.util.Base64.encodeToString(
                        masterPlaylist.toByteArray(),
                        android.util.Base64.NO_WRAP
                    )
            }

            if (exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() != finalUrl) {
                val okHttpClient = MediaResolver.sharedOkHttpClient.newBuilder().apply {
                    addNetworkInterceptor { chain ->
                        val originalReq = chain.request()
                        val builder = originalReq.newBuilder()
                        finalHeaders.forEach { (k, v) -> builder.header(k, v) }
                        chain.proceed(builder.build())
                    }
                }.build()

                val httpDataSourceFactory =
                    androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                val baseDataSourceFactory =
                    androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)
                val dataSourceFactory = androidx.media3.datasource.DataSource.Factory {
                    com.streambd.iptv.utils.PngStrippingDataSource(
                        baseDataSourceFactory.createDataSource()
                    )
                }

                val mediaItemBuilder = MediaItem.Builder().setUri(finalUrl)
                val mimeType = if (shouldWrapInMasterPlaylist ||
                    finalUrl.startsWith("data:application/x-mpegURL", ignoreCase = true)
                ) {
                    androidx.media3.common.MimeTypes.APPLICATION_M3U8
                } else {
                    com.streambd.iptv.utils.TokenParser
                        .detectMimeType(finalUrl, resolved?.mimeType)
                }
                mimeType?.let { mediaItemBuilder.setMimeType(it) }

                val subtitleConfigs = stream.subtitles?.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                        .setMimeType(
                            when {
                                sub.url.endsWith(".vtt", ignoreCase = true) ->
                                    androidx.media3.common.MimeTypes.TEXT_VTT
                                sub.url.endsWith(".srt", ignoreCase = true) ->
                                    androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                                sub.url.endsWith(".ass", ignoreCase = true) ||
                                    sub.url.endsWith(".ssa", ignoreCase = true) ->
                                    androidx.media3.common.MimeTypes.TEXT_SSA
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

                val loadErrorPolicy = object :
                    androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy() {
                    override fun getRetryDelayMsFor(
                        loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
                    ): Long {
                        return if (loadErrorInfo.errorCount <= 5) 1000L
                        else super.getRetryDelayMsFor(loadErrorInfo)
                    }
                    override fun getMinimumLoadableRetryCount(dataType: Int): Int = 5
                }

                val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                    .setMp4ExtractorFlags(
                        androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS
                    )

                if (finalUrl.contains("googleusercontent.com", ignoreCase = true)) {
                    extractorsFactory.setMatroskaExtractorFlags(
                        androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES
                    )
                }

                val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    dataSourceFactory, extractorsFactory
                ).setLoadErrorHandlingPolicy(loadErrorPolicy)

                if (finalDrm != null && finalDrm.type != null) {
                    val drmScheme = when (finalDrm.type.lowercase()) {
                        "widevine" -> C.WIDEVINE_UUID
                        "playready" -> C.PLAYREADY_UUID
                        "clearkey" -> C.CLEARKEY_UUID
                        else -> C.WIDEVINE_UUID
                    }

                    val drmBuilder = MediaItem.DrmConfiguration.Builder(drmScheme)

                    var useLocalClearKey = false
                    var clearKeyBytes: ByteArray? = null

                    if (finalDrm.type.lowercase() == "clearkey" && finalDrm.rawKeyPair != null) {
                        val parts = finalDrm.rawKeyPair.split(":")
                        if (parts.size == 2) {
                            try {
                                val kidBytes = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                val keyBytes = parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                val kidB64 = android.util.Base64.encodeToString(
                                    kidBytes,
                                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                                )
                                val keyB64 = android.util.Base64.encodeToString(
                                    keyBytes,
                                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                                )
                                val clearkeyJson = "{\"keys\":[{\"kty\":\"oct\"," +
                                    "\"k\":\"$keyB64\",\"kid\":\"$kidB64\"}],\"type\":\"temporary\"}"
                                clearKeyBytes = clearkeyJson.toByteArray()
                                useLocalClearKey = true
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    } else if (finalDrm.licenseServer != null) {
                        if (finalDrm.type.lowercase() == "clearkey" &&
                            finalDrm.licenseServer.trim().startsWith("{")
                        ) {
                            clearKeyBytes = finalDrm.licenseServer.trim().toByteArray()
                            useLocalClearKey = true
                        } else {
                            drmBuilder.setLicenseUri(finalDrm.licenseServer)
                            drmBuilder.setLicenseRequestHeaders(
                                finalHeaders.filterKeys { it.lowercase() != "user-agent" }
                            )
                            drmBuilder.setMultiSession(true)
                        }
                    }

                    mediaItemBuilder.setDrmConfiguration(drmBuilder.build())

                    if (useLocalClearKey && clearKeyBytes != null) {
                        val localMediaDrmCallback =
                            androidx.media3.exoplayer.drm.LocalMediaDrmCallback(clearKeyBytes)
                        val drmSessionManager =
                            androidx.media3.exoplayer.drm.DefaultDrmSessionManager.Builder()
                                .setUuidAndExoMediaDrmProvider(
                                    C.CLEARKEY_UUID,
                                    androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER
                                )
                                .build(localMediaDrmCallback)
                        mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
                    }
                }

                val mediaItem = mediaItemBuilder.build()
                val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } else {
                isLoading = false
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
            resolveFailed = true

            PlaybackLogger.logVodPlaybackError(
                context = context,
                title = videoTitle ?: stream.provider,
                streamProvider = stream.provider,
                streamQuality = stream.quality,
                streamUrl = stream.url,
                error = null
            )
        }
    }

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
                playerError = error
                isLoading = false

                PlaybackLogger.logVodPlaybackError(
                    context = context,
                    title = videoTitle ?: stream.provider,
                    streamProvider = stream.provider,
                    streamQuality = stream.quality,
                    streamUrl = stream.url,
                    error = error
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging) currentPosition = exoPlayer.currentPosition
            bufferedPosition = exoPlayer.bufferedPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
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

    // ── Real exit ──
    val finishExit: () -> Unit = {
        exitScope.launch {
            (context as? ComponentActivity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            delay(400)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            navController.popBackStack()
        }
    }

    val requestExit: () -> Unit = {
        if (!exitRequested) exitRequested = true
    }

    BackHandler {
        if (isLocked) {
            showOverlay = true
        } else {
            requestExit()
        }
    }

    PlayerRevealContainer(
        exitRequested = exitRequested,
        forceUpright = isInPiP,
        onExitComplete = finishExit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (!showOverlay) {
                                    currentPosition = seekBy(exoPlayer, -seekAmount); true
                                } else false
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (!showOverlay) {
                                    currentPosition = seekBy(exoPlayer, seekAmount); true
                                } else false
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                            android.view.KeyEvent.KEYCODE_ENTER -> {
                                if (!showOverlay) { showOverlay = true; true } else false
                            }
                            else -> false
                        }
                    } else false
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showOverlay = !showOverlay }
        ) {
            val isCinema = scaleMode == VideoScaleMode.CINEMA

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
                        val exoResizeMode = when (scaleMode) {
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
                            Icon(Icons.Default.Lock, "Unlock", tint = Color.White)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvIconButton(
                                onClick = { requestExit() },
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))

                            if (videoTitle != null) {
                                Box(
                                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = videoTitle, color = Color.White,
                                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else Spacer(modifier = Modifier.weight(1f))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TvIconButton(onClick = { isLocked = true }) {
                                    Icon(Icons.Default.Lock, "Lock", tint = Color.White)
                                }
                                TvIconButton(onClick = {
                                    val modes = VideoScaleMode.entries.toTypedArray()
                                    val nextIndex = (modes.indexOf(scaleMode) + 1) % modes.size
                                    scaleMode = modes[nextIndex]
                                    android.widget.Toast.makeText(
                                        context, scaleMode.title, android.widget.Toast.LENGTH_SHORT
                                    ).show()
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

                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvIconButton(
                                onClick = { currentPosition = seekBy(exoPlayer, -seekAmount) },
                                modifier = Modifier.size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(Icons.Default.Replay10, "Seek Back 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            TvIconButton(
                                onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier.size(64.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .focusRequester(playPauseFocusRequester)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White, modifier = Modifier.size(36.dp)
                                )
                            }
                            TvIconButton(
                                onClick = { currentPosition = seekBy(exoPlayer, seekAmount) },
                                modifier = Modifier.size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(Icons.Default.Forward10, "Seek Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val shownPosition = if (isDragging)
                                (dragFraction * duration).toLong() else currentPosition
                            Text(formatVodTime(shownPosition), color = Color.White, fontSize = 12.sp)
                            Box(
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val bufferFraction = if (duration > 0)
                                    (bufferedPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
                                val playbackFraction = if (duration > 0)
                                    (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
                                LinearProgressIndicator(
                                    progress = { bufferFraction },
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = Color.White.copy(alpha = 0.4f),
                                    trackColor = Color.Gray.copy(alpha = 0.3f)
                                )
                                Slider(
                                    value = if (isDragging) dragFraction else playbackFraction,
                                    onValueChange = { value ->
                                        isDragging = true; dragFraction = value
                                    },
                                    onValueChangeFinished = {
                                        if (duration > 0) {
                                            val pos = (dragFraction * duration).toLong()
                                            exoPlayer.seekTo(pos); currentPosition = pos
                                        }
                                        isDragging = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.Red,
                                        inactiveTrackColor = Color.Transparent
                                    )
                                )
                            }
                            Text(formatVodTime(duration), color = Color.White, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            TvIconButton(onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            }) {
                                Icon(
                                    if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                    else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Volume", tint = Color.White
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
            } else if (playerError != null || resolveFailed) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Error, "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Playback Error", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            playerError = null
                            resolveFailed = false
                            isLoading = true
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                            retryToken++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BioAccent)
                    ) {
                        Icon(Icons.Default.Refresh, "Retry", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", color = Color.White)
                    }
                }
            }

            // Dialogs (Activity is landscape now, so standard Dialog is landscape too)
            if (showTrackSelector) {
                TrackSelectionDialog(
                    player = exoPlayer,
                    onDismiss = { showTrackSelector = false }
                )
            }
        }
    }
}

private fun seekBy(player: ExoPlayer, deltaMs: Long): Long {
    val duration = player.duration
    val raw = player.currentPosition + deltaMs
    val target = if (duration != C.TIME_UNSET && duration > 0) raw.coerceIn(0L, duration)
                 else raw.coerceAtLeast(0L)
    player.seekTo(target)
    return target
}

private fun formatVodTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
           else String.format("%02d:%02d", minutes, seconds)
}
