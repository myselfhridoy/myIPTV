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

    /** Any file-like extension at the end of the path component. */
    private val EXT_ONLY_REGEX = Regex("\\.[a-zA-Z0-9]{2,5}(\\?|$)")

    /**
     * Path segments that hint the response is a media manifest even
     * without a `.m3u8`/`.mpd` extension.
     * (e.g. `/hls`, `/dash`, `/mpegts`, `/manifest`, `/playlist`, `/chunklist`.)
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
     * URL patterns that identify a *page* rather than a stream, regardless of
     * who the provider is. Kept as generic token markers, not provider names.
     */
    private val PAGE_URL_MARKERS = listOf(
        "tokenurl=",            // our own page-marker convention
        "portal.php",           // generic Stalker / IPTV portal
        "action=create_link",   // generic Stalker API action
        "action=get_profile",   // generic Stalker API action
        "action=handshake"      // generic Stalker API action
    )

    // Max bytes read when sniffing a response body. Playlists / JSON are small; this
    // stops a live stream that reports a text-like content type from being read forever.
    private const val MAX_SNIFF_BYTES = 2L * 1024 * 1024

    // Use a single OkHttpClient instance for better performance and resource management.
    val sharedOkHttpClient: OkHttpClient by lazy {
        UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
            .build()
    }

    /**
     * Returns true for URLs that are obviously HTML/API pages, not streams.
     *
     * Generic check — uses marker tokens, not provider names. The markers are the
     * same ones our own M3U pages use to flag "this is not a stream, resolve me".
     * If we don't detect these, the mimeType fallback below would force-assign
     * `application/x-mpegURL` to an extensionless page URL, and ExoPlayer would
     * then fail with "Input does not start with the #EXTM3U header".
     */
    private fun isKnownPageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()

        // Marker tokens in the URL (query, path, or fragment).
        if (PAGE_URL_MARKERS.any { lower.contains(it) }) return true

        // Literal HTML files. `.php` and other server-side extensions are NOT blocked
        // here — many valid streams hide behind `live.php`, `stream.php`, etc.
        val path = lower.substringBefore("?").substringBefore("#")
        if (path.endsWith(".html") || path.endsWith(".htm")) return true

        return false
    }

    /**
     * True if the URL looks like a media URL by extension, protocol, path, or
     * query hint. This is deliberately broad so we don't reject valid streams
     * that come without an explicit `.m3u8` / `.mpd` extension.
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

    /** Cancellable OkHttp call: cancelling the coroutine also cancels the network request. */
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

            // ---- Merge headers from channel metadata + URL custom params ---------
            val initialHeaders = mutableMapOf<String, String>()
            channel.userAgent?.let { initialHeaders["User-Agent"] = it }
            channel.cookie?.let { initialHeaders["Cookie"] = it }
            channel.httpReferer?.let { initialHeaders["Referer"] = it }
            channel.origin?.let { initialHeaders["Origin"] = it }
            channel.httpHeaders?.forEach { (k, v) -> initialHeaders[k] = v }
            parsedSource.headers.forEach { (k, v) -> initialHeaders[k] = v }

            // Case-insensitive dedupe: last-wins for conflicting keys.
            val deduplicatedHeaders = linkedMapOf<String, Pair<String, String>>()
            initialHeaders.forEach { (k, v) ->
                deduplicatedHeaders[k.lowercase()] = k to v
            }
            initialHeaders.clear()
            deduplicatedHeaders.values.forEach { (k, v) -> initialHeaders[k] = v }
            if (initialHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                initialHeaders["User-Agent"] = UnsafeOkHttpClient.DEFAULT_USER_AGENT
            }

            val currentTokenUrl = parsedSource.custom["tokenurl"] ?: channel.tokenUrl
            val currentTokenMatch = parsedSource.custom["tokenmatch"] ?: channel.tokenMatch
            val currentTokenReplace = parsedSource.custom["tokenreplace"] ?: channel.tokenReplace
            val currentTokenId = parsedSource.custom["tokenid"]?.toIntOrNull() ?: channel.tokenId
            val currentPlayRef = parsedSource.custom["playref"]

            // ---- DRM extraction from URL custom params --------------------------
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

            // ---- Try the token/page resolver first ------------------------------
            var resolved: ResolvedToken? = null
            if (!currentTokenUrl.isNullOrEmpty() || isStalkerOrPortal || YouTubeExtractor.isYouTubeUrl(rawSourceUrl)) {
                resolved = TokenParser.resolveTokenForUrl(
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

            // ---- Fall back to generic redirect / playlist sniffing ---------------
            if (resolved == null) {
    val tokenLower = currentTokenUrl?.trim()?.lowercase().orEmpty()
    val mustUseResolver = tokenLower == "streambd" || tokenLower == "streamplay" ||
        Regex("""^[\\/]?\d+$""").matches(tokenLower)
    // এই token-গুলোর resolver ফেইল করলে page/API URL ExoPlayer-কে দেওয়া চলবে না
    if (mustUseResolver) return@withContext null
    resolved = resolveRedirectsAndPlaylists(rawSourceUrl, initialHeaders)
}

            var finalUrl = resolved?.url ?: rawSourceUrl
            val rawFinalHeaders = resolved?.headers ?: initialHeaders
            val finalHeaders = TokenParser.applyPlayRefToHeaders(finalUrl, rawFinalHeaders, currentPlayRef)
            val finalDrm = resolved?.drm ?: sourceDrm

            // ---- Guard: resolver failed and URL is still a page ------------------
            // If nothing resolved and the URL is still a page marker, return null so
            // the ViewModel shows "resolver failed" instead of handing a page URL to
            // ExoPlayer as if it were an HLS manifest.
            if (resolved == null && isKnownPageUrl(finalUrl)) {
                return@withContext null
            }

            // ---- Crichd-style wrap: many CRICHD endpoints return a bare stream URL
            //      without an #EXTM3U header; wrap it in a tiny master playlist.
            val isCrichd = currentTokenUrl?.equals("crichd", ignoreCase = true) == true ||
                rawSourceUrl.contains("tokenUrl=crichd", ignoreCase = true) ||
                finalUrl.contains("tokenUrl=crichd", ignoreCase = true)

            val shouldWrapInMasterPlaylist = !finalUrl.startsWith("data:", ignoreCase = true) && isCrichd

            if (shouldWrapInMasterPlaylist) {
                val bandwidth = 220000
                val masterPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=$bandwidth\n$finalUrl"
                finalUrl = "data:application/x-mpegURL;base64," +
                    Base64.encodeToString(masterPlaylist.toByteArray(), Base64.NO_WRAP)
            }

            // ---- MimeType detection ---------------------------------------------
            var mimeType: String? = if (shouldWrapInMasterPlaylist ||
                finalUrl.startsWith("data:application/x-mpegURL", ignoreCase = true)) {
                MimeTypes.APPLICATION_M3U8
            } else {
                TokenParser.detectMimeType(finalUrl, resolved?.mimeType)
            }

            // Universal mimeType fallback:
            // Only force m3u8 when the URL gives us a positive media hint.
            // Never force m3u8 just because the URL is extensionless — that was the
            // root cause of "Input does not start with the #EXTM3U header".
            if (mimeType == null && !isKnownPageUrl(finalUrl) && looksLikeMediaUrl(finalUrl)) {
                mimeType = when {
                    finalUrl.contains(".mpd", ignoreCase = true) ||
                        finalUrl.contains("dash+xml", ignoreCase = true) ||
                        finalUrl.contains("/dash", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                    else -> MimeTypes.APPLICATION_M3U8
                }
            }

            val pathOnly = finalUrl.substringBefore("?").substringBefore("#").lowercase()
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
            // Never swallow cancellation.
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    // =========================================================================
    // Generic redirect / playlist resolver
    //
    // Handles URLs that aren't handled by TokenParser (no tokenUrl) and that
    // look like they might be a redirect or a raw playlist body.
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

        // PHP files that explicitly declare HLS in their own query string are
        // treated as stream endpoints even though their path has `.php`.
        val isExplicitHlsPhp =
            rawSourceUrl.contains("live.php", ignoreCase = true) ||
            rawSourceUrl.contains("extension=m3u8", ignoreCase = true) ||
            rawSourceUrl.contains("output=m3u8", ignoreCase = true)

        val hasKnownExtensionOrProto =
            EXT_REGEX.containsMatchIn(decodedUrl) ||
            PROTO_REGEX.containsMatchIn(rawSourceUrl) ||
            isExplicitHlsPhp

        val isKnownFileHost = rawSourceUrl.contains("googleusercontent.com", ignoreCase = true)

        // Skip pre-resolve for known page URLs — the resolver path handles them.
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
                    // peekBody caps how much is read, so an endless stream can't hang/OOM here.
                    val bodyStr = res.peekBody(MAX_SNIFF_BYTES).string()
                    isMasterPlaylist = bodyStr.contains("#EXT-X-STREAM-INF")

                    // JSON body that might be a Stalker / API response.
                    if (bodyStr.trim().startsWith("{")) {
                        val stalkerResolved = TokenParser.resolveStalkerPortalOrJson(
                            rawSourceUrl, initialHeaders, bodyStr
                        )
                        if (stalkerResolved != null) return stalkerResolved
                    }

                    val isHlsStream = bodyStr.contains("#EXTINF:") ||
                        bodyStr.contains("EXT-X-STREAM-INF")

                    // Body IS a playlist with custom EXTHTTP headers → parse the first entry.
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

                    // Body is an HLS stream missing the leading #EXTM3U → fix and wrap.
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
    // MediaSource builder — unchanged apart from mimeType routing.
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

        val httpDataSourceFactory = OkHttpDataSource.Factory(sharedOkHttpClient)
            .setDefaultRequestProperties(finalHeaders)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val mediaItemBuilder = MediaItem.Builder().setUri(finalUrl)
        mimeType?.let { mediaItemBuilder.setMimeType(it) }

        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(10000)
            .setMinOffsetMs(5000)
            .setMaxOffsetMs(60000)
            .build()
        mediaItemBuilder.setLiveConfiguration(liveConfig)

        val loadErrorPolicy = object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val defaultDelay = super.getRetryDelayMsFor(loadErrorInfo)
                // Errors the default policy treats as non-retryable (parser error, file
                // not found...) should fail right away instead of retrying 10 times
                // (~2 min) before falling back to the next source.
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
                        val clearkeyJson = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"$keyB64\",\"kid\":\"$kidB64\"}],\"type\":\"temporary\"}"
                        clearKeyBytes = clearkeyJson.toByteArray()
                        useLocalClearKey = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (finalDrm.licenseServer != null) {
                if (finalDrm.type.lowercase() == "clearkey" && finalDrm.licenseServer.trim().startsWith("{")) {
                    val jsonStr = finalDrm.licenseServer.trim()
                    try {
                        val jsonObj = JSONObject(jsonStr)
                        if (jsonObj.has("keys")) {
                            clearKeyBytes = jsonStr.toByteArray()
                        } else {
                            // Legacy {kidHex: keyHex} map → JWK set.
                            val keysArray = JSONArray()
                            jsonObj.keys().forEach { kidHex ->
                                val keyHex = jsonObj.getString(kidHex)
                                try {
                                    val kidBytes = kidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                    val keyBytes = keyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                    val kidB64 = Base64.encodeToString(kidBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                                    val keyB64 = Base64.encodeToString(keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
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
