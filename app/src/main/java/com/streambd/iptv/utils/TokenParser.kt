package com.streambd.iptv.utils

import android.content.Context
import com.streambd.iptv.data.models.DrmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import android.net.Uri

data class ResolvedToken(
    val url: String,
    val headers: Map<String, String>? = null,
    val drm: DrmConfig? = null,
    val mimeType: String? = null
)

object TokenParser {

    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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

        // 0. YouTube extractor via NewPipeExtractor
        if (lowerTokenUrl == "youtube" || lowerTokenUrl == "yt" || YouTubeExtractor.isYouTubeUrl(baseUrl)) {
            val ytToken = YouTubeExtractor.extractStreamUrl(baseUrl)
            if (ytToken != null) return@withContext ytToken
        }

        // 0. streamBD WebViewSniffer
        if (lowerTokenUrl == "streambd" || lowerTokenUrl == "streamplay") {
            if (baseUrl.contains("sportsupa.sbs") || baseUrl.contains("streamed.pk")) {
                try {
                    val uri = Uri.parse(baseUrl)
                    val matchId = uri.getQueryParameter("id")
                    val src = uri.getQueryParameter("src") ?: "admin"
                    val sno = uri.getQueryParameter("sno")?.toIntOrNull() ?: 1
                    
                    if (matchId != null) {
                        var sourceId: String? = null
                        val endpoints = listOf("https://streamed.pk/api/matches/live", "https://streamed.pk/api/matches/all")
                        
                        for (endpoint in endpoints) {
                            val req = Request.Builder().url(endpoint).apply {
                                header("User-Agent", USER_AGENT)
                                header("Referer", "https://sportsupa.sbs/")
                            }.build()
                            val res = client.newCall(req).execute()
                            if (res.isSuccessful) {
                                val jsonArray = org.json.JSONArray(res.body?.string() ?: "[]")
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
                                val streamArray = org.json.JSONArray(streamRes.body?.string() ?: "[]")
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
                                    return@withContext WebViewSniffer.sniff(context, embedUrl, headers, USER_AGENT)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return@withContext WebViewSniffer.sniff(context, baseUrl, headers, USER_AGENT)
        }

        // 1. InfinityFree
        if (lowerTokenUrl == "if") {
            val bypass = InfinityFreeBypass.resolve(baseUrl, headers)
            if (bypass?.content?.contains("#EXTM3U") == true) {
                // Parse mini-M3U (similar to JS logic)
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
                            val jsonObj = org.json.JSONObject(jsonStr)
                            val headersToParse = if (jsonObj.has("headers")) jsonObj.getJSONObject("headers") else jsonObj
                            val keys = headersToParse.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                streamHeaders[k] = headersToParse.getString(k)
                            }
                        } catch (e: Exception) {}
                        continue
                    }
                    if (!line.startsWith("#")) {
                        streamUrl = line
                        continue
                    }
                }
                if (streamUrl.isNotEmpty()) {
                    if (!streamUrl.startsWith("http")) return@withContext ResolvedToken(url = bypass.url, headers = bypass.headers, mimeType = "application/x-mpegURL")
                    return@withContext ResolvedToken(url = streamUrl, headers = streamHeaders, drm = drm, mimeType = "application/x-mpegURL")
                }
            } else if (bypass?.content != null) {
                // Try jsdecode
                val decoded = JSDecoder.decode(context, bypass.content)
                if (decoded != null) {
                    var finalUrl = decoded.url
                    if (tokenMatch != null && tokenReplace != null) finalUrl = finalUrl.replace(tokenMatch, tokenReplace)
                    return@withContext ResolvedToken(url = finalUrl, headers = getStreamHeaders(baseUrl, bypass.headers), drm = decoded.drm)
                }
                // Try crichd
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

        // 2. JSDecode
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
            
            // Fallback to WebViewSniffer if JSDecode fails
            return@withContext WebViewSniffer.sniff(context, baseUrl, headers, USER_AGENT)
        }

        // 3. crichd
        if (lowerTokenUrl == "crichd") {
            try {
                val req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> header(k, v) }
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
                        return@withContext ResolvedToken(url = finalUrl, headers = getStreamHeaders(baseUrl, headers, playRef))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            return@withContext null
        }

        // 4. fetchiframe
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
                    if (match != null) {
                        var src = match.groupValues[1]
                        if (tokenMatch != null && tokenReplace != null) {
                            src = src.replace(tokenMatch, tokenReplace)
                        }
                        return@withContext ResolvedToken(url = src, headers = getStreamHeaders(baseUrl, headers))
                    } else {
                        // Fallback: search for m3u8 in html
                        val m3u8Regex = Regex("[\"']([^\"']+\\.m3u8[^\"]*)[\"']")
                        val m3u8Match = m3u8Regex.find(html)
                        if (m3u8Match != null) {
                            var src = m3u8Match.groupValues[1]
                            if (tokenMatch != null && tokenReplace != null) {
                                src = src.replace(tokenMatch, tokenReplace)
                            }
                            return@withContext ResolvedToken(url = src, headers = getStreamHeaders(baseUrl, headers))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            return@withContext null
        }

        // 5. /3 fallback
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
                    if (streams.isNotEmpty()) {
                        val streamUrl = streams.first()
                        val drm = extractDrmFromBody(html)
                        return@withContext ResolvedToken(url = streamUrl, headers = getStreamHeaders(baseUrl, headers), drm = drm)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            return@withContext null
        }

        // 6. fetch (Generic Mini-M3U Fetch)
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
                                    val jsonObj = org.json.JSONObject(jsonStr)
                                    val headersToParse = if (jsonObj.has("headers")) jsonObj.getJSONObject("headers") else jsonObj
                                    val keys = headersToParse.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        streamHeaders[k] = headersToParse.getString(k)
                                    }
                                } catch (e: Exception) {}
                                continue
                            }
                            if (!line.startsWith("#")) {
                                streamUrl = line
                                continue
                            }
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

        // 7. dlhd
        if (lowerTokenUrl == "dlhd") {
            try {
                var req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    header("Accept-Language", "en-US,en;q=0.9")
                    header("Upgrade-Insecure-Requests", "1")
                    headers?.forEach { (k, v) -> header(k, v) }
                }.build()
                var res = client.newCall(req).execute()
                
                if (res.isSuccessful) {
                    var html = res.body?.string() ?: ""
                    
                    val iframeRegex = Regex("<iframe[^>]*id=[\"']playerFrame[\"'][^>]*src=[\"']([^\"']+)[\"']|<iframe[^>]*src=[\"']([^\"']+)[\"'][^>]*id=[\"']playerFrame[\"']", RegexOption.IGNORE_CASE)
                    val match = iframeRegex.find(html)
                    
                    if (match != null) {
                        var iframeUrl = match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]
                        if (!iframeUrl.startsWith("http")) {
                            val parsedUrl = URL(baseUrl)
                            val base = "${parsedUrl.protocol}://${parsedUrl.host}"
                            iframeUrl = base + (if (iframeUrl.startsWith("/")) "" else "/") + iframeUrl
                        }
                        
                        var currentIframeUrl = iframeUrl
                        req = Request.Builder().url(iframeUrl).apply {
                            header("User-Agent", USER_AGENT)
                            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                            header("Accept-Language", "en-US,en;q=0.9")
                            header("Upgrade-Insecure-Requests", "1")
                            header("Referer", baseUrl)
                        }.build()
                        res = client.newCall(req).execute()
                        
                        if (res.isSuccessful) {
                            var iframeHtml = res.body?.string() ?: ""
                            
                            if (!iframeHtml.contains("window.atob", ignoreCase = true)) {
                                val innerIframeRegex = Regex("<iframe[^>]*src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                                val innerMatch = innerIframeRegex.find(iframeHtml)
                                if (innerMatch != null) {
                                    var innerUrl = innerMatch.groupValues[1]
                                    if (!innerUrl.startsWith("http")) {
                                        if (innerUrl.startsWith("//")) {
                                            innerUrl = "https:$innerUrl"
                                        } else {
                                            val parsedInner = URL(iframeUrl)
                                            val baseInner = "${parsedInner.protocol}://${parsedInner.host}"
                                            innerUrl = baseInner + (if (innerUrl.startsWith("/")) "" else "/") + innerUrl
                                        }
                                    }
                                    currentIframeUrl = innerUrl
                                    req = Request.Builder().url(innerUrl).apply {
                                        header("User-Agent", USER_AGENT)
                                        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                                        header("Accept-Language", "en-US,en;q=0.9")
                                        header("Upgrade-Insecure-Requests", "1")
                                        header("Referer", iframeUrl)
                                    }.build()
                                    res = client.newCall(req).execute()
                                    if (res.isSuccessful) {
                                        iframeHtml = res.body?.string() ?: ""
                                    }
                                }
                            }
                            
                            val b64Regex = Regex("window\\.atob\\(['\"]([A-Za-z0-9+/=\\-_]+)['\"]\\)", RegexOption.IGNORE_CASE)
                            val b64Match = b64Regex.find(iframeHtml)
                            if (b64Match != null) {
                                val base64Str = b64Match.groupValues[1]
                                val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                                val m3u8Url = String(decodedBytes, Charsets.UTF_8)
                                
                                val parsedIframe = URL(currentIframeUrl)
                                val dynamicDomain = if (parsedIframe.port != -1) {
                                    "${parsedIframe.protocol}://${parsedIframe.host}:${parsedIframe.port}"
                                } else {
                                    "${parsedIframe.protocol}://${parsedIframe.host}"
                                }
                                
                                val streamHeaders = getStreamHeaders(baseUrl, headers).toMutableMap()
                                streamHeaders["Referer"] = "$dynamicDomain/"
                                streamHeaders["Origin"] = dynamicDomain
                                streamHeaders["User-Agent"] = USER_AGENT
                                
                                return@withContext ResolvedToken(url = m3u8Url, headers = streamHeaders)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }

        // 8. handlerId logic (omitted complex extraction for brevity, basic fallback)
        val idMatch = tokenUrl?.let { Regex("^[\\\\/]?(\\d+)$").find(it) }
        if (idMatch != null) {
            // Simplified handler logic
            try {
                val req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> header(k, v) }
                }.build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    
                    var jsonStreamUrl: String? = null
                    try {
                        val jsonObj = org.json.JSONObject(body)
                        // Recursively search JSON for any value that is a valid stream URL
                        fun findStreamUrl(json: Any?): String? {
                            when (json) {
                                is org.json.JSONObject -> {
                                    for (key in json.keys()) {
                                        val result = findStreamUrl(json.opt(key))
                                        if (result != null) return result
                                    }
                                }
                                is org.json.JSONArray -> {
                                    for (i in 0 until json.length()) {
                                        val result = findStreamUrl(json.opt(i))
                                        if (result != null) return result
                                    }
                                }
                                is String -> {
                                    val trimmed = json.trim()
                                    if (trimmed.startsWith("http") && isStreamUrl(trimmed)) {
                                        return trimmed
                                    }
                                }
                            }
                            return null
                        }
                        
                        jsonStreamUrl = findStreamUrl(jsonObj)
                    } catch (e: Exception) {}

                    if (!jsonStreamUrl.isNullOrEmpty()) {
                        val drm = extractDrmFromBody(body)
                        return@withContext ResolvedToken(url = jsonStreamUrl, headers = getStreamHeaders(baseUrl, headers), drm = drm)
                    }

                    val streams = extractAllUrls(body).filter { isStreamUrl(it) }
                    if (streams.isNotEmpty()) {
                        val parsedId = idMatch.groupValues[1].toIntOrNull() ?: 1
                        val idx = parsedId - 1
                        val streamUrl = streams.getOrElse(idx) { streams.first() }
                        val drm = extractDrmFromBody(body)
                        return@withContext ResolvedToken(url = streamUrl, headers = getStreamHeaders(baseUrl, headers), drm = drm)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        null
    }

    suspend fun resolveStalkerPortalOrJson(
        baseUrl: String,
        headers: Map<String, String>? = null,
        responseBody: String? = null
    ): ResolvedToken? = withContext(Dispatchers.IO) {
        try {
            val bodyStr = if (responseBody != null) {
                responseBody
            } else {
                val req = Request.Builder().url(baseUrl).apply {
                    header("User-Agent", USER_AGENT)
                    headers?.forEach { (k, v) -> header(k, v) }
                }.build()
                val res = client.newCall(req).execute()
                if (!res.isSuccessful) return@withContext null
                res.body?.string() ?: ""
            }

            val jsonObj = org.json.JSONObject(bodyStr)
            var cmd = jsonObj.optJSONObject("js")?.optString("cmd")
                ?: jsonObj.optJSONObject("js")?.optString("url")
                ?: jsonObj.optString("cmd")
                ?: jsonObj.optString("url")
                ?: ""

            if (cmd.isEmpty()) {
                fun findStreamUrl(json: Any?): String? {
                    when (json) {
                        is org.json.JSONObject -> {
                            for (key in json.keys()) {
                                val result = findStreamUrl(json.opt(key))
                                if (result != null) return result
                            }
                        }
                        is org.json.JSONArray -> {
                            for (i in 0 until json.length()) {
                                val result = findStreamUrl(json.opt(i))
                                if (result != null) return result
                            }
                        }
                        is String -> {
                            val trimmed = json.trim()
                            if (trimmed.startsWith("http") && isStreamUrl(trimmed)) {
                                return trimmed
                            }
                        }
                    }
                    return null
                }
                cmd = findStreamUrl(jsonObj) ?: ""
            }

            if (cmd.isNotEmpty()) {
                var streamUrl = cmd.removePrefix("ffmpeg ").removePrefix("ffrtsp ").removePrefix("vlc ").trim()
                if (streamUrl.startsWith("http://", ignoreCase = true) || 
                    streamUrl.startsWith("https://", ignoreCase = true) || 
                    streamUrl.startsWith("rtmp://", ignoreCase = true)) {
                    
                    val streamHeaders = getStreamHeaders(baseUrl, headers).toMutableMap()
                    val drm = extractDrmFromBody(bodyStr)
                    val mime = detectMimeType(streamUrl)
                    return@withContext ResolvedToken(url = streamUrl, headers = streamHeaders, drm = drm, mimeType = mime)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

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
            // 1. Check if user specified a dedicated PlayRef / stream-referer for video playback
            val explicitPlayRef = playRef ?: out.entries.firstOrNull { 
                val l = it.key.lowercase()
                l == "playref" || l == "play-ref" || l == "streamref" || l == "stream-ref" || l == "stream-referer" || l == "m3u8-referer" || l == "cdnref" || l == "mediaref" || l == "liveref"
            }?.value
            
            // Remove control keys from out map so OkHttp/ExoPlayer only sends clean standard HTTP headers
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
            
            // 2. If no PlayRef is specified, check if Referer already exists
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
            if (!out.keys.any { it.equals("Cookie", ignoreCase = true) }) {
                val macMatch = Regex("[?&]mac=([0-9A-Fa-f:]+)", RegexOption.IGNORE_CASE).find(baseUrl)
                if (macMatch != null) {
                    out["Cookie"] = "mac=" + macMatch.groupValues[1] + "; st=1; timezone=GMT"
                }
            }
            if (!out.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
                out["User-Agent"] = "VLC/3.0.20 LibVLC/3.0.20 (OTT Navigator; TiviMate)"
            }
        } catch (e: Exception) {}
        return out
    }

    fun detectMimeType(url: String, explicitMimeType: String? = null): String? {
        if (explicitMimeType != null) return explicitMimeType
        val lower = url.lowercase()
        if (lower.contains(".mpd") || lower.contains("extension=mpd") || lower.contains("output=mpd") || lower.contains("format=mpd")) {
            return androidx.media3.common.MimeTypes.APPLICATION_MPD
        }
        if (lower.contains(".m3u8") ||
            lower.contains("extension=m3u8") ||
            lower.contains("output=m3u8") ||
            lower.contains("type=m3u8") ||
            lower.contains("format=m3u8") ||
            lower.contains("stream=m3u8") ||
            lower.contains("hls=true") ||
            lower.contains("m3u8=true")) {
            return androidx.media3.common.MimeTypes.APPLICATION_M3U8
        }
        return null
    }

    private fun isStreamUrl(url: String): Boolean {
        val ext = Regex("\\.(m3u8|mpd|mp4|mkv|ts|webm|flv|avi|mov|mpeg|mpg|m4s|f4m|ism|sdp)(\\?|$)", RegexOption.IGNORE_CASE)
        val noDotExt = Regex("/(mpegts|hls|dash)(\\?|/|$)", RegexOption.IGNORE_CASE)
        val paramExt = Regex("([?&](extension|output|type|format|stream)=(m3u8|mpd|ts))", RegexOption.IGNORE_CASE)
        val proto = Regex("^(rtmp|rtsp|udp|srt)://", RegexOption.IGNORE_CASE)
        return ext.containsMatchIn(url) || noDotExt.containsMatchIn(url) || paramExt.containsMatchIn(url) || proto.containsMatchIn(url)
    }

    private fun extractAllUrls(payload: String): List<String> {
        val candidates = mutableListOf<String>()
        val decoded = payload.replace("\\/", "/")
            .replace("\\u002F", "/", ignoreCase = true)
            .replace("\\u003A", ":", ignoreCase = true)
            .replace("\\u0026", "&", ignoreCase = true)
        
        val httpRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        httpRegex.findAll(decoded).forEach { candidates.add(it.value) }
        
        val protoRegex = Regex("(rtmp|rtsp|udp|srt)://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        protoRegex.findAll(decoded).forEach { candidates.add(it.value) }
        
        return candidates.distinct()
    }
}
