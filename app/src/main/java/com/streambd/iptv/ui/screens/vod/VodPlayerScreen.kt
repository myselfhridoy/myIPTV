package com.streambd.iptv.ui.screens.vod

import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.streambd.iptv.R
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.PlaybackErrorPanel
import com.streambd.iptv.ui.components.PlayerLoadingIndicator
import com.streambd.iptv.ui.components.PlayerRevealContainer
import com.streambd.iptv.ui.components.PlayerWindowEffect
import com.streambd.iptv.ui.components.TrackSelectionDialog
import com.streambd.iptv.ui.components.rememberLoadingElapsed
import com.streambd.iptv.ui.components.rememberPlayerOrientationController
import com.streambd.iptv.ui.components.rememberPlayerProgress
import com.streambd.iptv.ui.screens.live.VideoScaleMode
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import com.streambd.iptv.utils.AnalyticsLogger
import com.streambd.iptv.utils.ClearKeyHelper
import com.streambd.iptv.utils.MediaResolver
import com.streambd.iptv.utils.PlaybackLogger
import com.streambd.iptv.utils.PlayerPreferences
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
    val selectedStream by mainViewModel.selectedStream.collectAsState()
    val selectedMedia by tmdbViewModel.selectedMedia.collectAsState()
    val availableStreams by mainViewModel.availableStreams.collectAsState()

    if (selectedStream == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) { Text("No stream selected", color = Color.White) }
        return
    }

    val stream = selectedStream!!
    val videoTitle = selectedMedia?.displayTitle

    // ── Portrait player detection ──
    val device = LocalDeviceProfile.current
    val isTvDevice = device.isTv
    val portraitEnabled = remember { PlayerPreferences.isPortraitPlayerEnabled(context) }
    val flexible = !isTvDevice && portraitEnabled
    val isLandscapeNow =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val orientationController = rememberPlayerOrientationController()

    // ── Layout / UI state ──
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isLoading by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showTrackSelector by remember { mutableStateOf(false) }
    var isInPiP by remember { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }

    val portraitLayout = flexible && !isLandscapeNow && !isInPiP

    // ── Playback state ──
    val progress = rememberPlayerProgress(exoPlayer, active = showOverlay)
    var playerError by remember { mutableStateOf<androidx.media3.common.PlaybackException?>(null) }

    var resolveFailed by remember { mutableStateOf(false) }
    var retryToken by remember { mutableStateOf(0) }

    // Used only by the loading diagnostic to pick the right label
    // ("Resolving stream…" vs "Buffering…").
    var isResolving by remember(stream, retryToken) { mutableStateOf(false) }

    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    val effectiveScaleMode = if (portraitLayout) VideoScaleMode.FIT else scaleMode

    var isMuted by remember { mutableStateOf(exoPlayer.volume == 0f) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }

    var hasLoggedPlayback by remember(stream, retryToken) { mutableStateOf(false) }
    var backPressedTime by remember { mutableStateOf(0L) }

    val seekAmount = 10_000L

    PlayerWindowEffect(flexible = flexible) { inPip ->
        isInPiP = inPip
        if (inPip) showOverlay = false
    }

    // ── Resolve + setup media source ──
    LaunchedEffect(stream, retryToken) {
        isResolving = true
        isLoading = true
        resolveFailed = false
        hasLoggedPlayback = false

        try {
            val initialHeaders = stream.headers?.toMutableMap() ?: mutableMapOf()
            if (initialHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                initialHeaders["User-Agent"] =
                    com.streambd.iptv.utils.UnsafeOkHttpClient.DEFAULT_USER_AGENT
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
                val tokenLower = stream.tokenUrl?.trim()?.lowercase().orEmpty()
                val mustUseResolver = tokenLower == "streambd" || tokenLower == "streamplay" ||
                    tokenLower == "crichd" || tokenLower == "dlhd" ||
                    Regex("""^[\\/]?\d+$""").matches(tokenLower)
                if (mustUseResolver) {
                    throw IllegalStateException("Resolver failed for tokenUrl=$tokenLower")
                }
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
                val redirectShortenerRegex = Regex(
                    "(is\\.gd|tinyurl|redirect|url=)", RegexOption.IGNORE_CASE
                )
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

            val preWrapPath = finalUrl.substringBefore("?").substringBefore("#").lowercase()

            val isMacUrl = MediaResolver.isStalkerMacUrl(stream.url) ||
                MediaResolver.isStalkerMacUrl(finalUrl)
            val isTsUrl = finalUrl.contains("extension=ts", ignoreCase = true) ||
                finalUrl.contains("output=ts", ignoreCase = true) ||
                preWrapPath.endsWith(".ts") ||
                preWrapPath.endsWith("/mpegts")
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

            val alreadyPlaying =
                exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString() == finalUrl
            if (alreadyPlaying) {
                isLoading = false
                return@LaunchedEffect
            }

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
                    .setDefaultRequestProperties(finalHeaders)

            val dataSourceFactory =
                androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

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
                    info: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
                ): Long {
                    val defaultDelay = super.getRetryDelayMsFor(info)
                    if (defaultDelay == C.TIME_UNSET) return C.TIME_UNSET
                    return if (info.errorCount <= 4) {
                        500L * (1 shl (info.errorCount - 1).coerceIn(0, 3))
                    } else defaultDelay
                }

                override fun getMinimumLoadableRetryCount(dataType: Int): Int = 3
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

                var clearKeyParsed: ClearKeyHelper.ClearKeyParsed? = null
                if (finalDrm.type.lowercase() == "clearkey") {
                    clearKeyParsed = ClearKeyHelper.parseClearKeys(
                        finalDrm.rawKeyPair, finalDrm.licenseServer
                    )
                }

                if (clearKeyParsed != null) {
                    val drmSessionManager = ClearKeyHelper.createClearKeyDrmSessionManager(
                        clearKeyParsed.jwkBytes,
                        clearKeyParsed.kidBytesList
                    )
                    mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
                    drmBuilder.setForceSessionsForAudioAndVideoTracks(true)
                    drmBuilder.setMultiSession(true)
                } else if (finalDrm.licenseServer != null) {
                    drmBuilder.setLicenseUri(finalDrm.licenseServer)
                    drmBuilder.setLicenseRequestHeaders(
                        finalHeaders.filterKeys { it.lowercase() != "user-agent" }
                    )
                    drmBuilder.setMultiSession(true)
                }

                if (clearKeyParsed != null || finalDrm.type.lowercase() == "clearkey") {
                    drmBuilder.setForceSessionsForAudioAndVideoTracks(true)
                }
                mediaItemBuilder.setDrmConfiguration(drmBuilder.build())
            }

            val mediaItem = mediaItemBuilder.build()
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
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
        } finally {
            isResolving = false
        }
    }

    // ── Player listener ──
    DisposableEffect(exoPlayer, stream) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null

                    if (!hasLoggedPlayback) {
                        hasLoggedPlayback = true
                        val vType = when {
                            selectedMedia == null -> "stream"
                            selectedMedia!!.isMovie -> "movie"
                            else -> "tv"
                        }
                        AnalyticsLogger.logVodPlayed(
                            context = context,
                            title = videoTitle ?: stream.provider,
                            type = vType,
                            provider = stream.provider,
                            season = mainViewModel.selectedSeason.value,
                            episode = mainViewModel.selectedEpisode.value
                        )
                    }
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

    LaunchedEffect(showOverlay, isPlaying, isLocked) {
        if (showOverlay) {
            delay(100)
            try {
                if (isLocked) unlockFocusRequester.requestFocus()
                else playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
        if (showOverlay && isPlaying) {
            delay(if (portraitLayout) 3000 else 5000)
            showOverlay = false
        }
    }

    // ── Real exit ──
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

    BackHandler {
        if (isLocked) {
            showOverlay = true
        } else if (flexible && isLandscapeNow) {
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
        val playerSurface: @Composable (Modifier) -> Unit = { surfaceModifier ->
            Box(
                modifier = surfaceModifier
                    .background(Color.Black)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp) {
                            when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (!showOverlay) {
                                        progress.overridePosition(seekBy(exoPlayer, -seekAmount)); true
                                    } else false
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (!showOverlay) {
                                        progress.overridePosition(seekBy(exoPlayer, seekAmount)); true
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

                if (showOverlay && !isInPiP) {
                    VodPlayerOverlay(
                        modifier = Modifier.fillMaxSize(),
                        title = videoTitle,
                        isLocked = isLocked,
                        isPlaying = isPlaying,
                        isMuted = isMuted,
                        progress = progress,
                        playPauseFocusRequester = playPauseFocusRequester,
                        unlockFocusRequester = unlockFocusRequester,
                        onBack = {
                            if (flexible && isLandscapeNow) orientationController.setFullscreen(false)
                            else requestExit()
                        },
                        onUnlock = { isLocked = false },
                        onLock = { isLocked = true },
                        onCycleAspect = {
                            val modes = VideoScaleMode.entries.toTypedArray()
                            val nextIndex = (modes.indexOf(scaleMode) + 1) % modes.size
                            scaleMode = modes[nextIndex]
                            android.widget.Toast.makeText(
                                context, scaleMode.title, android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        onEnterPiP = {
                            val activity = context as? ComponentActivity
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                val params = android.app.PictureInPictureParams.Builder().build()
                                activity?.enterPictureInPictureMode(params)
                            }
                        },
                        onOpenTracks = { showTrackSelector = true },
                        onSeekBack = { progress.overridePosition(seekBy(exoPlayer, -seekAmount)) },
                        onSeekForward = { progress.overridePosition(seekBy(exoPlayer, seekAmount)) },
                        onTogglePlay = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        onSeekTo = { positionMs -> exoPlayer.seekTo(positionMs) },
                        onToggleMute = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        },
                        compact = portraitLayout,
                        showFullscreenButton = flexible,
                        isFullscreen = isLandscapeNow,
                        onToggleFullscreen = {
                            orientationController.setFullscreen(!isLandscapeNow)
                        }
                    )
                }

                // ── Loading diagnostic ──
                val showSpinner = isLoading && !isLocked
                val loadingSeconds = rememberLoadingElapsed(showSpinner)
                val loadingSubtitle = when {
                    loadingSeconds < 5 -> null
                    isResolving -> "Resolving stream…  ${loadingSeconds}s"
                    else -> "Buffering…  ${loadingSeconds}s"
                }
                val loadingRetry: (() -> Unit)? = if (loadingSeconds >= 20) {
                    {
                        playerError = null
                        resolveFailed = false
                        isLoading = true
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        retryToken++
                    }
                } else null

                PlayerLoadingIndicator(
                    visible = showSpinner,
                    modifier = Modifier.align(Alignment.Center),
                    subtitle = loadingSubtitle,
                    onRetry = loadingRetry
                )

                if (!showSpinner && (playerError != null || resolveFailed)) {
                    val primaryText = when {
                        playerError != null -> playerError?.message ?: "Unknown error"
                        resolveFailed ->
                            "The stream could not be resolved or the URL is unreachable."
                        else -> "Unknown error"
                    }
                    PlaybackErrorPanel(
                        modifier = Modifier.align(Alignment.Center),
                        message = primaryText,
                        cause = playerError?.cause?.message,
                        onRetry = {
                            playerError = null
                            resolveFailed = false
                            isLoading = true
                            exoPlayer.stop()
                            exoPlayer.clearMediaItems()
                            retryToken++
                        },
                        compact = portraitLayout
                    )
                }

                if (showTrackSelector) {
                    TrackSelectionDialog(
                        player = exoPlayer,
                        onDismiss = { showTrackSelector = false }
                    )
                }
            }
        } // end playerSurface

        if (portraitLayout) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                playerSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f))

                if (selectedMedia != null && availableStreams.isNotEmpty()) {
                    val m = selectedMedia!!
                    val subtitle = buildString {
                        append(if (m.isMovie) "Movie" else "TV Show")
                        if (!m.displayDate.isNullOrBlank() && m.displayDate.length >= 4) {
                            append("  •  ").append(m.displayDate.take(4))
                        }
                        if (!m.isMovie) {
                            val season = mainViewModel.selectedSeason.value
                            val episode = mainViewModel.selectedEpisode.value
                            if (season != null && episode != null) {
                                append("  •  S${season} E${episode}")
                            }
                        }
                    }
                    PortraitSourcePane(
                        streams = availableStreams,
                        currentStream = stream,
                        mediaTitle = m.displayTitle,
                        mediaSubtitle = subtitle,
                        onSelectStream = { newSource ->
                            mainViewModel.selectStream(newSource)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    PortraitStreamInfoPane(
                        stream = stream,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            playerSurface(Modifier.fillMaxSize())
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
