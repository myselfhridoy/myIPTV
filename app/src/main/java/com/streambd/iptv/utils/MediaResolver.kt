package com.streambd.iptv.utils

import android.content.Context
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
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

    /** URL extensions that almost always indicate a directly playable file. */
    private val EXT_REGEX = Regex(
        "\\.(m3u8|mpd|mp4|mkv|ts|webm|flv|avi|mov|mpeg|mpg|m4s|f4m|ism|sdp|m4v|3gp)(\\?|#|$|[\"'])",
        RegexOption.IGNORE_CASE
    )

    /** Streaming protocols that ExoPlayer can hand off directly. */
    private val PROTO_REGEX = Regex(
        "^(rtmp|rtsp|udp|srt|rtp|mms)://",
        RegexOption.IGNORE_CASE
    )

    /** URL patterns that suggest an intermediate redirect / shortener. */
    private val REDIRECT_REGEX = Regex(
        "(is\\.gd|tinyurl|bit\\.ly|t\\.co|redirect|url=|goto=)",
        RegexOption.IGNORE_CASE
    )

    /** ClearKey raw key pair (`kid:key`). */
    private val DRM_KEY_REGEX = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$")

    /**
     * Path segments that hint the response is a media manifest even
     * without a `.m3u8`/`.mpd` extension.
     */
    private val MEDIA_PATH_HINT_REGEX = Regex(
        "/(hls|dash|mpegts|manifest|playlist|chunklist|master|index|live)(\\?|/|#|$)",
        RegexOption.IGNORE_CASE
    )

    /** Query parameters that hint at a media format. */
    private val MEDIA_QUERY_HINT_REGEX = Regex(
        "[?&](extension|output|type|format|stream|mimetype|media|container)=(m3u8|mpd|ts|hls|dash|mpegts)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Page-URL markers. These flag URLs the resolver must not hand to
     * ExoPlayer as if they were media.
     */
    private val PAGE_URL_MARKERS = listOf(
        "tokenurl=",
        "portal.php",
        "action=create_link",
        "action=get_profile",
        "action=handshake",
        "/player.php"
    )

    // Max bytes read when sniffing a response body.
    private const val MAX_SNIFF_BYTES = 2L * 1024 * 1024

    /** Stalker MAC param: ?mac=00:1A:79:46:06:F3 (colon, %3A or dash work) */
    private val STALKER_MAC_REGEX = Regex(
        "[?&]mac=(?:[0-9a-f]{2}(?::|%3a|-)){5}[0-9a-f]{2}(?=&|#|$)",
        RegexOption.IGNORE_CASE
    )

    fun isStalkerMacUrl(url: String?): Boolean =
        !url.isNullOrEmpty() && STALKER_MAC_REGEX.containsMatchIn(url)

    // Use a single OkHttpClient instance for better performance and resource
    // management.
    val sharedOkHttpClient: OkHttpClient by lazy {
        UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder().build()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Header sanitization for the PLAYBACK layer
    //
    // Why this exists
    // ---------------
    // The playlist (JS script, M3U file, JSON API, …) is free to declare
    // any HTTP header it wants — that's the source of truth and we do
    // NOT want to strip anything at the parse layer.
    //
    // But some of those headers are *incompatible with OkHttp itself*.
    // OkHttp is not a browser; it manages several headers on its own and
    // breaks if we override them. Before handing a request to the media
    // data source we therefore drop only the specific headers that:
    //
    //   • `Accept-Encoding` — OkHttp sets this to `gzip` internally and
    //     transparently decompresses the response. If we send our own
    //     value (e.g. `gzip, deflate, br, zstd`), OkHttp stops
    //     decompressing, and any response the CDN sends as brotli (`br`)
    //     or `zstd` arrives as binary garbage. The manifest and segments
    //     then fail to parse and playback hangs at STATE_BUFFERING with
    //     a black screen and no audio.
    //
    //   • `Content-Length` — must never be set manually on a GET. Some
    //     servers respond 400 to a request that violates this.
    //
    //   • `Host` — OkHttp sets this from the URL. Overriding it makes
    //     the request go to the wrong virtual host.
    //
    //   • `Connection`, `Keep-Alive`, `Transfer-Encoding`, `TE`, `Upgrade`,
    //     `Proxy-Connection` — connection-level headers managed by the
    //     transport stack.
    //
    //   • `sec-fetch-*`, `sec-ch-ua-*` — browser fetch-metadata / client
    //     hints. Browsers send these alongside a specific TLS
    //     fingerprint. If we forward them from a non-browser client,
    //     a CDN that inspects them may reject the request because the
    //     fingerprint does not match. Dropping them makes the request
    //     look like a normal media client, which the CDN accepts.
    //
    // The playlist keeps declaring them (documentation, portability
    // with other players); only the OkHttp boundary filters them out.
    // ─────────────────────────────────────────────────────────────────────
    private val PLAYBACK_HEADER_BLOCKLIST: Set<String> = setOf(
        // Headers OkHttp must own
        "accept-encoding",
        "content-length",
        "host",
        "connection",
        "keep-alive",
        "transfer-encoding",
        "te",
        "upgrade",
        "proxy-connection",
        // Browser fetch-metadata that confuses non-browser clients
        "sec-fetch-site",
        "sec-fetch-mode",
        "sec-fetch-dest",
        "sec-fetch-user",
        "sec-ch-ua",
        "sec-ch-ua-mobile",
        "sec-ch-ua-platform",
        "sec-ch-ua-arch",
        "sec-ch-ua-model",
        "sec-ch-ua-bitness",
        "sec-ch-ua-full-version",
        "sec-ch-ua-full-version-list",
        "sec-ch-ua-platform-version"
    )

    /**
     * Drop headers that break OkHttp's request handling or its response
     * decompression. Case-insensitive. Original casing of surviving
     * headers is preserved so CDNs that inspect exact casing still
     * recognise them.
     */
    private fun sanitizeHeadersForPlayback(
        headers: Map<String, String>
    ): Map<String, String> {
        if (headers.isEmpty()) return headers
        val out = LinkedHashMap<String, String>(headers.size)
        for ((k, v) in headers) {
            if (k.lowercase() in PLAYBACK_HEADER_BLOCKLIST) continue
            out[k] = v
        }
        return out
    }

    /**
     * Returns true for URLs that are obviously HTML/API pages, not streams.
     */
    fun isKnownPageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()

        if (PAGE_URL_MARKERS.any { lower.contains(it) }) return true

        val path = lower.substringBefore("?").substringBefore("#")
        if (path.endsWith(".html") || path.endsWith(".htm")) return true

        return false
    }

    /**
     * True if the URL looks like a media URL by extension, protocol, path, or
     * query hint.
     */
    private fun looksLikeMediaUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (url.startsWith("data:", ignoreCase = true)) return true
        if (PROTO_REGEX.containsMatchIn(url)) return true
        if (EXT_REGEX.containsMatchIn(url)) return true
        if (MEDIA_PATH_HINT_REGEX.containsMatchIn(url)) return true
        if (MEDIA_QUERY_HINT_REGEX.containsMatchIn(url)) return true
        return false
    }

    /**
     * Cancellable OkHttp call: cancelling the coroutine also cancels the
     * network request.
     */
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

            val initialHeaders = linkedMapOf<String, String>()
            rawHeaders.forEach { (k, v) ->
                val lower = k.lowercase()
                val existing = initialHeaders.keys.firstOrNull { it.lowercase() == lower }
                if (existing != null) initialHeaders.remove(existing)
                initialHeaders[k] = v
            }
            if (initialHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                initialHeaders["User-Agent"] = UnsafeOkHttpClient.DEFAULT_USER_AGENT
            }

            val currentTokenUrl = parsedSource.custom["tokenurl"] ?: channel.tokenUrl
            val currentTokenMatch = parsedSource.custom["tokenmatch"] ?: channel.tokenMatch
            val currentTokenReplace = parsedSource.custom["tokenreplace"] ?: channel.tokenReplace
            val currentTokenId =
                parsedSource.custom["tokenid"]?.toIntOrNull() ?: channel.tokenId
            val currentPlayRef = parsedSource.custom["playref"]

            // ---- DRM extraction from URL custom params --------------------
            var sourceDrm = channel.drm
            if (parsedSource.custom.containsKey("drm-type")) {
                val drmType = parsedSource.custom["drm-type"]
                val drmKey = parsedSource.custom["drm-url"] ?: parsedSource.custom["drm-key"]
                sourceDrm = if (drmKey != null && DRM_KEY_REGEX.matches(drmKey)) {
                    DrmConfig(type = "clearkey", rawKeyPair = drmKey)
                } else {
                    DrmConfig(type = drmType, licenseServer = drmKey)
                }
            }

            val isStalkerOrPortal = rawSourceUrl.contains("portal.php", ignoreCase = true) ||
                rawSourceUrl.contains("action=create_link", ignoreCase = true)

            // ---- Try the token/page resolver first ------------------------
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

            // ---- Fall back to generic redirect / playlist sniffing --------
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

            // ---- Compute stream-type flags BEFORE any wrapping -----------
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
                val bandwidth = 2000000
                val masterPlaylist =
                    "#EXTM3U\n#EXT-X-VERSION:3\n" +
                        "#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=$bandwidth\n$finalUrl"
                finalUrl = "data:application/x-mpegURL;base64," +
                    Base64.encodeToString(masterPlaylist.toByteArray(), Base64.NO_WRAP)
            }

            // ---- MimeType detection ---------------------------------------
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

            // IMPORTANT: We return `finalHeaders` UNCHANGED here. Header
            // sanitization happens later, at the OkHttp boundary, in
            // buildMediaSource(). That way the parse layer stays free of
            // transport-layer decisions, and any other consumer of
            // ResolvedMediaParams still sees the full header set.
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
            val req = Request.Builder().url(rawSourceUrl).apply {
                initialHeaders.forEach { (k, v) -> header(k, v) }
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
    // MediaSource builder
    //
    // This is where the ONLY header filtering for the playback layer
    // happens. The playlist may declare whatever it wants; before we hand
    // the headers to OkHttp / ExoPlayer we drop the small set of headers
    // that break the transport (see PLAYBACK_HEADER_BLOCKLIST).
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
        val mimeType = params.mimeType
        val finalDrm = params.drm

        // ── Header sanitization happens HERE ────────────────────────────
        // Everything downstream (the network interceptor, the data-source
        // factory, the DRM license request headers) uses this sanitized
        // set, so we never accidentally forward a header that would
        // disable OkHttp's transparent decompression.
        val playbackHeaders = sanitizeHeadersForPlayback(params.headers)

        val okHttpClient = sharedOkHttpClient.newBuilder().apply {
            addNetworkInterceptor { chain ->
                val originalReq = chain.request()
                val builder = originalReq.newBuilder()
                // Apply sanitized headers to every request OkHttp makes
                // on behalf of ExoPlayer — including manifest fetches,
                // segment fetches, and redirects.
                playbackHeaders.forEach { (k, v) -> builder.header(k, v) }
                chain.proceed(builder.build())
            }
        }.build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(playbackHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItemBuilder = MediaItem.Builder().setUri(finalUrl)
        mimeType?.let { mediaItemBuilder.setMimeType(it) }

        // ── Live configuration: sync-friendly, low-latency, playback
        //    speed UNLOCKED so ExoPlayer's auto-sync correction can nudge
        //    audio/video back into alignment.
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(10_000)
            .setMinOffsetMs(5_000)
            .setMaxOffsetMs(30_000)
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(1.03f)
            .build()
        mediaItemBuilder.setLiveConfiguration(liveConfig)

        // ── Error handling: shorter retry cadence for faster failover.
        //    The default policy already returns C.TIME_UNSET for
        //    non-retryable errors (parse, 4xx via the datasource layer).
        val loadErrorPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(
                info: LoadErrorHandlingPolicy.LoadErrorInfo
            ): Long {
                val defaultDelay = super.getRetryDelayMsFor(info)
                if (defaultDelay == C.TIME_UNSET) return C.TIME_UNSET
                return if (info.errorCount <= 4) {
                    500L * (1 shl (info.errorCount - 1).coerceIn(0, 3))
                } else defaultDelay
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 3
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
                        val kidB64 = Base64.encodeToString(
                            kidBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                        )
                        val keyB64 = Base64.encodeToString(
                            keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                        )
                        val clearkeyJson =
                            "{\"keys\":[{\"kty\":\"oct\",\"k\":\"$keyB64\",\"kid\":\"$kidB64\"}],\"type\":\"temporary\"}"
                        clearKeyBytes = clearkeyJson.toByteArray()
                        useLocalClearKey = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (finalDrm.licenseServer != null) {
                if (finalDrm.type.lowercase() == "clearkey" &&
                    finalDrm.licenseServer.trim().startsWith("{")
                ) {
                    val jsonStr = finalDrm.licenseServer.trim()
                    try {
                        val jsonObj = JSONObject(jsonStr)
                        if (jsonObj.has("keys")) {
                            clearKeyBytes = jsonStr.toByteArray()
                        } else {
                            val keysArray = JSONArray()
                            jsonObj.keys().forEach { kidHex ->
                                val keyHex = jsonObj.getString(kidHex)
                                try {
                                    val kidBytes = kidHex.chunked(2)
                                        .map { it.toInt(16).toByte() }.toByteArray()
                                    val keyBytes = keyHex.chunked(2)
                                        .map { it.toInt(16).toByte() }.toByteArray()
                                    val kidB64 = Base64.encodeToString(
                                        kidBytes,
                                        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                                    )
                                    val keyB64 = Base64.encodeToString(
                                        keyBytes,
                                        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                                    )
                                    val keyObj = JSONObject().apply {
                                        put("kty", "oct")
                                        put("k", keyB64)
                                        put("kid", kidB64)
                                    }
                                    keysArray.put(keyObj)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            val jwkSet = JSONObject().apply {
                                put("keys", keysArray)
                                put("type", "temporary")
                            }
                            clearKeyBytes = jwkSet.toString().toByteArray()
                        }
                        useLocalClearKey = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        clearKeyBytes = jsonStr.toByteArray()
                        useLocalClearKey = true
                    }
                } else {
                    drmBuilder.setLicenseUri(finalDrm.licenseServer)
                    // License server also gets the sanitized headers —
                    // minus User-Agent (DRM servers usually want their own).
                    drmBuilder.setLicenseRequestHeaders(
                        playbackHeaders.filterKeys {
                            it.lowercase() != "user-agent"
                        }
                    )
                    drmBuilder.setMultiSession(true)
                }
            }

            if (useLocalClearKey || finalDrm.type.lowercase() == "clearkey") {
                drmBuilder.setForceSessionsForAudioAndVideoTracks(true)
            }
            mediaItemBuilder.setDrmConfiguration(drmBuilder.build())

            if (useLocalClearKey && clearKeyBytes != null) {
                val localMediaDrmCallback = LocalMediaDrmCallback(clearKeyBytes)
                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER
                    )
                    .build(localMediaDrmCallback)
                mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
            }
        }

        val mediaItem = mediaItemBuilder.build()
        return mediaSourceFactory.createMediaSource(mediaItem)
    }
}
