package com.streambd.iptv.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
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
    private suspend fun isStreamAlive(
        url: String,
        headers: Map<String, String>,
        budgetMs: Long,
        pngOut: BooleanArray? = null
    ): Boolean {
        fun t(default: Long) = maxOf(1_000L, minOf(default, budgetMs))
        return try {
            val master = fetchPage(url, headers, t(10_000))
            if (master.status != 200) {
                Log.d(TAG, "validation of $url -> HTTP ${master.status}")
                return false
            }
            val masterBody = master.body ?: return false
            val masterHead = masterBody.take(8000)
            if (!masterHead.contains("#EXTM3U") && !masterHead.contains("#EXT-X-")) {
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
            var segHead: ByteArray? = null
            val segOk = clientFor(t(8_000)).newCall(buildRequest(segment, probeHeaders)).await().use { res ->
                val ok = res.code == 200 || res.code == 206
                if (ok) {
                    segHead = try { res.peekBody(1024).bytes() } catch (_: Exception) { null }
                } else {
                    Log.d(TAG, "segment probe of $segment -> HTTP ${res.code}")
                }
                ok
            }
            if (!segOk) return false
            var head = segHead ?: return false

            // Some CDNs disguise segments as PNG images.
            // Two sub-cases:
            //   (a) PNG-wrapped TS: TS payload comes after IEND chunk.
            //   (b) Raw PNG segments: no TS payload — the CDN directly serves PNG-encoded video
            //       (e.g. TikTok CDN used by dlive.sx / dembed.top).
            // In case (b) stripPng returns null because IEND is the last byte, but the stream
            // IS alive (the web player handles it natively). We must not reject it.
            if (isPng(head)) {
                pngOut?.set(0, true)
                val big: ByteArray = clientFor(t(8_000))
                    .newCall(buildRequest(segment, headers + ("Range" to "bytes=0-262143")))
                    .await().use { res ->
                        if (res.code == 200 || res.code == 206) {
                            try { res.peekBody(262144).bytes() } catch (_: Exception) { null }
                        } else null
                    } ?: return false
                val payload = stripPng(big)
                if (payload == null) {
                    // Case (b): PNG pixel-encoded TS (TikTok CDN style — no IEND append,
                    // TS bytes stored as RGB pixel values). Keep pngOut=true so the proxy
                    // decodes pixels → TS before handing to ExoPlayer.
                    Log.w(TAG, "segment $segment is pixel-encoded PNG (TikTok CDN); routing via proxy decoder")
                    return true  // pngOut already true from above
                }
                Log.d(TAG, "segment $segment is PNG-wrapped TS, payload starts at byte ${big.size - payload.size}")
                head = payload
            }

            val keyTag = Regex("#EXT-X-KEY:([^\\n]*)").find(currentBody)?.groupValues?.get(1)
            val keyUri = keyTag?.let { Regex("URI=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            val encrypted = keyTag != null && keyUri != null && keyTag.contains("METHOD=AES-128")

            if (!encrypted) {
                if (!looksLikeMediaChunk(head)) {
                    Log.w(TAG, "segment $segment is not media: ${describeBytes(head)}")
                    return false
                }
                return true
            }

            // Encrypted: fetch the key and decrypt the first block ourselves.
            val keyUrl = inheritQueryIfMissing(join(currentUrl, keyUri!!), currentUrl)
            val keyBytes: ByteArray? = clientFor(t(8_000)).newCall(buildRequest(keyUrl, headers)).await().use { res ->
                if (res.code == 200) res.body?.bytes() else {
                    Log.d(TAG, "key probe of $keyUrl -> HTTP ${res.code}")
                    null
                }
            }
            if (keyBytes == null || keyBytes.size != 16) {
                Log.w(TAG, "key from $keyUrl invalid (size=${keyBytes?.size})")
                return false
            }
            if (head.size < 16) return false

            val iv = ByteArray(16)
            val ivHex = Regex("IV=0x([0-9A-Fa-f]+)").find(keyTag!!)?.groupValues?.get(1)
            if (ivHex != null) {
                val raw = ivHex.padStart(32, '0').takeLast(32)
                for (i in 0 until 16) iv[i] = raw.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            } else {
                var seq = Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)").find(currentBody)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                for (i in 15 downTo 8) { iv[i] = (seq and 0xFF).toByte(); seq = seq shr 8 }
            }
            val firstBlock = Cipher.getInstance("AES/CBC/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                doFinal(head, 0, 16)
            }
            if (!looksLikeMediaChunk(firstBlock)) {
                Log.w(TAG, "segment $segment decrypts to garbage with key $keyUrl: ${describeBytes(firstBlock)}")
                return false
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "validation of $url failed: ${e.message}")
            false
        }
    }

    // ----------------------------------------------------- chunk sniffing

    /** MPEG-TS (0x47 sync), fMP4 (ftyp/styp/moof), or raw audio (ADTS / ID3). */
    private fun looksLikeMediaChunk(b: ByteArray): Boolean {
        if (b.size < 8) return false
        if (b[0] == 0x47.toByte() && (b.size <= 188 || b[188] == 0x47.toByte())) return true
        val box = String(b, 4, 4, Charsets.ISO_8859_1)
        if (box == "ftyp" || box == "styp" || box == "moof" || box == "mdat") return true
        if (b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) return true
        if ((b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xF0) == 0xF0) return true
        return false
    }

    private fun isPng(b: ByteArray): Boolean =
        b.size >= 8 && (b[0].toInt() and 0xFF) == 0x89 &&
            b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte()

    /** Walks PNG chunks and returns the bytes after IEND (+CRC), or null if IEND isn't in [b]. */
    private fun stripPng(b: ByteArray): ByteArray? {
        var pos = 8
        while (pos + 12 <= b.size) {
            val len = ((b[pos].toInt() and 0xFF) shl 24) or ((b[pos + 1].toInt() and 0xFF) shl 16) or
                ((b[pos + 2].toInt() and 0xFF) shl 8) or (b[pos + 3].toInt() and 0xFF)
            if (len < 0) return null
            val type = String(b, pos + 4, 4, Charsets.ISO_8859_1)
            if (type == "IEND") {
                val start = pos + 12
                return if (start < b.size) b.copyOfRange(start, b.size) else null
            }
            pos += 12 + len
        }
        return null
    }

    /**
     * Decodes a PNG whose pixel RGB values encode raw MPEG-TS bytes.
     * Used by TikTok CDN segments (dlive.sx / dembed.top):
     *   pixel[i].R = ts[i*3+0]
     *   pixel[i].G = ts[i*3+1]
     *   pixel[i].B = ts[i*3+2]
     * Trailing zero-padding (to fill the last PNG row) is trimmed to the
     * nearest MPEG-TS packet boundary (188 bytes).
     */
    internal fun decodeRawPngToTs(b: ByteArray): ByteArray? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(b, 0, b.size, opts) ?: return null
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.recycle()
            val raw = ByteArray(pixels.size * 3)
            var idx = 0
            for (p in pixels) {
                raw[idx++] = ((p shr 16) and 0xFF).toByte() // R
                raw[idx++] = ((p shr  8) and 0xFF).toByte() // G
                raw[idx++] = ( p         and 0xFF).toByte() // B
            }
            // Trim trailing null padding; snap to MPEG-TS packet boundary
            var end = raw.size
            while (end > 0 && raw[end - 1] == 0.toByte()) end--
            val tsEnd = (end / 188) * 188
            if (tsEnd <= 0) {
                Log.w(TAG, "decodeRawPngToTs: no TS packets found (pixels=${pixels.size})")
                return null
            }
            Log.d(TAG, "decodeRawPngToTs: ${bitmap.width}x${h} PNG → $tsEnd TS bytes (${pixels.size * 3 - tsEnd} pad trimmed)")
            raw.copyOf(tsEnd)
        } catch (e: Exception) {
            Log.e(TAG, "decodeRawPngToTs failed: ${e.message}")
            null
        }
    }

    private fun describeBytes(b: ByteArray): String {
        val n = minOf(b.size, 8)
        val hex = (0 until n).joinToString(" ") { "%02x".format(b[it]) }
        val kind = when {
            b.size >= 4 && (b[0].toInt() and 0xFF) == 0x89 && b[1] == 'P'.code.toByte() -> "PNG-wrapped"
            b.isNotEmpty() && (b[0] == '<'.code.toByte() || b[0] == '{'.code.toByte()) ->
                "text/html: " + String(b, 0, minOf(b.size, 40), Charsets.ISO_8859_1).replace('\n', ' ')
            else -> "unknown"
        }
        return "$kind [$hex]"
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
        // Every extraction path here targets an HLS playlist (validated as #EXTM3U).
        // Without an explicit type, extensionless URLs are treated as progressive
        // by ExoPlayer -> "None of the available extractors could read the stream".
        val mime = TokenParser.detectMimeType(streamUrl) ?: androidx.media3.common.MimeTypes.APPLICATION_M3U8
        return ResolvedToken(url = streamUrl, headers = hdrs, mimeType = mime)
    }

    // ---------------------------------------------------------- extraction

    // ------------------------------------------------ local PNG-stripping proxy
    //
    // Some CDNs serve segments as PNG "images" with the TS payload after IEND.
    // Instead of touching the player's data source, the stream is exposed through a tiny
    // loopback HTTP server: playlists are rewritten to point back here, and segments/keys
    // are fetched with our own headers + cookies, returned with the PNG part removed.
    // NOTE: needs cleartext HTTP to 127.0.0.1 to be allowed by the app.

    private val proxyLock = Any()
    private var proxySocket: ServerSocket? = null
    private val proxyPool = Executors.newCachedThreadPool { r ->
        Thread(r, "dlhd-proxy").apply { isDaemon = true }
    }
    private val proxySessions = ConcurrentHashMap<String, Map<String, String>>()

    private fun ensureProxy(): Int {
        synchronized(proxyLock) {
            val existing = proxySocket
            if (existing != null && !existing.isClosed) return existing.localPort
            val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            proxySocket = ss
            Thread({
                while (!ss.isClosed) {
                    try {
                        val s = ss.accept()
                        proxyPool.execute { handleProxyClient(s) }
                    } catch (e: Exception) {
                        if (ss.isClosed) break
                    }
                }
            }, "dlhd-proxy-accept").apply { isDaemon = true; start() }
            Log.i(TAG, "local proxy listening on 127.0.0.1:${ss.localPort}")
            return ss.localPort
        }
    }

    private fun buildProxiedToken(streamUrl: String, playbackHeaders: Map<String, String>): ResolvedToken {
        val port = ensureProxy()
        // Stable per stream path, so refreshed tokens reuse the same session id.
        val id = Integer.toHexString(streamUrl.substringBefore("?").hashCode())
        proxySessions[id] = playbackHeaders
        val url = "http://127.0.0.1:$port/p/$id/index.m3u8?u=${URLEncoder.encode(streamUrl, "UTF-8")}"
        return ResolvedToken(
            url = url,
            headers = mapOf("User-Agent" to USER_AGENT),
            mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8
        )
    }

    private fun proxyReply(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val reason = if (code == 200) "OK" else "Error"
        out.write(
            ("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                .toByteArray(Charsets.ISO_8859_1)
        )
        out.write(body)
        out.flush()
    }

    private fun rewritePlaylist(text: String, base: String, port: Int, id: String): String {
        fun local(kind: String, target: String): String {
            val abs = inheritQueryIfMissing(join(base, target), base)
            return "http://127.0.0.1:$port/p/$id/$kind?u=${URLEncoder.encode(abs, "UTF-8")}"
        }
        val uriAttr = Regex("URI=\"([^\"]+)\"")
        return text.lineSequence().joinToString("\n") { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> raw
                line.startsWith("#") -> {
                    val kind = when {
                        line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-SESSION-KEY") -> "k.key"
                        line.startsWith("#EXT-X-MAP") -> "m.mp4"
                        line.startsWith("#EXT-X-MEDIA") -> "v.m3u8"
                        else -> null
                    }
                    if (kind == null) raw
                    else uriAttr.replace(line) { m -> "URI=\"" + local(kind, m.groupValues[1]) + "\"" }
                }
                else -> {
                    val path = line.substringBefore("?").substringBefore("#").lowercase()
                    local(if (path.endsWith(".m3u8")) "v.m3u8" else "s.ts", line)
                }
            }
        }
    }

    private fun handleProxyClient(sock: Socket) {
        try {
            sock.use {
                sock.soTimeout = 20_000
                val reader = sock.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val requestLine = reader.readLine() ?: return
                while (true) {
                    val l = reader.readLine() ?: break
                    if (l.isEmpty()) break
                }
                val out = sock.getOutputStream().buffered()

                val target = requestLine.split(" ").getOrNull(1)
                if (target == null) {
                    proxyReply(out, 400, "text/plain", ByteArray(0)); return
                }
                val segs = target.substringBefore("?").split("/").filter { it.isNotEmpty() }
                val id = segs.getOrNull(1)
                val kind = segs.getOrNull(2).orEmpty()
                val upstreamUrl = target.substringAfter("?", "").split("&")
                    .firstOrNull { it.startsWith("u=") }
                    ?.substring(2)?.let { URLDecoder.decode(it, "UTF-8") }
                val headers = id?.let { proxySessions[it] }
                if (segs.firstOrNull() != "p" || headers == null || upstreamUrl == null) {
                    proxyReply(out, 404, "text/plain", ByteArray(0)); return
                }

                clientFor(15_000).newCall(buildRequest(upstreamUrl, headers)).execute().use { res ->
                    if (!res.isSuccessful) {
                        Log.w(TAG, "proxy: upstream ${res.code} for $upstreamUrl")
                        proxyReply(out, if (res.code in 400..599) res.code else 502, "text/plain", ByteArray(0))
                        return
                    }
                    if (kind.endsWith(".m3u8")) {
                        val text = res.body?.string().orEmpty()
                        val rewritten = rewritePlaylist(text, res.request.url.toString(), sock.localPort, id!!)
                        proxyReply(out, 200, "application/vnd.apple.mpegurl", rewritten.toByteArray(Charsets.UTF_8))
                    } else {
                        var data = res.body?.bytes() ?: ByteArray(0)
                        if (isPng(data)) {
                            val tsFromIend = stripPng(data)
                            data = if (tsFromIend != null && tsFromIend.isNotEmpty()) {
                                // Case A: classic TS-after-IEND wrapper
                                tsFromIend
                            } else {
                                // Case B: TikTok CDN pixel-encoded TS (RGB pixels → TS bytes)
                                decodeRawPngToTs(data) ?: run {
                                    Log.w(TAG, "proxy: PNG segment not decodable from $upstreamUrl")
                                    proxyReply(out, 502, "text/plain", ByteArray(0))
                                    return
                                }
                            }
                        }
                        val type = when {
                            kind.endsWith(".key") -> "application/octet-stream"
                            kind.endsWith(".mp4") -> "video/mp4"
                            else -> "video/mp2t"
                        }
                        proxyReply(out, 200, type, data)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "proxy client error: ${e.message}")
        }
    }

    private suspend fun extractImpl(
        baseUrl: String,
        channelId: String,
        extraHeaders: Map<String, String>?
    ): Extracted? = withContext(Dispatchers.IO) {
        val entryOrigin = try { originOf(URL(baseUrl)) } catch (_: Exception) { return@withContext null }
        // If baseUrl is already a direct page URL (e.g. watch.php?id=346), try it first.
        // Then fall back to the generic PLAYER_PATHS pattern.
        val genericPaths = PLAYER_PATHS.map { "$entryOrigin/$it/stream-$channelId.php" }
        val playerUrls: List<String> = if (baseUrl != "$entryOrigin/" && !genericPaths.contains(baseUrl)) {
            listOf(baseUrl) + genericPaths
        } else {
            genericPaths
        }

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

                val pngFlag = BooleanArray(1)
                if (isStreamAlive(streamUrl, playbackHeaders, remaining(), pngFlag)) {
                    Log.i(TAG, "working stream found via $candidate (png=${pngFlag[0]})")
                    val token = if (pngFlag[0]) buildProxiedToken(streamUrl, playbackHeaders)
                                else buildToken(streamUrl, playbackHeaders)
                    return@withContext Extracted(token, validated = true)
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
