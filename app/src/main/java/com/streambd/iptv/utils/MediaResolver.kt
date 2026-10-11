package com.streambd.iptv.utils

import android.content.Context
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.Mp4Extractor
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.DrmConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ResolvedMediaParams(
    val url: String,
    val headers: Map<String, String>,
    val mimeType: String?,
    val drm: DrmConfig?
)

@UnstableApi
object MediaResolver {

    // -------------------------------------------------------------------------
    // URL pattern tables — no provider names anywhere.
    // -------------------------------------------------------------------------

    private val EXT_REGEX = Regex(
        "\\.(m3u8|mpd|mp4|mkv|ts|webm|flv|avi|mov|mpeg|mpg|m4s|f4m|ism|sdp|m4v|3gp)(\\?|#|$|[\"'])",
        RegexOption.IGNORE_CASE
    )

    private val PROTO_REGEX = Regex(
        "^(rtmp|rtsp|udp|srt|rtp|mms)://",
        RegexOption.IGNORE_CASE
    )

    private val REDIRECT_REGEX = Regex(
        "(is\\.gd|tinyurl|bit\\.ly|t\\.co|redirect|url=|goto=)",
        RegexOption.IGNORE_CASE
    )

    private val DRM_KEY_REGEX = Regex("[0-9a-fA-F]{32}:[0-9a-fA-F]{32}")

    private val MEDIA_PATH_HINT_REGEX = Regex(
        "/(hls|dash|mpegts|manifest|playlist|chunklist|master|index|live)(\\?|/|#|$)",
        RegexOption.IGNORE_CASE
    )

    private val MEDIA_QUERY_HINT_REGEX = Regex(
        "[?&](extension|output|type|format|stream|mimetype|media|container)=(m3u8|mpd|ts|hls|dash|mpegts)",
        RegexOption.IGNORE_CASE
    )

    private val PAGE_URL_MARKERS = listOf(
        "tokenurl=",
        "portal.php",
        "action=create_link",
        "action=get_profile",
        "action=handshake",
        "/player.php"
    )

    private const val MAX_SNIFF_BYTES = 2L * 1024 * 1024

    private val STALKER_MAC_REGEX = Regex(
        "[?&]mac=(?:[0-9a-f]{2}(?::|%3a|-)){5}[0-9a-f]{2}(?=&|#|$)",
        RegexOption.IGNORE_CASE
    )

    fun isStalkerMacUrl(url: String?): Boolean =
        !url.isNullOrEmpty() && STALKER_MAC_REGEX.containsMatchIn(url)

    val sharedOkHttpClient: OkHttpClient by lazy {
        UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder().build()
    }

    /**
     * Returns true for URLs that are obviously HTML/API pages, not streams.
     * Public so the UI layer can reuse this exact classification.
     */
    fun isKnownPageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()

        if (PAGE_URL_MARKERS.any { lower.contains(it) }) return true

        val path = lower.substringBefore("?").substringBefore("#")
        if (path.endsWith(".html") || path.endsWith(".htm")) return true

        return false
    }

    private fun looksLikeMediaUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (url.startsWith("data:", ignoreCase = true)) return true
        if (PROTO_REGEX.containsMatchIn(url)) return true
        if (EXT_REGEX.containsMatchIn(url)) return true
        if (MEDIA_PATH_HINT_REGEX.containsMatchIn(url)) return true
        if (MEDIA_QUERY_HINT_REGEX.containsMatchIn(url)) return true
        return false
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    // =========================================================================
    // Public entry point
    // =========================================================================
    suspend fun resolveMediaParams(
        context: Context,
        channel: Channel,
        currentSourceUrl: String
    ): ResolvedMediaParams? = withContext(Dispatchers.IO) {
        try {
            val parsedSource = M3UParser.parseUrlHeaders(currentSourceUrl)
            val rawSourceUrl = parsedSource.cleanUrl

            // ---- Merge headers from channel metadata + URL custom params ----
            val rawHeaders = mutableMapOf<String, String>()
            channel.userAgent?.let { rawHeaders["User-Agent"] = it }
            channel.cookie?.let { rawHeaders["Cookie"] = it }
            channel.httpReferer?.let { rawHeaders["Referer"] = it }
            channel.origin?.let { rawHeaders["Origin"] = it }
            channel.httpHeaders?.forEach { (k, v) -> rawHeaders[k] = v }
            parsedSource.headers.forEach { (k, v) -> rawHeaders[k] = v }

            // Case-insensitive dedup: last-wins for conflicting keys.
            val deduped = linkedMapOf<String, Pair<String, String>>()
            rawHeaders.forEach { (k, v) ->
                deduped[k.lowercase()] = k to v
            }
            val initialHeaders = linkedMapOf<String, String>()
            deduped.values.forEach { (k, v) -> initialHeaders[k] = v }

            if (initialHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                initialHeaders["User-Agent"] = UnsafeOkHttpClient.DEFAULT_USER_AGENT
            }

            val currentTokenUrl = parsedSource.custom["tokenurl"] ?: channel.tokenUrl
            val currentTokenMatch = parsedSource.custom["tokenmatch"] ?: channel.tokenMatch
            val currentTokenReplace = parsedSource.custom["tokenreplace"] ?: channel.tokenReplace
            val currentTokenId =
                parsedSource.custom["tokenid"]?.toIntOrNull() ?: channel.tokenId
            val currentPlayRef = parsedSource.custom["playref"]

            // ---- DRM extraction from URL custom params ----
            var sourceDrm = channel.drm
            if (parsedSource.custom.containsKey("drm-type") ||
                parsedSource.custom.containsKey("drm-key") ||
                parsedSource.custom.containsKey("drm-url")
            ) {
                val drmType = parsedSource.custom["drm-type"] ?: "clearkey"
                val drmKey = parsedSource.custom["drm-url"] ?: parsedSource.custom["drm-key"]
                val existingPairs = sourceDrm?.rawKeyPair?.split(",")?.filter { it.isNotBlank() }.orEmpty()
                sourceDrm = if (drmKey != null && DRM_KEY_REGEX.containsMatchIn(drmKey)) {
                    val newPairs = DRM_KEY_REGEX.findAll(drmKey).map { it.value }
                    val mergedPairs = (existingPairs + newPairs).distinct().joinToString(",")
                    DrmConfig(type = "clearkey", rawKeyPair = mergedPairs)
                } else if (drmKey != null && drmKey.trim().startsWith("{")) {
                    DrmConfig(type = "clearkey", licenseServer = drmKey, rawKeyPair = sourceDrm?.rawKeyPair)
                } else {
                    DrmConfig(type = drmType, licenseServer = drmKey, rawKeyPair = sourceDrm?.rawKeyPair)
                }
            }

            val isStalkerOrPortal = rawSourceUrl.contains("portal.php", ignoreCase = true) ||
                rawSourceUrl.contains("action=create_link", ignoreCase = true)

            // ---- Try the token/page resolver first ----
            var resolved: ResolvedToken? = null
            if (!currentTokenUrl.isNullOrEmpty() ||
                isStalkerOrPortal ||
                YouTubeExtractor.isYouTubeUrl(rawSourceUrl)
            ) {
                resolved = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    TokenParser.resolveTokenForUrl(
                        context = context,
                        baseUrl = rawSourceUrl,
                        tokenUrl = currentTokenUrl,
                        tokenId = currentTokenId?.toString(),
                        headers = initialHeaders,
                        tokenMatch = currentTokenMatch,
                        tokenReplace = currentTokenReplace,
                        playRef = currentPlayRef
                    )
                }
                if (resolved == null) {
                    android.util.Log.w(
                        "MediaResolver",
                        "TokenParser resolve timed out or failed for $rawSourceUrl"
                    )
                }
            }

            // ---- Fall back to generic redirect / playlist sniffing ----
            if (resolved == null) {
                val tokenLower = currentTokenUrl?.trim()?.lowercase().orEmpty()
                val mustUseResolver = tokenLower == "streambd" ||
                    tokenLower == "streamplay" ||
                    tokenLower == "crichd" ||
                    tokenLower == "dlhd" ||
                    Regex("""^[\\/]?\d+$""").matches(tokenLower)
                if (mustUseResolver) return@withContext null
                resolved = resolveRedirectsAndPlaylists(rawSourceUrl, initialHeaders)
            }

            var finalUrl = resolved?.url ?: rawSourceUrl
            val rawFinalHeaders = resolved?.headers ?: initialHeaders
            val finalHeaders =
                TokenParser.applyPlayRefToHeaders(finalUrl, rawFinalHeaders, currentPlayRef)
            val finalDrm = resolved?.drm ?: sourceDrm

            if (resolved == null && isKnownPageUrl(finalUrl)) {
                return@withContext null
            }

            // ---- Compute stream-type flags BEFORE any wrapping ----
            val preWrapPath = finalUrl.substringBefore("?").substringBefore("#").lowercase()

            val isMacUrl = isStalkerMacUrl(rawSourceUrl) || isStalkerMacUrl(finalUrl)
            val isTsStream = finalUrl.contains("extension=ts", ignoreCase = true) ||
                finalUrl.contains("output=ts", ignoreCase = true) ||
                preWrapPath.endsWith(".ts") ||
                preWrapPath.endsWith("/mpegts")
            val shouldWrapMac = isMacUrl && !isTsStream

            val shouldWrapInMasterPlaylist = !finalUrl.startsWith("data:", ignoreCase = true) &&
                (resolved?.needsPlaylistWrap == true || shouldWrapMac)

            if (shouldWrapInMasterPlaylist) {
                val bandwidth = 2_000_000
                val masterPlaylist =
                    "#EXTM3U\n#EXT-X-VERSION:3\n" +
                        "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=$bandwidth\n$finalUrl"
                finalUrl = "data:application/x-mpegURL;base64," +
                    Base64.encodeToString(masterPlaylist.toByteArray(), Base64.NO_WRAP)
            }

            // ---- MimeType detection ----
            var mimeType: String? = if (shouldWrapInMasterPlaylist ||
                finalUrl.startsWith("data:application/x-mpegURL", ignoreCase = true)
            ) {
                MimeTypes.APPLICATION_M3U8
            } else {
                TokenParser.detectMimeType(finalUrl, resolved?.mimeType)
            }

            if (mimeType == null && !isKnownPageUrl(finalUrl) && looksLikeMediaUrl(finalUrl)) {
                mimeType = when {
                    finalUrl.contains(".mpd", ignoreCase = true) ||
                        finalUrl.contains("dash+xml", ignoreCase = true) ||
                        finalUrl.contains("/dash", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                    else -> MimeTypes.APPLICATION_M3U8
                }
            }

            if (isTsStream && !finalUrl.startsWith("data:", ignoreCase = true)) {
                mimeType = MimeTypes.VIDEO_MP2T
            }

            return@withContext ResolvedMediaParams(
                url = finalUrl,
                headers = finalHeaders,
                drm = finalDrm,
                mimeType = mimeType
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    // =========================================================================
    // Generic redirect / playlist resolver
    // =========================================================================
    private suspend fun resolveRedirectsAndPlaylists(
        rawSourceUrl: String,
        initialHeaders: Map<String, String>
    ): ResolvedToken? {
        val decodedUrl = try {
            URLDecoder.decode(rawSourceUrl, "UTF-8")
        } catch (e: Exception) {
            rawSourceUrl
        }

        val isExplicitHlsPhp =
            rawSourceUrl.contains("live.php", ignoreCase = true) ||
                rawSourceUrl.contains("extension=m3u8", ignoreCase = true) ||
                rawSourceUrl.contains("output=m3u8", ignoreCase = true)

        val hasKnownExtensionOrProto =
            EXT_REGEX.containsMatchIn(decodedUrl) ||
                PROTO_REGEX.containsMatchIn(rawSourceUrl) ||
                isExplicitHlsPhp

        val isKnownFileHost =
            rawSourceUrl.contains("googleusercontent.com", ignoreCase = true)

        if (isKnownPageUrl(rawSourceUrl)) return null

        val shouldPreResolve = REDIRECT_REGEX.containsMatchIn(rawSourceUrl) ||
            (!isKnownFileHost && !hasKnownExtensionOrProto)

        if (!shouldPreResolve) return null

        try {
            val safeInitialHeaders = mutableMapOf<String, String>()
            initialHeaders.forEach { (k, v) ->
                val lower = k.lowercase()
                if (lower != "host" && lower != "range" && lower != "content-length" && lower != "connection") {
                    if (lower == "accept-encoding") {
                        safeInitialHeaders["Accept-Encoding"] = "gzip, deflate"
                    } else {
                        safeInitialHeaders[k] = v
                    }
                }
            }
            val req = Request.Builder().url(rawSourceUrl).apply {
                safeInitialHeaders.forEach { (k, v) -> header(k, v) }
            }.build()

            sharedOkHttpClient.newCall(req).await().use { res ->
                val isPlaylistOrJsonType = isExplicitHlsPhp ||
                    res.header("Content-Type")?.lowercase()?.let {
                        it.contains("mpegurl") ||
                            it.contains("text/") ||
                            it.contains("application/vnd.apple.mpegurl") ||
                            it.contains("json") ||
                            it.contains("javascript")
                    } == true

                val contentLength = res.header("Content-Length")?.toLongOrNull() ?: 0L
                var isMasterPlaylist = false

                if (res.isSuccessful && isPlaylistOrJsonType && contentLength < 10_000_000L) {
                    val bodyStr = res.peekBody(MAX_SNIFF_BYTES).string()
                    isMasterPlaylist = bodyStr.contains("#EXT-X-STREAM-INF")

                    if (bodyStr.trim().startsWith("{")) {
                        val stalkerResolved = TokenParser.resolveStalkerPortalOrJson(
                            rawSourceUrl, initialHeaders, bodyStr
                        )
                        if (stalkerResolved != null) return stalkerResolved
                    }

                    val isHlsStream = bodyStr.contains("#EXTINF:") ||
                        bodyStr.contains("EXT-X-STREAM-INF")

                    if (bodyStr.contains("#EXTM3U") &&
                        (bodyStr.contains("EXTHTTP:") || !isHlsStream)
                    ) {
                        val parsedChannels = M3UParser.parsePlaylist(bodyStr)
                        if (parsedChannels.isNotEmpty()) {
                            val first = parsedChannels.first()
                            val mergedHeaders = initialHeaders.toMutableMap()
                            first.httpHeaders?.forEach { (k, v) -> mergedHeaders[k] = v }
                            first.userAgent?.let { mergedHeaders["User-Agent"] = it }
                            first.cookie?.let { mergedHeaders["Cookie"] = it }
                            first.httpReferer?.let { mergedHeaders["Referer"] = it }
                            first.origin?.let { mergedHeaders["Origin"] = it }

                            val innerParsed = M3UParser.parseUrlHeaders(first.url)
                            innerParsed.headers.forEach { (k, v) -> mergedHeaders[k] = v }

                            return ResolvedToken(
                                url = innerParsed.cleanUrl,
                                headers = mergedHeaders,
                                mimeType = MimeTypes.APPLICATION_M3U8,
                                isMasterPlaylist = false
                            )
                        }
                    }

                    if (isHlsStream && !bodyStr.trim().startsWith("#EXTM3U")) {
                        return ResolvedToken(
                            url = res.request.url.toString(),
                            headers = initialHeaders,
                            mimeType = MimeTypes.APPLICATION_M3U8,
                            isMasterPlaylist = isMasterPlaylist
                        )
                    }
                }

                if (res.isSuccessful) {
                    val redirectedUrl = res.request.url.toString()
                    val contentType = res.header("Content-Type")?.lowercase()
                    val mimeType: String? = when {
                        contentType?.contains("mpegurl") == true ||
                            redirectedUrl.contains(".m3u8", ignoreCase = true) ||
                            isMasterPlaylist -> MimeTypes.APPLICATION_M3U8
                        contentType?.contains("dash") == true ||
                            redirectedUrl.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                        else -> TokenParser.detectMimeType(redirectedUrl)
                    }

                    return ResolvedToken(
                        url = redirectedUrl,
                        headers = initialHeaders,
                        mimeType = mimeType,
                        isMasterPlaylist = isMasterPlaylist
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // =========================================================================
    // MediaSource builder — matches your 1-week-ago working code
    // =========================================================================
    @androidx.annotation.MainThread
    fun buildMediaSource(
        context: Context,
        params: ResolvedMediaParams
    ): MediaSource {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            "MediaResolver.buildMediaSource must be called on the main thread"
        }

        val finalUrl = params.url
        val finalHeaders = params.headers
        val mimeType = params.mimeType
        val finalDrm = params.drm

        // Pass browser headers (Referer, Origin, sec-fetch-*, etc.) but filter out
        // hop-by-hop and transport headers that break OkHttp's transparent decompression,
        // chunk range loading, or CDN edge host routing.
        val safeHeaders = mutableMapOf<String, String>()
        finalHeaders.forEach { (key, value) ->
            val lower = key.lowercase()
            if (lower != "host" && lower != "range" && lower != "content-length" && lower != "connection") {
                if (lower == "accept-encoding") {
                    safeHeaders["Accept-Encoding"] = "gzip, deflate"
                } else {
                    safeHeaders[key] = value
                }
            }
        }

        val okHttpClient = sharedOkHttpClient.newBuilder().apply {
            addNetworkInterceptor { chain ->
                val originalReq = chain.request()
                val builder = originalReq.newBuilder()
                safeHeaders.forEach { (k, v) -> builder.header(k, v) }
                chain.proceed(builder.build())
            }
        }.build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(safeHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItemBuilder = MediaItem.Builder().setUri(finalUrl)
        mimeType?.let { mediaItemBuilder.setMimeType(it) }

        // ── LiveConfiguration: same window as your old working code ──
        // 20s target / 10s min / 60s max. This is what made the JioTV
        // dynamic MPD channels play in the past. Playback speed is
        // unlocked (0.97–1.03) for A/V sync on weak TV boxes.
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(20_000)
            .setMinOffsetMs(10_000)
            .setMaxOffsetMs(60_000)
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(1.03f)
            .build()
        mediaItemBuilder.setLiveConfiguration(liveConfig)

        // ── Error handling: shorter retry cadence for faster failover ──
        val loadErrorPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(
                info: LoadErrorHandlingPolicy.LoadErrorInfo
            ): Long {
                val defaultDelay = super.getRetryDelayMsFor(info)
                if (defaultDelay == C.TIME_UNSET) return C.TIME_UNSET
                return if (info.errorCount <= 5) {
                    500L * (1 shl (info.errorCount - 1).coerceIn(0, 3))
                } else defaultDelay
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 5
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

        if (finalUrl.contains("googleusercontent.com", ignoreCase = true)) {
            extractorsFactory.setMatroskaExtractorFlags(
                MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES
            )
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)

        if (finalDrm != null && (finalDrm.type != null || !finalDrm.rawKeyPair.isNullOrBlank() || !finalDrm.licenseServer.isNullOrBlank())) {
            var clearKeyParsed: ClearKeyHelper.ClearKeyParsed? = null
            val isClearKey = finalDrm.type?.contains("clearkey", ignoreCase = true) == true ||
                !finalDrm.rawKeyPair.isNullOrBlank()

            if (isClearKey) {
                clearKeyParsed = ClearKeyHelper.parseClearKeys(finalDrm.rawKeyPair, finalDrm.licenseServer)
            }

            val drmScheme = if (clearKeyParsed != null || isClearKey) {
                C.CLEARKEY_UUID
            } else {
                when (finalDrm.type?.lowercase()) {
                    "widevine" -> C.WIDEVINE_UUID
                    "playready" -> C.PLAYREADY_UUID
                    "clearkey" -> C.CLEARKEY_UUID
                    else -> C.WIDEVINE_UUID
                }
            }

            val drmBuilder = MediaItem.DrmConfiguration.Builder(drmScheme)

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
                    safeHeaders.filterKeys { it.lowercase() != "user-agent" }
                )
                drmBuilder.setMultiSession(true)
            }

            if (clearKeyParsed != null || isClearKey) {
                drmBuilder.setForceSessionsForAudioAndVideoTracks(true)
            }
            mediaItemBuilder.setDrmConfiguration(drmBuilder.build())
        }

        val mediaItem = mediaItemBuilder.build()
        return mediaSourceFactory.createMediaSource(mediaItem)
    }
}
