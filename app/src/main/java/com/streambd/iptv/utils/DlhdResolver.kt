package com.streambd.iptv.utils

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Kotlin port of the Python DLStreamsExtractor.
 *
 * - multi-player scrape (7 candidate player pages)
 * - _econfig / atob / source: / .m3u8 stream URL extraction
 * - full chain validation: master -> variant -> first segment -> AES key
 * - session cookies forwarded in playback headers
 * - per-host 429 / Retry-After backoff
 * - 30s fresh cache, 15min stale fallback, in-flight de-duplication
 * - overall budget + "first page timed out -> abort loop"
 */
object DlhdResolver {

    private const val TAG = "DlhdResolver"

    // Python uses 20s, but MediaResolver cuts the resolver at 15s, so we must
    // return (with a fallback) before that.
    private const val EXTRACT_BUDGET_MS = 12_000L
    private const val CANDIDATE_TIMEOUT_MS = 6_000L
    private const val STREAM_CACHE_MS = 30_000L
    private const val STREAM_CACHE_STALE_MS = 900_000L
    private const val STREAM_CACHE_FAIL_BACKOFF_MS = 15_000L
    private const val MAX_HOST_BACKOFF_MS = 3_600_000L

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    private const val ACCEPT_HTML =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"

    private val PLAYER_PATHS = listOf("stream", "cast", "watch", "plus", "casting", "player", "hub")

    // ------------------------------------------------------------------ state

    private class CacheEntry(val freshUntil: Long, val staleUntil: Long, val token: ResolvedToken)
    private class Extracted(val token: ResolvedToken, val validated: Boolean)
    private class Page(val status: Int, val body: String?, val retryAfter: String?)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val hostBackoff = ConcurrentHashMap<String, Long>()
    private val inflight = ConcurrentHashMap<String, Deferred<Extracted?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun now(): Long = SystemClock.elapsedRealtime()

    // ------------------------------------------------------------ http client

    private class MemoryCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            synchronized(list) {
                cookies.forEach { c ->
                    list.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                    list.add(c)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = store[url.host] ?: return emptyList()
            synchronized(list) {
                val t = System.currentTimeMillis()
                list.removeAll { it.expiresAt < t }
                return list.filter { it.matches(url) }
            }
        }
    }

    private val cookieJar = MemoryCookieJar()

    private val baseClient: OkHttpClient by lazy {
        UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
            .cookieJar(cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun clientFor(timeoutMs: Long): OkHttpClient =
        baseClient.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()

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

    private fun buildRequest(url: String, headers: Map<String, String>): Request =
        Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> try { header(k, v) } catch (_: Exception) {} }
        }.build()

    private suspend fun fetchPage(url: String, headers: Map<String, String>, timeoutMs: Long): Page =
        clientFor(timeoutMs).newCall(buildRequest(url, headers)).await().use { res ->
            Page(res.code, if (res.isSuccessful) res.body?.string() else null, res.header("Retry-After"))
        }

    private fun cookieHeaderFor(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val cookies = cookieJar.loadForRequest(httpUrl)
        if (cookies.isEmpty()) return null
        return cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    // ------------------------------------------------------- host rate limits

    private fun hostOf(url: String): String =
        try { URL(url).host.lowercase() } catch (_: Exception) { "" }

    private fun parseRetryAfterMs(value: String?): Long? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val ms: Long = if (raw.all { it.isDigit() }) {
            (raw.toLongOrNull() ?: return null) * 1000L
        } else {
            try {
                val d = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(raw) ?: return null
                d.time - System.currentTimeMillis()
            } catch (_: Exception) {
                return null
            }
        }
        return ms.coerceIn(1_000L, MAX_HOST_BACKOFF_MS)
    }

    private fun hostLimitedFor(url: String): Long {
        val deadline = hostBackoff[hostOf(url)] ?: return 0L
        return maxOf(0L, deadline - now())
    }

    private fun markHostLimited(url: String, retryAfter: String?) {
        val host = hostOf(url)
        val ms = parseRetryAfterMs(retryAfter) ?: STREAM_CACHE_FAIL_BACKOFF_MS
        hostBackoff[host] = now() + ms
        Log.w(TAG, "$host rate-limited, backing off ${ms / 1000}s")
    }

    private fun rateLimitWait(): Long {
        val t = now()
        hostBackoff.entries.removeIf { it.value <= t }
        return hostBackoff.values.maxOrNull()?.minus(t) ?: 0L
    }

    private fun backoffCacheEntry(key: String, cached: CacheEntry) {
        val wait = maxOf(STREAM_CACHE_FAIL_BACKOFF_MS, rateLimitWait())
        cache[key] = CacheEntry(now() + wait, cached.staleUntil, cached.token)
    }

    // ---------------------------------------------------------- URL helpers

    private fun join(base: String, rel: String): String =
        try { URL(URL(base), rel).toString() } catch (_: Exception) { rel }

    private fun originOf(u: URL): String =
        "${u.protocol}://${u.host}" + (if (u.port != -1) ":${u.port}" else "")

    /** CDNs put the token in the playlist URL and list relative URIs; keep the parent query. */
    private fun inheritQueryIfMissing(absoluteUrl: String, baseUrl: String): String {
        val parsed = absoluteUrl.toHttpUrlOrNull() ?: return absoluteUrl
        if (!parsed.query.isNullOrEmpty()) return absoluteUrl
        val baseQuery = baseUrl.toHttpUrlOrNull()?.query
        if (baseQuery.isNullOrEmpty()) return absoluteUrl
        return parsed.newBuilder().encodedQuery(baseQuery).build().toString()
    }

    private fun firstMediaUri(body: String, baseUrl: String, wantPlaylist: Boolean): String? {
        for (raw in body.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val low = line.lowercase().substringBefore("?").substringBefore("#")
            if (low.endsWith(".m3u8") == wantPlaylist) {
                return inheritQueryIfMissing(join(baseUrl, line), baseUrl)
            }
        }
        return null
    }

    // ----------------------------------------------------------- validation

    /**
     * master -> variant -> first segment (first bytes only) -> AES key.
     * A manifest-only check would accept players whose segments 403.
     */
    private suspend fun isStreamAlive(url: String, headers: Map<String, String>, budgetMs: Long): Boolean {
        fun t(default: Long) = maxOf(1_000L, minOf(default, budgetMs))
        return try {
            val master = fetchPage(url, headers, t(10_000))
            if (master.status != 200) {
                Log.d(TAG, "validation of $url -> HTTP ${master.status}")
                return false
            }
            val masterBody = master.body ?: return false
            val head = masterBody.take(8000)
            if (!head.contains("#EXTM3U") && !head.contains("#EXT-X-")) {
                Log.d(TAG, "validation of $url -> not a playlist")
                return false
            }

            var currentUrl = url
            var currentBody = masterBody

            // Media playlists contain #EXTINF, masters don't.
            if (!masterBody.contains("#EXTINF")) {
                val variant = firstMediaUri(masterBody, url, wantPlaylist = true)
                if (variant == null) {
                    Log.d(TAG, "validation of $url -> no variant found")
                    return false
                }
                val v = fetchPage(variant, headers, t(8_000))
                if (v.status != 200) {
                    Log.d(TAG, "validation variant $variant -> HTTP ${v.status}")
                    return false
                }
                currentBody = v.body ?: return false
                currentUrl = variant
                if (!currentBody.take(8000).contains("#EXTM3U")) return false
            }

            val segment = firstMediaUri(currentBody, currentUrl, wantPlaylist = false)
            if (segment == null) {
                Log.d(TAG, "validation of $url -> no segments listed")
                return false
            }
            val probeHeaders = headers + ("Range" to "bytes=0-1023")
            val segOk = clientFor(t(8_000)).newCall(buildRequest(segment, probeHeaders)).await().use { res ->
                val ok = res.code == 200 || res.code == 206
                if (ok) {
                    try { res.peekBody(4096) } catch (_: Exception) {}
                } else {
                    Log.d(TAG, "segment probe of $segment -> HTTP ${res.code}")
                }
                ok
            }
            if (!segOk) return false

            val keyMatch = Regex("#EXT-X-KEY:[^\\n]*URI=\"([^\"]+)\"").find(currentBody)
            if (keyMatch != null) {
                val keyUrl = inheritQueryIfMissing(join(currentUrl, keyMatch.groupValues[1]), currentUrl)
                try {
                    val code = clientFor(t(8_000)).newCall(buildRequest(keyUrl, headers)).await().use { it.code }
                    if (code != 200) {
                        Log.d(TAG, "key probe of $keyUrl -> HTTP $code")
                        return false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d(TAG, "key probe of $keyUrl failed: ${e.message}")
                    return false
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "validation of $url failed: ${e.message}")
            false
        }
    }

    // ------------------------------------------------------- HTML extraction

    private fun findIframe(html: String): String? {
        for (id in listOf("thatframe", "playerFrame")) {
            val m = Regex(
                "<iframe[^>]*\\bid=[\"']$id[\"'][^>]*\\bsrc=[\"']([^\"']+)[\"']" +
                    "|<iframe[^>]*\\bsrc=[\"']([^\"']+)[\"'][^>]*\\bid=[\"']$id[\"']",
                RegexOption.IGNORE_CASE
            ).find(html)
            if (m != null) {
                val s = m.groupValues[1].ifEmpty { m.groupValues[2] }
                if (s.isNotBlank()) return s
            }
        }
        return Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
    }

    /** _econfig -> atob -> source: -> any .m3u8 */
    private fun extractStreamUrl(html: String): String? {
        Regex("window\\._econfig\\s*=\\s*['\"]([^'\"]{100,})['\"]", RegexOption.IGNORE_CASE)
            .find(html)?.let { m ->
                val u = TokenParser.decodeBarecropEconfig(m.groupValues[1])
                if (!u.isNullOrEmpty()) return u
            }

        Regex("(?:window\\.)?atob\\(['\"]([A-Za-z0-9+/=\\-_]{20,})['\"]\\)", RegexOption.IGNORE_CASE)
            .find(html)?.let { m ->
                try {
                    val raw = m.groupValues[1].replace('-', '+').replace('_', '/')
                    val dec = String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8).trim()
                    if (dec.startsWith("http")) return dec
                } catch (_: Exception) {}
            }

        Regex("source\\s*:\\s*['\"](https?://[^'\"]+?)['\"]", RegexOption.IGNORE_CASE)
            .find(html)?.let { return it.groupValues[1].trim() }

        Regex("https?://[^\\s'\"<>\\\\]+\\.m3u8[^\\s'\"<>\\\\]*", RegexOption.IGNORE_CASE)
            .find(html)?.let { return it.value }

        return null
    }

    private fun buildToken(streamUrl: String, playbackHeaders: Map<String, String>): ResolvedToken {
        val cookie = cookieHeaderFor(streamUrl)
        val hdrs = if (cookie != null) playbackHeaders + ("Cookie" to cookie) else playbackHeaders
        return ResolvedToken(url = streamUrl, headers = hdrs, mimeType = TokenParser.detectMimeType(streamUrl))
    }

    // ---------------------------------------------------------- extraction

    private suspend fun extractImpl(
        baseUrl: String,
        channelId: String,
        extraHeaders: Map<String, String>?
    ): Extracted? = withContext(Dispatchers.IO) {
        val entryOrigin = try { originOf(URL(baseUrl)) } catch (_: Exception) { return@withContext null }
        val playerUrls = PLAYER_PATHS.map { "$entryOrigin/$it/stream-$channelId.php" }

        val deadline = now() + EXTRACT_BUDGET_MS
        fun remaining() = deadline - now()
        fun timeoutMs(default: Long) = maxOf(1_000L, minOf(default, remaining()))

        val seenIframes = mutableSetOf<String>()
        var firstFallback: Pair<String, Map<String, String>>? = null

        for ((idx, candidate) in playerUrls.withIndex()) {
            if (remaining() <= 2_000L) {
                Log.w(TAG, "extraction budget exhausted after $idx/${playerUrls.size} players")
                break
            }
            if (hostLimitedFor(candidate) > 0) continue

            try {
                val pageHeaders = extraHeaders.orEmpty() + mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to baseUrl,
                    "Accept" to ACCEPT_HTML,
                    "Accept-Language" to "en-US,en;q=0.9"
                )

                val page = fetchPage(candidate, pageHeaders, timeoutMs(CANDIDATE_TIMEOUT_MS))
                if (page.status != 200) {
                    Log.d(TAG, "candidate $candidate returned ${page.status}")
                    if (page.status == 429) markHostLimited(candidate, page.retryAfter)
                    continue
                }
                val html = page.body.orEmpty()
                if (html.isBlank()) continue

                val iframeRaw = findIframe(html) ?: continue
                val iframeSrc = join(candidate, iframeRaw)
                if (!seenIframes.add(iframeSrc)) continue
                if (hostLimitedFor(iframeSrc) > 0) continue

                val iframeHeaders = pageHeaders + mapOf("Referer" to candidate, "Origin" to entryOrigin)
                val iframePage = fetchPage(iframeSrc, iframeHeaders, timeoutMs(CANDIDATE_TIMEOUT_MS))
                if (iframePage.status != 200) {
                    Log.d(TAG, "iframe $iframeSrc returned ${iframePage.status}")
                    if (iframePage.status == 429) markHostLimited(iframeSrc, iframePage.retryAfter)
                    continue
                }
                val iframeHtml = iframePage.body.orEmpty()
                if (iframeHtml.isBlank()) continue

                val streamUrl = extractStreamUrl(iframeHtml)?.takeIf { it.startsWith("http") } ?: continue

                val iframeOrigin = originOf(URL(iframeSrc))
                val playbackHeaders = mapOf(
                    "Referer" to "$iframeOrigin/",
                    "Origin" to iframeOrigin,
                    "User-Agent" to USER_AGENT,
                    "Accept" to "*/*",
                    "Sec-Fetch-Dest" to "empty",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site"
                )

                if (firstFallback == null) firstFallback = streamUrl to playbackHeaders

                if (isStreamAlive(streamUrl, playbackHeaders, remaining())) {
                    Log.i(TAG, "working stream found via $candidate")
                    return@withContext Extracted(buildToken(streamUrl, playbackHeaders), validated = true)
                }
                Log.i(TAG, "stream from $candidate is dead, trying next player")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "candidate $candidate failed: ${e.message}")
                if (idx == 0 && e is InterruptedIOException) {
                    // All candidates share the same origin: if the first page
                    // doesn't answer, the others won't either.
                    Log.w(TAG, "site page timed out, aborting candidate loop")
                    break
                }
            }
        }

        firstFallback?.let { (url, hdrs) ->
            Log.w(TAG, "no player validated alive, returning first URL as fallback")
            Extracted(buildToken(url, hdrs), validated = false)
        }
    }

    // --------------------------------------------------------- public entry

    suspend fun resolve(baseUrl: String, headers: Map<String, String>?): ResolvedToken? {
        val channelId = Regex("(?:id=|premium|stream-)(\\d+)").find(baseUrl)?.groupValues?.get(1)
            ?: baseUrl.filter { it.isDigit() }
        if (channelId.isEmpty()) return null

        val key = "premium$channelId"
        val cached = cache[key]
        if (cached != null && cached.freshUntil > now()) {
            Log.d(TAG, "reusing cached stream URL for $key")
            return cached.token
        }

        val extracted: Extracted? = try {
            var created: Deferred<Extracted?>? = null
            val task = inflight.computeIfAbsent(key) {
                scope.async(start = CoroutineStart.LAZY) { extractImpl(baseUrl, channelId, headers) }
                    .also { created = it }
            }
            created?.let { c ->
                c.invokeOnCompletion { inflight.remove(key, c) }
                c.start()
            }
            task.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "extraction failed for $key: ${e.message}")
            null
        }

        if (extracted == null) {
            if (cached != null && cached.staleUntil > now()) {
                Log.w(TAG, "extraction failed for $key, serving last known stream URL")
                backoffCacheEntry(key, cached)
                return cached.token
            }
            return null
        }

        if (extracted.validated) {
            val t = now()
            cache[key] = CacheEntry(t + STREAM_CACHE_MS, t + STREAM_CACHE_STALE_MS, extracted.token)
            return extracted.token
        }

        if (cached != null && cached.staleUntil > now()) {
            Log.w(TAG, "no player validated for $key, serving last known stream URL")
            backoffCacheEntry(key, cached)
            return cached.token
        }
        return extracted.token
    }
}
