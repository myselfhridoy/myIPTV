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

    private val DRM_KEY_REGEX = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$")

    private val MEDIA_PATH_HINT_REGEX = Regex(
        "/(hls|dash|mpegts|manifest|playlist|chunklist|master|index|live)(\\?|/|#|$)",
        RegexOption.IGNORE_CASE
    )

    private val MEDIA_QUERY_HINT_REGEX = Regex(
        "[?&](extension|output|type|format|stream|mimetype|media|container)=(m3u8|mpd|ts|hls|dash|mpegts)",
        RegexOption.IGNORE_CASE
    )

    /**
     * Page-URL markers. These flag URLs the resolver must not hand to
     * ExoPlayer as if they were media. Public so the UI layer can share
     * the exact same classification.
     */
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

    /** Public — used by the UI layer to short-circuit obviously bad source URLs. */
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
            val initialHeaders = mutableMapOf<String, String>()
            channel.userAgent?.let { initialHeaders["User-Agent"] = it }
            channel.cookie?.let { initialHeaders["Cookie"] = it }
            channel.httpReferer?.let { initialHeaders["Referer"] = it }
            channel.origin?.let { initialHeaders["Origin"] = it }
            channel.httpHeaders?.forEach { (k, v) -> initialHeaders[k] = v }
            parsedSource.headers.forEach { (k, v) -> initialHeaders[k] = v }

            // Case-insensitive dedupe: last-wins.
            val deduped = linkedMapOf<String, Pair<String, String>>()
            initialHeaders.forEach { (k, v) -> deduped[k.lowercase()] = k to v }
            initialHeaders.clear()
            deduped.values.forEach { (k, v) -> initialHeaders[k] = v }
            if (initialHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                initialHeaders["User-Agent"] = UnsafeOkHttpClient.DEFAULT_USER_AGENT
            }

            val currentTokenUrl = parsedSource.custom["tokenurl"] ?: channel.tokenUrl
            val currentTokenMatch = parsedSource.custom["tokenmatch"] ?: channel.tokenMatch
            val currentTokenReplace = parsedSource.custom["tokenreplace"] ?: channel.tokenReplace
            val currentTokenId = parsedSource.custom["tokenid"]?.toIntOrNull() ?: channel.tokenId
            val currentPlayRef = parsedSource.custom["playref"]

            // ---- DRM extraction from URL custom params ---------------------
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

            // ---- Try token/page resolver first -----------------------------
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
                    android.util.Log.w("MediaResolver", "TokenParser timed out or failed for $rawSourceUrl")
                }
            }

            // ---- Fall back to generic redirect / playlist sniffing ---------
            if (resolved == null) {
                val tokenLower = currentTokenUrl?.trim()?.lowercase().orEmpty()
                val mustUseResolver = tokenLower == "streambd" || tokenLower == "streamplay" ||
                    tokenLower == "crichd" || tokenLower == "dlhd" ||
                    Regex("""^[\\/]?\d+$""").matches(tokenLower)
                if (mustUseResolver) return@withContext null
                resolved = resolveRedirectsAndPlaylists(rawSourceUrl, initialHeaders)
            }

            var finalUrl = resolved?.url ?: rawSourceUrl
            val rawFinalHeaders = resolved?.headers ?: initialHeaders
            val finalHeaders = TokenParser.applyPlayRefToHeaders(finalUrl, rawFinalHeaders, currentPlayRef)
            val finalDrm = resolved?.drm ?: sourceDrm

            if (resolved == null && isKnownPageUrl(finalUrl)) {
                return@withContext null
            }

            val isCrichd = currentTokenUrl?.trim()?.lowercase() == "crichd" ||
                rawSourceUrl.contains("tokenUrl=crichd", ignoreCase = true) ||
                finalUrl.contains("tokenUrl=crichd", ignoreCase = true)

            // Path-only view — used for TS detection AND mimeType override.
            // (Fixes the previous bug where `.ts?token=x` was not detected.)
            val pathOnly = finalUrl.substringBefore("?").substringBefore("#").lowercase()

            val isMacUrl = isStalkerMacUrl(rawSourceUrl) || isStalkerMacUrl(finalUrl)
            val isTsUrl = finalUrl.contains("extension=ts", ignoreCase = true) ||
                finalUrl.contains("output=ts", ignoreCase = true) ||
                pathOnly.endsWith(".ts")
            val shouldWrapMac = isMacUrl && !isTsUrl

            val shouldWrapInMasterPlaylist = !finalUrl.startsWith("data:", ignoreCase = true) &&
                (resolved?.needsPlaylistWrap == true || shouldWrapMac)

            if (shouldWrapInMasterPlaylist) {
                val bandwidth = 2000000
                val masterPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=$bandwidth\n$finalUrl"
                finalUrl = "data:application/x-mpegURL;base64," +
                    Base64.encodeToString(masterPlaylist.toByteArray(), Base64.NO_WRAP)
            }

            // ---- MimeType detection ----------------------------------------
            var mimeType: String? = if (shouldWrapInMasterPlaylist ||
                finalUrl.startsWith("data:application/x-mpegURL", ignoreCase = true)) {
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

            if (!finalUrl.startsWith("data:", ignoreCase = true) &&
                !pathOnly.contains(".m3u8") && !pathOnly.contains(".mpd") &&
                (pathOnly.endsWith("/mpegts") || pathOnly.endsWith(".ts"))
            ) {
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

        val isKnownFileHost = rawSourceUrl.contains("googleusercontent.com", ignoreCase = true)

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

                    if (bodyStr.contains("#EXTM3U") && (bodyStr.contains("EXTHTTP:") || !isHlsStream)) {
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
                        val fixedPlaylist = "#EXTM3U\n" + bodyStr.replace("# EXT-", "#EXT-")
                        val dataUri = "data:application/x-mpegURL;base64," +
                            Base64.encodeToString(fixedPlaylist.toByteArray(), Base64.NO_WRAP)
                        return ResolvedToken(
                            url = dataUri,
                            headers = initialHeaders,
                            mimeType = MimeTypes.APPLICATION_M3U8,
                            isMasterPlaylist = fixedPlaylist.contains("EXT-X-STREAM-INF")
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
    // =========================================================================
    @androidx.annotation.MainThread
    fun buildMediaSource(
        context: Context,
        params: ResolvedMediaParams
    ): MediaSource {
        val finalUrl = params.url
        val finalHeaders = params.headers
        val mimeType = params.mimeType
        val finalDrm = params.drm

        val okHttpClient = sharedOkHttpClient.newBuilder().apply {
            addNetworkInterceptor { chain ->
                val originalReq = chain.request()
                val builder = originalReq.newBuilder()
                finalHeaders.forEach { (k, v) -> builder.header(k, v) }
                chain.proceed(builder.build())
            }
        }.build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(finalHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItemBuilder = MediaItem.Builder().setUri(finalUrl)
        mimeType?.let { mediaItemBuilder.setMimeType(it) }

        // ── Live configuration: sync-friendly, low-latency, and — critically
        //    — with playback speed UNLOCKED so ExoPlayer's auto-sync
        //    correction can nudge audio/video back into alignment.
        //
        //    Previously min/max speed were both 1f, which fully disabled
        //    that correction and was the single biggest cause of A/V drift.
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(10_000)   // 20s → 10s: less room for drift
            .setMinOffsetMs(5_000)
            .setMaxOffsetMs(30_000)
            .setMinPlaybackSpeed(0.97f)  // unlock the sync corrector
            .setMaxPlaybackSpeed(1.03f)
            .build()
        mediaItemBuilder.setLiveConfiguration(liveConfig)

        // ── Error handling: fast-fail on non-recoverable errors so we move
        //    to the next source instead of spinning for two minutes.
        val loadErrorPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val defaultDelay = super.getRetryDelayMsFor(loadErrorInfo)
                if (defaultDelay == C.TIME_UNSET) return C.TIME_UNSET
                return if (loadErrorInfo.errorCount <= 10) {
                    1000L * (1 shl (loadErrorInfo.errorCount - 1).coerceIn(0, 4))
                } else {
                    defaultDelay
                }
            }
            override fun getMinimumLoadableRetryCount(dataType: Int): Int = 10
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

        if (finalUrl.contains("googleusercontent.com", ignoreCase = true)) {
            extractorsFactory.setMatroskaExtractorFlags(MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
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
                        val kidB64 = Base64.encodeToString(kidBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                        val keyB64 = Base64.encodeToString(keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                        clearKeyBytes = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"$keyB64\",\"kid\":\"$kidB64\"}],\"type\":\"temporary\"}".toByteArray()
                        useLocalClearKey = true
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } else if (finalDrm.licenseServer != null) {
                if (finalDrm.type.lowercase() == "clearkey" && finalDrm.licenseServer.trim().startsWith("{")) {
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
                                    val kidBytes = kidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                    val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                    val kidB64 = Base64.encodeToString(kidBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                                    val keyB64 = Base64.encodeToString(keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                                    keysArray.put(JSONObject().apply {
                                        put("kty", "oct"); put("k", keyB64); put("kid", kidB64)
                                    })
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                            clearKeyBytes = JSONObject().apply {
                                put("keys", keysArray); put("type", "temporary")
                            }.toString().toByteArray()
                        }
                        useLocalClearKey = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        clearKeyBytes = jsonStr.toByteArray()
                        useLocalClearKey = true
                    }
                } else {
                    drmBuilder.setLicenseUri(finalDrm.licenseServer)
                    drmBuilder.setLicenseRequestHeaders(finalHeaders.filterKeys { it.lowercase() != "user-agent" })
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
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(localMediaDrmCallback)
                mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
            }
        }

        val mediaItem = mediaItemBuilder.build()
        return mediaSourceFactory.createMediaSource(mediaItem)
    }
}
