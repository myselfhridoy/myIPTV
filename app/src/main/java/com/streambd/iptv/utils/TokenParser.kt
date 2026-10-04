package com.streambd.iptv.utils

import android.content.Context
import com.streambd.iptv.data.models.DrmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import android.net.Uri

data class ResolvedToken(
    val url: String,
    val headers: Map<String, String>? = null,
    val drm: DrmConfig? = null,
    val mimeType: String? = null,
    val isMasterPlaylist: Boolean = false,
    val needsPlaylistWrap: Boolean = false
)

object TokenParser {

    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_SNIFF_BYTES = 2L * 1024 * 1024

    // =========================================================================
    // Universal JSON schema — no provider is special-cased.
    // Field names are ordered by priority; first present one wins.
    // =========================================================================
    private val URL_FIELD_PRIORITY = listOf(
        "url", "stream", "stream_url", "streamurl", "streamUrl",
        "link", "src", "source", "source_url", "sourceUrl",
        "file", "filename",
        "play_url", "playurl", "playUrl", "play",
        "manifest", "manifest_url", "manifestUrl",
        "hls", "hls_url", "hlsUrl", "hlsurl",
        "dash", "dash_url", "dashUrl", "dashurl",
        "m3u8", "m3u8_url", "m3u8Url", "m3u8url",
        "mpd", "mpd_url", "mpdUrl", "mpdurl",
        "video", "video_url", "videoUrl", "videourl",
        "content", "content_url", "contentUrl", "contenturl",
        "media", "media_url", "mediaUrl", "mediaurl",
        "address", "path", "uri"
    )

    private val TYPE_FIELD_NAMES = listOf(
        "type", "format", "mimetype", "mime_type", "mimeType",
        "content_type", "contentType", "stream_type", "streamType",
        "protocol", "container"
    )

    private val SUCCESS_FIELD_NAMES = listOf("success", "ok", "status", "result")

    // =========================================================================
    // Stream URL detection (extended for path-based hints like /mpegts, /hls, /dash)
    // =========================================================================
    private val STREAM_EXT_REGEX = Regex(
        "\\.(m3u8|mpd|mp4|mkv|ts|webm|flv|avi|mov|mpeg|mpg|m4s|f4m|ism|sdp|m4v|3gp)(\\?|#|$)",
        RegexOption.IGNORE_CASE
    )
    private val STREAM_PATH_REGEX = Regex(
        "/(mpegts|hls|dash|manifest|playlist|chunklist|live|stream|master|index)(\\?|/|#|$)",
        RegexOption.IGNORE_CASE
    )
    private val STREAM_PARAM_REGEX = Regex(
        "[?&](extension|output|type|format|stream|mimetype|media|container)=(m3u8|mpd|ts|hls|dash|mpegts)",
        RegexOption.IGNORE_CASE
    )
    private val STREAM_PROTO_REGEX = Regex(
        "^(rtmp|rtsp|udp|srt|rtp|mms)://",
        RegexOption.IGNORE_CASE
    )

    private fun isStreamUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return STREAM_EXT_REGEX.containsMatchIn(url) ||
            STREAM_PATH_REGEX.containsMatchIn(url) ||
            STREAM_PARAM_REGEX.containsMatchIn(url) ||
            STREAM_PROTO_REGEX.containsMatchIn(url)
    }

    // =========================================================================
    // Stream validation — probes a URL and checks if the response is real media
    // =========================================================================
    private suspend fun validateStreamUrl(url: String, headers: Map<String, String>?): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (lower.startsWith("data:")) return true
        if (STREAM_PROTO_REGEX.containsMatchIn(lower)) return true

        return withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(url).apply {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "*/*")
                    header("Range", "bytes=0-1024")
                    headers?.forEach { (k, v) ->
                        if (!k.equals("User-Agent", true) &&
                            !k.equals("Accept", true) &&
                            !k.equals("Range", true)) {
                            try { header(k, v) } catch (e: Exception) {}
                        }
                    }
                }.build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful && res.code != 206) return@use false
                    val ct = res.header("Content-Type")?.lowercase() ?: ""
                    if (ct.contains("mpegurl") || ct.contains("dash+xml")) return@use true
                    if (ct.startsWith("video/") || ct.startsWith("audio/")) return@use true
                    if (ct.contains("text/html") || ct.contains("application/json")) return@use false
                    val head = res.peekBody(1024).string().trimStart()
                    head.startsWith("#EXTM3U") ||
                        head.startsWith("#EXT-X-") ||
                        head.contains("<MPD") ||
                        head.contains("ftyp") ||
                        head.contains("#EXTINF")
                }
            } catch (e: Exception) {
                android.util.Log.w("TokenParser", "validateStreamUrl failed for $url: ${e.message}")
                false
            }
        }
    }

    // =========================================================================
    // Main entry point
    // =========================================================================
    suspend fun resolveTokenForUrl(
        context: Context,
        baseUrl: String,
        tokenUrl: String?,
        tokenId: String? = null,
        headers: Map<String, String>? = null,
        tokenMatch: String? = null,
        tokenReplace: String? = null,
        playRef: String? = null
    ): ResolvedToken? = withContext(Dispatchers.IO) {
        val isStalkerOrPortal = baseUrl.contains("portal.php", ignoreCase = true) ||
            baseUrl.contains("action=create_link", ignoreCase = true)

        if (tokenUrl.isNullOrEmpty() && !YouTubeExtractor.isYouTubeUrl(baseUrl) && !isStalkerOrPortal) {
            return@withContext null
        }

        val lowerTokenUrl = tokenUrl?.lowercase() ?: ""
        if (lowerTokenUrl.contains("single_details") || lowerTokenUrl.contains("all_tv_channel")) {
            return@withContext null
        }

        // ─── Stalker / portal ─────────────────────────────────────────────────
        if (lowerTokenUrl == "create_link" || lowerTokenUrl == "stalker" || lowerTokenUrl == "portal" || isStalkerOrPortal) {
            val resolvedFromJson = resolveStalkerPortalOrJson(baseUrl, headers)
            if (resolvedFromJson != null) {
                var finalUrl = resolvedFromJson.url
                if (tokenMatch != null && tokenReplace != null) finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                return@withContext resolvedFromJson.copy(url = finalUrl)
            }
            if (lowerTokenUrl == "create_link" || lowerTokenUrl == "stalker" || lowerTokenUrl == "portal") {
                return@withContext null
            }
        }

        // ─── YouTube ──────────────────────────────────────────────────────────
        if (lowerTokenUrl == "youtube" || lowerTokenUrl == "yt" || YouTubeExtractor.isYouTubeUrl(baseUrl)) {
            val ytToken = YouTubeExtractor.extractStreamUrl(baseUrl)
            if (ytToken != null) return@withContext ytToken
        }

        // ─── streamBD / streamPlay (WebView sniffer) ──────────────────────────
        if (lowerTokenUrl == "streambd" || lowerTokenUrl == "streamplay") {
            if (baseUrl.contains("sportsupa.sbs") || baseUrl.contains("streamed.pk")) {
                try {
                    val uri = Uri.parse(baseUrl)
                    val matchId = uri.getQueryParameter("id")
                    val src = uri.getQueryParameter("src") ?: "admin"
                    val sno = uri.getQueryParameter("sno")?.toIntOrNull() ?: 1
                    if (matchId != null) {
                        var sourceId: String? = null
                        val endpoints = listOf(
                            "https://streamed.pk/api/matches/live",
                            "https://streamed.pk/api/matches/all"
                        )
                        for (endpoint in endpoints) {
                            val req = Request.Builder().url(endpoint).apply {
                                header("User-Agent", USER_AGENT)
                                header("Referer", "https://sportsupa.sbs/")
                            }.build()
                            val res = client.newCall(req).execute()
                            if (res.isSuccessful) {
                                val jsonArray = JSONArray(res.body?.string() ?: "[]")
                                for (i in 0 until jsonArray.length()) {
                                    val matchObj = jsonArray.getJSONObject(i)
                                    if (matchObj.optString("id") == matchId) {
                                        val sourcesArray = matchObj.optJSONArray("sources")
                                        if (sourcesArray != null) {
                                            for (j in 0 until sourcesArray.length()) {
                                                val srcObj = sourcesArray.getJSONObject(j)
                                                if (srcObj.optString("source") == src) {
                                                    sourceId = srcObj.optString("id")
                                                    break
                                                }
                                            }
                                        }
                                        break
                                    }
                                }
                            }
                            if (sourceId != null) break
                        }
                        if (sourceId != null) {
                            val streamReq = Request.Builder().url("https://streamed.pk/api/stream/${src}/${sourceId}").apply {
                                header("User-Agent", USER_AGENT)
                                header("Referer", "https://sportsupa.sbs/")
                            }.build()
                            val streamRes = client.newCall(streamReq).execute()
                            if (streamRes.isSuccessful) {
                                val streamArray = JSONArray(streamRes.body?.string() ?: "[]")
                                var embedUrl: String? = null
                                for (i in 0 until streamArray.length()) {
                                    val stObj = streamArray.getJSONObject(i)
                                    if (stObj.optInt("streamNo") == sno || (sno == 1 && i == 0)) {
                                        embedUrl = stObj.optString("embedUrl")
                                        if (embedUrl.isNullOrBlank()) {
                                            embedUrl = "https://embedstreams.top/embed/${src}/${sourceId}/${sno}"
                                        }
                                        break
                                    }
                                }
                                if (!embedUrl.isNullOrBlank()) {
                                    val sniffed = WebViewSniffer.sniff(context, embedUrl, headers, USER_AGENT)
                                    if (sniffed != null && validateStreamUrl(sniffed.url, sniffed.headers ?: headers)) {
                                        return@withContext sniffed
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            val sniffed = WebViewSniffer.sniff(context, baseUrl, headers, USER_AGENT)
android.util.Log.d("TokenParser", "sniff result for $baseUrl = ${sniffed?.url}")
if (sniffed == null) return@withContext null
val ok = hasManifestExt(sniffed.url) || validateStreamUrl(sniffed.url, sniffed.headers ?: headers)
return@withContext if (ok) sniffed else null
        }

        // ─── InfinityFree ─────────────────────────────────────────────────────
        if (lowerTokenUrl == "if") {
            val bypass = InfinityFreeBypass.resolve(baseUrl, headers)
            if (bypass?.content?.contains("#EXTM3U") == true) {
                var streamUrl = ""
                val streamHeaders = bypass.headers?.toMutableMap() ?: mutableMapOf()
                var drm: DrmConfig? = null
                val lines = bypass.content.split("\n").map { it.trim() }
                for (line in lines) {
                    if (line.isEmpty() || line.startsWith("#EXTM3U")) continue
                    if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
                        val type = line.split("=").getOrNull(1)?.lowercase()?.trim()
                        if (type?.contains("widevine") == true) drm = DrmConfig(type = "widevine")
                        else if (type?.contains("playready") == true) drm = DrmConfig(type = "playready")
                        else if (type?.contains("clearkey") == true) drm = DrmConfig(type = "clearkey")
                        continue
                    }
                    if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key=")) {
                        val keyVal = line.substringAfter("=").trim()
                        if (keyVal.matches(Regex("^[0-9a-f]{32}:[0-9a-f]{32}$", RegexOption.IGNORE_CASE))) {
                            drm = DrmConfig(type = "clearkey", rawKeyPair = keyVal)
                        } else {
                            drm = drm?.copy(licenseServer = keyVal) ?: DrmConfig(type = "unknown", licenseServer = keyVal)
                        }
                        continue
                    }
                    if (line.startsWith("#EXTVLCOPT:")) {
                        val payload = line.substring("#EXTVLCOPT:".length).trim()
                        val sep = payload.indexOf("=")
                        if (sep != -1) {
                            val k = payload.substring(0, sep).trim()
                            val v = payload.substring(sep + 1).trim()
                            val lowerK = k.lowercase()
                            when {
                                lowerK == "http-user-agent" -> streamHeaders["User-Agent"] = v
                                lowerK == "http-referrer" || lowerK == "http-referer" -> streamHeaders["Referer"] = v
                                lowerK == "http-origin" -> streamHeaders["Origin"] = v
                                else -> streamHeaders[k] = v
                            }
                        }
                        continue
                    }
                    if (line.startsWith("#EXTHTTP:")) {
                        try {
                            val jsonStr = line.substringAfter("#EXTHTTP:").trim()
                            val jsonObj = JSONObject(jsonStr)
                            val headersToParse = if (jsonObj.has("headers")) jsonObj.getJSONObject("headers") else jsonObj
                            val keys = headersToParse.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                streamHeaders[k] = headersToParse.getString(k)
                            }
                        } catch (e: Exception) {}
                        continue
                    }
                    if (!line.startsWith("#")) { streamUrl = line; continue }
                }
                if (streamUrl.isNotEmpty()) {
                    if (!streamUrl.startsWith("http")) return@withContext ResolvedToken(url = bypass.url, headers = bypass.headers, mimeType = "application/x-mpegURL")
                    return@withContext ResolvedToken(url = streamUrl, headers = streamHeaders, drm = drm, mimeType = "application/x-mpegURL")
                }
            } else if (bypass?.content != null) {
                val decoded = JSDecoder.decode(context, bypass.content)
                if (decoded != null) {
                    var finalUrl = decoded.url
                    if (tokenMatch != null && tokenReplace != null) finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                    return@withContext ResolvedToken(url = finalUrl, headers = getStreamHeaders(baseUrl, bypass.headers), drm = decoded.drm)
                }
                val crichdRegex = Pattern.compile("return\\s*\\(\\s*\\[(.*?)\\]\\.join\\(", Pattern.CASE_INSENSITIVE)
                val m = crichdRegex.matcher(bypass.content)
                if (m.find()) {
                    val arr = m.group(1)
                    val chars = Regex("[\"']([^\"']*)[\"']").findAll(arr ?: "").map { it.groupValues[1] }.joinToString("").replace("\\/", "/")
                    var finalUrl = chars
                    if (tokenMatch != null && tokenReplace != null) finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                    return@withContext ResolvedToken(url = finalUrl, headers = getStreamHeaders(baseUrl, bypass.headers))
                }
            }
            return@withContext bypass?.let { ResolvedToken(url = it.url, headers = it.headers, mimeType = if (it.url.lowercase().contains(".mpd")) "application/dash+xml" else "application/x-mpegURL") }
        }

        // ─── JSDecode ─────────────────────────────────────────────────────────
        if (lowerTokenUrl == "jsdecode") {
            try {
                val req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> header(k, v) }
                }.build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val html = res.body?.string() ?: ""
                    val decoded = JSDecoder.decode(context, html)
                    if (decoded != null) {
                        var finalUrl = decoded.url
                        if (tokenMatch != null && tokenReplace != null) finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                        return@withContext ResolvedToken(url = finalUrl, headers = getStreamHeaders(baseUrl, headers), drm = decoded.drm)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            val sniffed = WebViewSniffer.sniff(context, baseUrl, headers, USER_AGENT)
android.util.Log.d("TokenParser", "sniff result for $baseUrl = ${sniffed?.url}")
if (sniffed == null) return@withContext null
val ok = hasManifestExt(sniffed.url) || validateStreamUrl(sniffed.url, sniffed.headers ?: headers)
return@withContext if (ok) sniffed else null
        }

        // ─── crichd-family embed pages ─────────────────────────────────────────
        if (lowerTokenUrl == "crichd") {
            try {
                val req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) ->
                        if (!k.equals("User-Agent", ignoreCase = true)) {
                            header(k, v)
                        }
                    }
                }.build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val html = res.body?.string() ?: ""

                    val funcRegex = Regex("function\\s+([a-zA-Z0-9_]+)\\s*\\(\\)\\s*\\{\\s*return\\s*\\(\\s*\\[(.*?)\\]\\.join", RegexOption.IGNORE_CASE)
                    val allFuncs = funcRegex.findAll(html).toList()
                    val targetFunc = allFuncs.find { match ->
                        val name = match.groupValues[1]
                        Regex("\\b$name\\b").findAll(html).count() > 1
                    } ?: allFuncs.lastOrNull()
                    if (targetFunc != null) {
                        val arr = targetFunc.groupValues[2]
                        val chars = Regex("[\"']([^\"']*)[\"']").findAll(arr).map { it.groupValues[1] }.joinToString("").replace("\\/", "/")
                        var finalUrl = chars
                        if (tokenMatch != null && tokenReplace != null) finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                        android.util.Log.d("TokenParser", "crichd: obfuscation pattern matched -> $finalUrl")
                        return@withContext ResolvedToken(
                            url = finalUrl,
                            headers = getStreamHeaders(baseUrl, headers, playRef),
                            needsPlaylistWrap = false
                        )
                    }

                    val normalized = html
                        .replace("\\/", "/")
                        .replace("\\u002F", "/", true)
                        .replace("&amp;", "&")

                    val directM3u8 = Regex("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*").find(normalized)?.value
                    if (directM3u8 != null) {
                        var finalUrl = directM3u8
                        if (tokenMatch != null && tokenReplace != null) finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                        val hdrs = getStreamHeaders(baseUrl, headers, playRef)
                        if (validateStreamUrl(finalUrl, hdrs)) {
                            android.util.Log.d("TokenParser", "crichd: direct m3u8 found -> $finalUrl")
                            return@withContext ResolvedToken(url = finalUrl, headers = hdrs)
                        }
                    }

                    val iframeMatch = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
                    if (iframeMatch != null) {
                        var iframeUrl = iframeMatch.groupValues[1]
                        if (!iframeUrl.startsWith("http")) {
                            try {
                                val parsed = URL(baseUrl)
                                val base = "${parsed.protocol}://${parsed.host}"
                                iframeUrl = base + (if (iframeUrl.startsWith("/")) "" else "/") + iframeUrl
                            } catch (e: Exception) {}
                        }
                        try {
                            val iframeReq = Request.Builder().url(iframeUrl).apply {
                                header("User-Agent", USER_AGENT)
                                header("Referer", baseUrl)
                                headers?.forEach { (k, v) -> if (!k.equals("Referer", true)) header(k, v) }
                            }.build()
                            val iframeRes = client.newCall(iframeReq).execute()
                            if (iframeRes.isSuccessful) {
                                val iframeHtml = (iframeRes.body?.string() ?: "")
                                    .replace("\\/", "/")
                                    .replace("\\u002F", "/", true)
                                    .replace("&amp;", "&")
                                val iframeM3u8 = Regex("https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*").find(iframeHtml)?.value
                                if (iframeM3u8 != null) {
                                    var finalUrl = iframeM3u8
                                    if (tokenMatch != null && tokenReplace != null) finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                                    val hdrs = getStreamHeaders(iframeUrl, headers, playRef)
                                    if (validateStreamUrl(finalUrl, hdrs)) {
                                        android.util.Log.d("TokenParser", "crichd: iframe m3u8 found -> $finalUrl")
                                        return@withContext ResolvedToken(url = finalUrl, headers = hdrs)
                                    }
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            android.util.Log.d("TokenParser", "crichd: no stream found for $baseUrl")
            return@withContext null
        }

        // ─── fetchiframe ──────────────────────────────────────────────────────
        if (lowerTokenUrl == "fetchiframe") {
            try {
                val req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> header(k, v) }
                }.build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val html = res.body?.string() ?: ""
                    val iframeRegex = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']")
                    val match = iframeRegex.find(html)
                    var candidate = if (match != null) match.groupValues[1] else null
                    if (candidate == null) {
                        val m3u8Regex = Regex("[\"']([^\"']+\\.m3u8[^\"]*)[\"']")
                        candidate = m3u8Regex.find(html)?.groupValues?.get(1)
                    }
                    if (candidate != null) {
                        var src = candidate
                        if (tokenMatch != null && tokenReplace != null) src = src.replace(tokenMatch, tokenReplace)
                        val hdrs = getStreamHeaders(baseUrl, headers)
                        if (validateStreamUrl(src, hdrs)) return@withContext ResolvedToken(url = src, headers = hdrs)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            return@withContext null
        }

        // ─── /3 (specific path) ───────────────────────────────────────────────
        if (lowerTokenUrl == "/3") {
            try {
                val req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> header(k, v) }
                }.build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val html = res.body?.string() ?: ""
                    val streams = extractAllUrls(html).filter { isStreamUrl(it) }
                    for (streamUrl in streams) {
                        val hdrs = getStreamHeaders(baseUrl, headers)
                        if (validateStreamUrl(streamUrl, hdrs)) {
                            val drm = extractDrmFromBody(html)
                            return@withContext ResolvedToken(url = streamUrl, headers = hdrs, drm = drm)
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            return@withContext null
        }

        // ─── fetch (Mini-M3U) ─────────────────────────────────────────────────
        if (lowerTokenUrl == "fetch") {
            try {
                val req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> header(k, v) }
                }.build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val content = res.body?.string() ?: ""
                    if (content.contains("#EXTM3U")) {
                        var streamUrl = ""
                        val streamHeaders = headers?.toMutableMap() ?: mutableMapOf()
                        var drm: DrmConfig? = null
                        val lines = content.split("\n").map { it.trim() }
                        for (line in lines) {
                            if (line.isEmpty() || line.startsWith("#EXTM3U") || line.startsWith("#EXTINF:")) continue
                            if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
                                val type = line.substringAfter("=").trim().lowercase()
                                val parsedType = when {
                                    type.contains("widevine") -> "widevine"
                                    type.contains("playready") -> "playready"
                                    type.contains("clearkey") -> "clearkey"
                                    else -> null
                                }
                                drm = drm?.copy(type = parsedType) ?: DrmConfig(type = parsedType)
                                continue
                            }
                            if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key=")) {
                                val keyVal = line.substringAfter("=").trim()
                                if (keyVal.matches(Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$"))) {
                                    drm = drm?.copy(type = "clearkey", rawKeyPair = keyVal) ?: DrmConfig(type = "clearkey", rawKeyPair = keyVal)
                                } else {
                                    drm = drm?.copy(licenseServer = keyVal) ?: DrmConfig(licenseServer = keyVal)
                                }
                                continue
                            }
                            if (line.startsWith("#EXTVLCOPT:")) {
                                val payload = line.removePrefix("#EXTVLCOPT:").trim()
                                val sep = payload.indexOf("=")
                                if (sep != -1) {
                                    val k = payload.substring(0, sep).trim()
                                    val v = payload.substring(sep + 1).trim()
                                    val lowerK = k.lowercase()
                                    when {
                                        lowerK == "http-user-agent" -> streamHeaders["User-Agent"] = v
                                        lowerK == "http-referrer" || lowerK == "http-referer" -> streamHeaders["Referer"] = v
                                        lowerK == "http-origin" -> streamHeaders["Origin"] = v
                                        else -> streamHeaders[k] = v
                                    }
                                }
                                continue
                            }
                            if (line.startsWith("#EXTHTTP:")) {
                                try {
                                    val jsonStr = line.substringAfter("#EXTHTTP:").trim()
                                    val jsonObj = JSONObject(jsonStr)
                                    val headersToParse = if (jsonObj.has("headers")) jsonObj.getJSONObject("headers") else jsonObj
                                    val keys = headersToParse.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        streamHeaders[k] = headersToParse.getString(k)
                                    }
                                } catch (e: Exception) {}
                                continue
                            }
                            if (!line.startsWith("#")) { streamUrl = line; continue }
                        }
                        if (streamUrl.isNotEmpty()) {
                            if (!streamUrl.startsWith("http")) return@withContext ResolvedToken(url = res.request.url.toString(), headers = headers, mimeType = "application/x-mpegURL")
                            return@withContext ResolvedToken(url = streamUrl, headers = streamHeaders, drm = drm)
                        }
                    } else {
                        return@withContext ResolvedToken(url = res.request.url.toString(), headers = headers)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // ─── dlhd / dlstreams (multi-player scrape + _econfig / atob / source / m3u8) ─
        if (lowerTokenUrl == "dlhd") {
            try {
                // 1) channel ID + entry origin
                val channelId = Regex("(?:id=|premium|stream-)(\\d+)").find(baseUrl)?.groupValues?.get(1)
                    ?: baseUrl.filter { it.isDigit() }

                val parsedBase  = URL(baseUrl)
                val entryOrigin = "${parsedBase.protocol}://${parsedBase.host}" +
                    (if (parsedBase.port != -1) ":${parsedBase.port}" else "")

                // 2) candidate player URLs (same order as Python)
                val playerUrls = mutableListOf(
                    "$entryOrigin/stream/stream-$channelId.php",
                    "$entryOrigin/cast/stream-$channelId.php",
                    "$entryOrigin/watch/stream-$channelId.php",
                    "$entryOrigin/plus/stream-$channelId.php",
                    "$entryOrigin/casting/stream-$channelId.php",
                    "$entryOrigin/player/stream-$channelId.php",
                    "$entryOrigin/hub/stream-$channelId.php",
                    baseUrl
                )

                val seenIframes = mutableSetOf<String>()
                var firstFallback: Pair<String, Map<String, String>>? = null

                for (candidate in playerUrls) {
                    try {
                        // 3) fetch player page
                        val pageReq = Request.Builder().url(candidate).apply {
                            header("User-Agent", USER_AGENT)
                            header("Referer", baseUrl)
                            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                            header("Accept-Language", "en-US,en;q=0.9")
                            header("Upgrade-Insecure-Requests", "1")
                            headers?.forEach { (k, v) -> try { header(k, v) } catch (_: Exception) {} }
                        }.build()

                        val pageRes = try { client.newCall(pageReq).execute() } catch (e: Exception) { null }
                            ?: continue
                        if (!pageRes.isSuccessful) { pageRes.close(); continue }
                        val html = pageRes.body?.string() ?: ""
                        pageRes.close()
                        if (html.isBlank()) continue

                        // 4) find iframe (prefer thatframe > playerFrame > any)
                        var iframeSrc: String? = null

                        val thatFrame = Regex(
                            "<iframe[^>]*\\bid=[\"']thatframe[\"'][^>]*\\bsrc=[\"']([^\"']+)[\"']" +
                                "|<iframe[^>]*\\bsrc=[\"']([^\"']+)[\"'][^>]*\\bid=[\"']thatframe[\"']",
                            RegexOption.IGNORE_CASE
                        ).find(html)
                        if (thatFrame != null) {
                            iframeSrc = thatFrame.groupValues[1].takeIf { it.isNotEmpty() }
                                ?: thatFrame.groupValues[2]
                        }
                        if (iframeSrc.isNullOrBlank()) {
                            val playerFrame = Regex(
                                "<iframe[^>]*\\bid=[\"']playerFrame[\"'][^>]*\\bsrc=[\"']([^\"']+)[\"']" +
                                    "|<iframe[^>]*\\bsrc=[\"']([^\"']+)[\"'][^>]*\\bid=[\"']playerFrame[\"']",
                                RegexOption.IGNORE_CASE
                            ).find(html)
                            if (playerFrame != null) {
                                iframeSrc = playerFrame.groupValues[1].takeIf { it.isNotEmpty() }
                                    ?: playerFrame.groupValues[2]
                            }
                        }
                        if (iframeSrc.isNullOrBlank()) {
                            iframeSrc = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                                .find(html)?.groupValues?.get(1)
                        }
                        if (iframeSrc.isNullOrBlank()) continue

                        iframeSrc = resolveAbsolute(iframeSrc, candidate)
                        if (!seenIframes.add(iframeSrc)) continue

                        // 5) fetch iframe page
                        val iframeReq = Request.Builder().url(iframeSrc).apply {
                            header("User-Agent", USER_AGENT)
                            header("Referer", candidate)
                            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                            header("Accept-Language", "en-US,en;q=0.9")
                            header("Upgrade-Insecure-Requests", "1")
                        }.build()

                        val iframeRes = try { client.newCall(iframeReq).execute() } catch (e: Exception) { null }
                            ?: continue
                        if (!iframeRes.isSuccessful) { iframeRes.close(); continue }
                        val iframeHtml = iframeRes.body?.string() ?: ""
                        iframeRes.close()
                        if (iframeHtml.isBlank()) continue

                        // 6) multi-strategy stream URL extraction
                        var streamUrl: String? = null

                        // 6a) window._econfig = '...' (barecrop.net Player 2)
                        Regex("window\\._econfig\\s*=\\s*['\"]([^'\"]{100,})['\"]", RegexOption.IGNORE_CASE)
                            .find(iframeHtml)?.let { m ->
                                streamUrl = decodeBarecropEconfig(m.groupValues[1])
                                if (!streamUrl.isNullOrEmpty()) {
                                    android.util.Log.d("TokenParser", "dlhd: _econfig decoded -> $streamUrl")
                                }
                            }

                        // 6b) window.atob('...') or atob('...')
                        if (streamUrl.isNullOrEmpty()) {
                            Regex("(?:window\\.)?atob\\(['\"]([A-Za-z0-9+/=\\-_]{20,})['\"]\\)", RegexOption.IGNORE_CASE)
                                .find(iframeHtml)?.let { m ->
                                    try {
                                        val raw = m.groupValues[1].replace('-', '+').replace('_', '/')
                                        val dec = String(android.util.Base64.decode(raw, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
                                        if (dec.startsWith("http")) streamUrl = dec
                                    } catch (_: Exception) {}
                                }
                        }

                        // 6c) source: 'https://...'
                        if (streamUrl.isNullOrEmpty()) {
                            Regex("source\\s*:\\s*['\"](https?://[^'\"]+?)['\"]", RegexOption.IGNORE_CASE)
                                .find(iframeHtml)?.let { m ->
                                    streamUrl = m.groupValues[1].trim()
                                }
                        }

                        // 6d) any .m3u8 in page
                        if (streamUrl.isNullOrEmpty()) {
                            Regex("https?://[^\\s'\"<>\\\\]+\\.m3u8[^\\s'\"<>\\\\]*", RegexOption.IGNORE_CASE)
                                .find(iframeHtml)?.let { m ->
                                    streamUrl = m.value
                                }
                        }

                        // Extract to a stable val — resolves Kotlin smart-cast issues
                        // (streamUrl is captured by the changing closures above).
                        val streamUrlFinal: String = streamUrl ?: continue
                        if (!streamUrlFinal.startsWith("http")) continue

                        // 7) playback headers based on iframe origin
                        val parsedIframe = URL(iframeSrc)
                        val iframeOrigin = "${parsedIframe.protocol}://${parsedIframe.host}" +
                            (if (parsedIframe.port != -1) ":${parsedIframe.port}" else "")

                        val playbackHeaders: Map<String, String> = mutableMapOf(
                            "Referer"        to "$iframeOrigin/",
                            "Origin"         to iframeOrigin,
                            "User-Agent"     to USER_AGENT,
                            "Accept"         to "*/*",
                            "Sec-Fetch-Dest" to "empty",
                            "Sec-Fetch-Mode" to "cors",
                            "Sec-Fetch-Site" to "cross-site"
                        )

                        if (firstFallback == null) firstFallback = streamUrlFinal to playbackHeaders

                        // 8) validate; dead -> next candidate
                        if (validateStreamUrl(streamUrlFinal, playbackHeaders)) {
                            android.util.Log.d("TokenParser", "dlhd: working stream via $candidate -> $streamUrlFinal")
                            return@withContext ResolvedToken(
                                url      = streamUrlFinal,
                                headers  = playbackHeaders,
                                mimeType = detectMimeType(streamUrlFinal)
                            )
                        }
                        android.util.Log.d("TokenParser", "dlhd: stream from $candidate failed validation, trying next")

                    } catch (e: Exception) {
                        android.util.Log.d("TokenParser", "dlhd: candidate $candidate failed: ${e.message}")
                    }
                }

                if (firstFallback != null) {
                    android.util.Log.d("TokenParser", "dlhd: no player validated, returning first URL as fallback")
                    val fb = firstFallback!!
                    return@withContext ResolvedToken(
                        url      = fb.first,
                        headers  = fb.second,
                        mimeType = detectMimeType(fb.first)
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            return@withContext null
        }

        // ─── UNIVERSAL numeric / ID handler ───────────────────────────────────
        val idMatch = tokenUrl?.let { Regex("^[\\\\/]?(\\d+)$").find(it) }
        if (idMatch != null) {
            val preferIndex = idMatch.groupValues[1].toIntOrNull()
            val resolved = resolveGenericApi(baseUrl, headers, preferIndex)
            if (resolved != null) {
                var finalUrl = resolved.url
                if (tokenMatch != null && tokenReplace != null) {
                    finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                }
                return@withContext resolved.copy(url = finalUrl)
            }
        }

        null
    }

    private fun hasManifestExt(url: String): Boolean {
        val p = url.substringBefore("?").substringBefore("#").lowercase()
        return p.endsWith(".m3u8") || p.endsWith(".mpd")
    }

    // =========================================================================
    // barecrop.net _econfig decoder (Player 2)
    // Port of the _0x1b7ade() function in /assets/stream.js.
    // =========================================================================
    private fun decodeBarecropEconfig(enc: String?): String? {
        if (enc.isNullOrEmpty()) return null
        return try {
            val sBytes = android.util.Base64.decode(enc, android.util.Base64.DEFAULT)
            val s = String(sBytes, Charsets.ISO_8859_1)

            val n = 4
            val order = intArrayOf(2, 0, 3, 1)
            val chunkLen = (s.length + n - 1) / n
            val parts = Array(n) { i ->
                val start = i * chunkLen
                if (start >= s.length) ""
                else s.substring(start, minOf((i + 1) * chunkLen, s.length))
            }

            val arr = arrayOf("", "", "", "")
            for (i in 0 until n) {
                var a = parts[i]
                if (a.length < 4) return null
                a = a.substring(0, 3) + a.substring(4)
                val dec = android.util.Base64.decode(a, android.util.Base64.DEFAULT)
                arr[order[i]] = String(dec, Charsets.ISO_8859_1)
            }

            val joined = arr.joinToString("")
            val finalBytes = android.util.Base64.decode(joined, android.util.Base64.DEFAULT)
            val finalStr = String(finalBytes, Charsets.UTF_8)

            val cfg = JSONObject(finalStr)
            val url = cfg.optString("stream_url").takeIf { it.isNotBlank() }
                ?: cfg.optString("stream_url_nop2p").takeIf { it.isNotBlank() }
            url?.takeIf { it.startsWith("http") }?.trim()
        } catch (e: Exception) {
            android.util.Log.d("TokenParser", "decodeBarecropEconfig failed: ${e.message}")
            null
        }
    }

    /** Resolve relative / protocol-relative / absolute URLs. */
    private fun resolveAbsolute(rel: String, base: String): String {
        if (rel.startsWith("http://") || rel.startsWith("https://")) return rel
        return try {
            val b = URL(base)
            val scheme = b.protocol
            val host = b.host
            val portPart = if (b.port != -1) ":${b.port}" else ""
            when {
                rel.startsWith("//") -> "$scheme:$rel"
                rel.startsWith("/")  -> "$scheme://$host$portPart$rel"
                else -> {
                    val dir = (b.path ?: "/").substringBeforeLast("/", "")
                    "$scheme://$host$portPart$dir/$rel"
                }
            }
        } catch (e: Exception) { rel }
    }

    // =========================================================================
    // Universal HTTP endpoint resolver
    // =========================================================================
    private suspend fun resolveGenericApi(
        baseUrl: String,
        headers: Map<String, String>?,
        preferIndex: Int? = null
    ): ResolvedToken? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(baseUrl).apply {
                header("User-Agent", USER_AGENT)
                header("Accept", "*/*")
                headers?.forEach { (k, v) ->
                    if (!k.equals("User-Agent", true) && !k.equals("Accept", true)) {
                        try { header(k, v) } catch (e: Exception) {}
                    }
                }
            }.build()

            client.newCall(req).execute().use { res ->
                val finalUrl = res.request.url.toString()
                val ct = res.header("Content-Type")?.lowercase() ?: ""

                if (finalUrl != baseUrl && isStreamUrl(finalUrl)) {
                    return@use ResolvedToken(
                        url = finalUrl,
                        headers = getStreamHeaders(baseUrl, headers),
                        mimeType = detectMimeType(finalUrl)
                    )
                }

                if (!res.isSuccessful && res.code != 206) return@use null

                val body = res.peekBody(MAX_SNIFF_BYTES).string()
                val trimmed = body.trimStart()

                if (trimmed.startsWith("#EXTM3U")) {
                    val dataUri = "data:application/x-mpegURL;base64," +
                        android.util.Base64.encodeToString(body.toByteArray(), android.util.Base64.NO_WRAP)
                    return@use ResolvedToken(
                        url = dataUri,
                        headers = getStreamHeaders(baseUrl, headers),
                        mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8
                    )
                }

                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    val extracted = extractStreamFromJson(body)
                    if (extracted != null) {
                        val (url, hint) = extracted
                        return@use ResolvedToken(
                            url = url,
                            headers = getStreamHeaders(baseUrl, headers),
                            mimeType = mimeTypeFromHint(hint, url)
                        )
                    }
                }

                val normalized = body
                    .replace("\\/", "/")
                    .replace("\\u002F", "/", true)
                    .replace("\\u003A", ":", true)
                    .replace("&amp;", "&")
                val urls = extractAllUrls(normalized).filter { isStreamUrl(it) }
                if (urls.isNotEmpty()) {
                    val picked = if (preferIndex != null && preferIndex in 1..urls.size) urls[preferIndex - 1] else urls.first()
                    return@use ResolvedToken(
                        url = picked,
                        headers = getStreamHeaders(baseUrl, headers),
                        mimeType = detectMimeType(picked)
                    )
                }

                android.util.Log.d(
                    "TokenParser",
                    "resolveGenericApi saw unrecognised body from $baseUrl (ct=$ct, first=${trimmed.take(80)})"
                )
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("TokenParser", "resolveGenericApi failed for $baseUrl: ${e.message}")
            null
        }
    }

    // =========================================================================
    // Universal JSON extraction
    // =========================================================================
    private fun extractStreamFromJson(body: String): Pair<String, String?>? {
        return try {
            val trimmed = body.trimStart()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(body)
                if (!jsonSuccessOK(json)) return null
                extractStreamFromJsonObject(json)
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val r = extractStreamFromJsonObject(item)
                    if (r != null) return r
                }
                null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractStreamFromJsonObject(json: JSONObject): Pair<String, String?>? {
        for (key in URL_FIELD_PRIORITY) {
            if (json.has(key)) {
                val v = json.optString(key, "").trim()
                if (v.startsWith("http://") || v.startsWith("https://") ||
                    v.startsWith("rtmp://") || v.startsWith("rtsp://") ||
                    v.startsWith("udp://") || v.startsWith("srt://")) {
                    val hint = findTypeHint(json)
                    return v to hint
                }
            }
        }
        val url = walkJson(json) ?: return null
        return url to findTypeHint(json)
    }

    private fun findTypeHint(json: JSONObject): String? {
        for (key in TYPE_FIELD_NAMES) {
            if (json.has(key)) {
                val v = json.optString(key, "").trim().lowercase()
                if (v.isNotBlank()) return v
            }
        }
        return null
    }

    private fun jsonSuccessOK(json: JSONObject): Boolean {
        if (SUCCESS_FIELD_NAMES.none { json.has(it) }) return true
        for (key in SUCCESS_FIELD_NAMES) {
            if (!json.has(key)) continue
            when (val v = json.opt(key)) {
                is Boolean -> return v
                is Number  -> return v.toInt() != 0
                is String  -> {
                    val s = v.lowercase()
                    if (s == "ok" || s == "success" || s == "true" || s == "1") return true
                    if (s == "error" || s == "fail" || s == "false" || s == "0") return false
                }
            }
        }
        return true
    }

    private fun walkJson(value: Any?): String? {
        when (value) {
            is JSONObject -> {
                for (key in value.keys()) {
                    val r = walkJson(value.opt(key))
                    if (r != null) return r
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val r = walkJson(value.opt(i))
                    if (r != null) return r
                }
            }
            is String -> {
                val t = value.trim()
                if ((t.startsWith("http://") || t.startsWith("https://") ||
                     t.startsWith("rtmp://") || t.startsWith("rtsp://")) && isStreamUrl(t)) return t
            }
        }
        return null
    }

    // =========================================================================
    // Map a JSON type hint (or URL) to a Media3 MimeType.
    // =========================================================================
    private fun mimeTypeFromHint(hint: String?, url: String): String? {
        val pathOnly = url.substringBefore("?").substringBefore("#").lowercase()
        if (pathOnly.endsWith("/mpegts") || pathOnly.endsWith(".ts")) {
            return androidx.media3.common.MimeTypes.VIDEO_MP2T
        }
        when (hint?.lowercase()) {
            "hls", "m3u8", "mpegurl", "application/x-mpegurl",
            "application/vnd.apple.mpegurl", "x-mpegurl" ->
                return androidx.media3.common.MimeTypes.APPLICATION_M3U8
            "dash", "mpd", "application/dash+xml", "dash+xml" ->
                return androidx.media3.common.MimeTypes.APPLICATION_MPD
            "ts", "mpegts", "video/mp2t" ->
                return androidx.media3.common.MimeTypes.VIDEO_MP2T
            "mp4", "video/mp4", "m4v" ->
                return androidx.media3.common.MimeTypes.VIDEO_MP4
            "webm", "video/webm", "matroska", "mkv", "video/x-matroska" ->
                return "video/x-matroska"
        }
        return detectMimeType(url)
    }

    // =========================================================================
    // Stalker portal (specific MAC handshake + JSON protocol) — unchanged
    // =========================================================================
    suspend fun resolveStalkerPortalOrJson(
        baseUrl: String,
        headers: Map<String, String>? = null,
        responseBody: String? = null
    ): ResolvedToken? = withContext(Dispatchers.IO) {
        try {
            val isPortal = baseUrl.contains("portal.php", ignoreCase = true)
            val mergedHeaders = getStreamHeaders(baseUrl, headers).toMutableMap()

            fun parseCmd(bodyStr: String, reqUrl: String): String? {
                try {
                    val jsonObj = JSONObject(bodyStr)
                    var cmd = jsonObj.optJSONObject("js")?.optString("cmd")
                        ?: jsonObj.optJSONObject("js")?.optString("url")
                        ?: jsonObj.optString("cmd")
                        ?: jsonObj.optString("url")
                        ?: ""
                    if (cmd.isEmpty()) cmd = walkJson(jsonObj) ?: ""
                    if (cmd.isNotEmpty()) {
                        var streamUrl = cmd.removePrefix("ffmpeg ").removePrefix("ffrtsp ").removePrefix("vlc ").trim()
                        if (streamUrl.startsWith("http://", true) || streamUrl.startsWith("https://", true) || streamUrl.startsWith("rtmp://", true)) {
                            try {
                                val baseAuthority = URL(reqUrl).authority
                                if (!baseAuthority.isNullOrEmpty() &&
                                    (streamUrl.contains("localhost") || streamUrl.contains("127.0.0.1") || streamUrl.contains("0.0.0.0"))) {
                                    streamUrl = streamUrl.replace(Regex("://(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0)(?::\\d+)?/"), "://$baseAuthority/")
                                }
                            } catch (e: Exception) {}
                            return streamUrl
                        }
                    }
                } catch (e: Exception) {}
                return null
            }

            if (responseBody != null) {
                val foundUrl = parseCmd(responseBody, baseUrl)
                if (foundUrl != null) {
                    val drm = extractDrmFromBody(responseBody)
                    val mime = detectMimeType(foundUrl)
                    return@withContext ResolvedToken(url = foundUrl, headers = mergedHeaders, drm = drm, mimeType = mime)
                }
            }

            if (isPortal) {
                try {
                    val portalIdx = baseUrl.indexOf("/portal.php", ignoreCase = true)
                    if (portalIdx != -1) {
                        val portalBase = baseUrl.substring(0, portalIdx)
                        val handshakeUrl = "$portalBase/portal.php?type=stb&action=handshake&JsHttpRequest=1-xml"
                        val hsReq = Request.Builder().url(handshakeUrl).apply {
                            mergedHeaders.forEach { (k, v) -> header(k, v) }
                        }.build()
                        val hsRes = client.newCall(hsReq).execute()
                        if (hsRes.isSuccessful) {
                            val hsHeaders = hsRes.headers("Set-Cookie")
                            val hsBody = hsRes.body?.string() ?: ""
                            val existingCookies = (mergedHeaders.entries.firstOrNull { it.key.equals("Cookie", true) }?.value ?: "")
                                .split(";").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                            for (sc in hsHeaders) {
                                val cNameVal = sc.substringBefore(";").trim()
                                if (cNameVal.isNotEmpty()) {
                                    existingCookies.removeIf { it.substringBefore("=") == cNameVal.substringBefore("=") }
                                    existingCookies.add(cNameVal)
                                }
                            }
                            try {
                                val hsJson = JSONObject(hsBody)
                                val token = hsJson.optJSONObject("js")?.optString("token") ?: hsJson.optString("token")
                                if (!token.isNullOrEmpty()) {
                                    existingCookies.removeIf { it.startsWith("token=") }
                                    existingCookies.add("token=$token")
                                    mergedHeaders["Authorization"] = "Bearer $token"
                                }
                            } catch (e: Exception) {}
                            if (existingCookies.isNotEmpty()) mergedHeaders["Cookie"] = existingCookies.joinToString("; ")
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            val req = Request.Builder().url(baseUrl).apply {
                mergedHeaders.forEach { (k, v) -> header(k, v) }
            }.build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val finalBody = res.body?.string() ?: ""
                val foundUrl = parseCmd(finalBody, baseUrl)
                if (foundUrl != null) {
                    val drm = extractDrmFromBody(finalBody)
                    val mime = detectMimeType(foundUrl)
                    return@withContext ResolvedToken(url = foundUrl, headers = mergedHeaders, drm = drm, mimeType = mime)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        null
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private fun extractDrmFromBody(body: String): DrmConfig? {
        val kidPattern = Regex("(?:const|let|var)?\\s*kid\\s*=\\s*['\"]([a-fA-F0-9]{32})['\"]", RegexOption.IGNORE_CASE)
        val keyPattern = Regex("(?:const|let|var)?\\s*key\\s*=\\s*['\"]([a-fA-F0-9]{32})['\"]", RegexOption.IGNORE_CASE)
        val kidMatch = kidPattern.find(body)
        val keyMatch = keyPattern.find(body)
        if (kidMatch != null && keyMatch != null) {
            return DrmConfig(type = "clearkey", rawKeyPair = "${kidMatch.groupValues[1]}:${keyMatch.groupValues[1]}")
        }
        val pairPattern = Regex("([a-fA-F0-9]{32}):([a-fA-F0-9]{32})")
        val pairMatch = pairPattern.find(body)
        if (pairMatch != null) {
            return DrmConfig(type = "clearkey", rawKeyPair = "${pairMatch.groupValues[1]}:${pairMatch.groupValues[2]}")
        }
        return null
    }

    fun applyPlayRefToHeaders(baseUrl: String, headers: Map<String, String>?, playRef: String? = null): Map<String, String> {
        return getStreamHeaders(baseUrl, headers, playRef)
    }

    private fun getStreamHeaders(baseUrl: String, headers: Map<String, String>?, playRef: String? = null): Map<String, String> {
        val out = headers?.toMutableMap() ?: mutableMapOf()
        try {
            val explicitPlayRef = playRef ?: out.entries.firstOrNull {
                val l = it.key.lowercase()
                l == "playref" || l == "play-ref" || l == "streamref" || l == "stream-ref" || l == "stream-referer" || l == "m3u8-referer" || l == "cdnref" || l == "mediaref" || l == "liveref"
            }?.value
            out.keys.filter {
                val l = it.lowercase()
                l == "playref" || l == "play-ref" || l == "streamref" || l == "stream-ref" || l == "stream-referer" || l == "m3u8-referer" || l == "cdnref" || l == "mediaref" || l == "liveref"
            }.forEach { out.remove(it) }
            if (!explicitPlayRef.isNullOrEmpty()) {
                out["Referer"] = explicitPlayRef
                try {
                    val ru = URL(explicitPlayRef)
                    out["Origin"] = "${ru.protocol}://${ru.host}"
                } catch (e: Exception) {}
                return out
            }
            val existingRefererKey = out.keys.firstOrNull { it.lowercase() == "referer" || it.lowercase() == "referrer" }
            if (existingRefererKey == null) {
                val u = URL(baseUrl)
                out["Referer"] = "${u.protocol}://${u.host}/c/"
                out["Origin"] = "${u.protocol}://${u.host}"
            } else if (!out.keys.any { it.lowercase() == "origin" }) {
                val refVal = out[existingRefererKey]
                if (!refVal.isNullOrEmpty()) {
                    try {
                        val ru = URL(refVal)
                        out["Origin"] = "${ru.protocol}://${ru.host}"
                    } catch (e: Exception) {}
                }
            }
            if (!out.keys.any { it.equals("Cookie", true) }) {
                val macMatch = Regex("[?&]mac=([0-9A-Fa-f:]+)", RegexOption.IGNORE_CASE).find(baseUrl)
                if (macMatch != null) out["Cookie"] = "mac=" + macMatch.groupValues[1] + "; st=1; timezone=GMT"
            }
            if (!out.keys.any { it.equals("User-Agent", true) }) {
                val isStalker = baseUrl.contains("portal.php", true) || baseUrl.contains("action=create_link", true)
                out["User-Agent"] = if (isStalker)
                    "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
                else "VLC/3.0.20 LibVLC/3.0.20 (OTT Navigator; TiviMate)"
            }
        } catch (e: Exception) {}
        return out
    }

    fun detectMimeType(url: String, explicitMimeType: String? = null): String? {
        if (explicitMimeType != null) return explicitMimeType
        val lower = url.lowercase()
        val pathOnly = lower.substringBefore("?").substringBefore("#")
        val decoded = try { URLDecoder.decode(lower, "UTF-8") } catch (e: Exception) { lower }

        if (lower.contains(".mpd") || lower.contains("extension=mpd") ||
            lower.contains("output=mpd") || lower.contains("format=mpd")) {
            return androidx.media3.common.MimeTypes.APPLICATION_MPD
        }
        if (lower.contains(".m3u8") || lower.contains("extension=m3u8") || lower.contains("output=m3u8") ||
            lower.contains("type=m3u8") || lower.contains("format=m3u8") || lower.contains("stream=m3u8") ||
            lower.contains("hls=true") || lower.contains("m3u8=true")) {
            return androidx.media3.common.MimeTypes.APPLICATION_M3U8
        }
        if (pathOnly.endsWith("/mpegts") || pathOnly.endsWith(".ts")) {
            return androidx.media3.common.MimeTypes.VIDEO_MP2T
        }
        if (lower.contains("/hls") || lower.contains("/manifest") || lower.contains("/playlist")) {
            return androidx.media3.common.MimeTypes.APPLICATION_M3U8
        }
        if (lower.contains("/dash/") || lower.contains("/dash?")) {
            return androidx.media3.common.MimeTypes.APPLICATION_MPD
        }
        if (lower.contains(".mkv") || decoded.contains(".mkv")) return "video/x-matroska"
        if (lower.contains(".mp4") || decoded.contains(".mp4")) return androidx.media3.common.MimeTypes.VIDEO_MP4
        return null
    }

    private fun extractAllUrls(payload: String): List<String> {
        val candidates = mutableListOf<String>()
        val decoded = payload.replace("\\/", "/")
            .replace("\\u002F", "/", true)
            .replace("\\u003A", ":", true)
            .replace("\\u0026", "&", true)
        val httpRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        httpRegex.findAll(decoded).forEach { candidates.add(it.value) }
        val protoRegex = Regex("(rtmp|rtsp|udp|srt)://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        protoRegex.findAll(decoded).forEach { candidates.add(it.value) }
        return candidates.distinct()
    }
}
