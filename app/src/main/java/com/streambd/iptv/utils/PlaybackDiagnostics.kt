package com.streambd.iptv.utils

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.TreeMap

/**
 * প্লেব্যাক এরর "চ্যানেলের সমস্যা" নাকি "প্লেয়ারের সমস্যা" আলাদা করার সাহায্যকারী।
 *
 *  - describe(): ExoPlayer এররের কোড + HTTP স্ট্যাটাস + ছোট হিন্ট
 *  - probe(): একই URL/হেডার দিয়ে আলাদা করে ফেচ করে দেখে সার্ভার আসলে কী ফেরত দিচ্ছে
 */
object PlaybackDiagnostics {

    data class ProbeResult(
        val status: Int?,
        val contentType: String?,
        val verdict: String,
        val snippet: String
    ) {
        fun summary(): String = buildString {
            append(verdict)
            append("\n")
            append("HTTP ").append(status ?: "-")
            if (!contentType.isNullOrBlank()) append(" • ").append(contentType)
            if (snippet.isNotBlank()) append("\n").append(snippet)
        }
    }

    @OptIn(UnstableApi::class)
    fun describe(error: PlaybackException): String {
        val parts = mutableListOf(error.errorCodeName)
        var t: Throwable? = error.cause
        var depth = 0
        while (t != null && depth < 6) {
            if (t is HttpDataSource.InvalidResponseCodeException) {
                parts.add("HTTP ${t.responseCode}")
            }
            t = t.cause
            depth++
        }
        hint(error)?.let { parts.add(it) }
        return parts.joinToString(" • ")
    }

    private fun hint(error: PlaybackException): String? {
        val msg = buildString {
            append(error.message.orEmpty()).append(' ')
            var t: Throwable? = error.cause
            var depth = 0
            while (t != null && depth < 6) {
                append(t.message.orEmpty()).append(' ')
                t = t.cause
                depth++
            }
        }
        return when {
            msg.contains("#EXTINF", ignoreCase = true) && msg.contains("Couldn't match") ->
                "Link is an IPTV channel list, not a stream"
            msg.contains("does not start with the #EXTM3U", ignoreCase = true) ->
                "Server did not return an HLS playlist (blocked / expired token / wrong URL type)"
            msg.contains("bind a sample queue", ignoreCase = true) ->
                "HLS codec mismatch (player-side setting)"
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "Invalid playlist/manifest received"
            error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "http:// blocked by Network Security Config"
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Network / server unreachable"
            else -> null
        }
    }

    suspend fun probe(
        rawUrl: String,
        userAgent: String?,
        cookie: String?,
        referer: String?,
        origin: String?,
        extraHeaders: Map<String, String>?
    ): ProbeResult = withContext(Dispatchers.IO) {
        try {
            val parsed = M3UParser.parseUrlHeaders(rawUrl)

            // হেডার নামের case-insensitive ম্যাপ: "User-Agent" ও "user-agent" দুইবার যাবে না
            val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
            extraHeaders?.forEach { (k, v) -> headers[k] = v }
            if (!userAgent.isNullOrBlank()) headers["User-Agent"] = userAgent
            if (!cookie.isNullOrBlank()) headers["Cookie"] = cookie
            if (!referer.isNullOrBlank()) headers["Referer"] = referer
            if (!origin.isNullOrBlank()) headers["Origin"] = origin
            parsed.headers.forEach { (k, v) -> headers[k] = v }

            var current = parsed.cleanUrl
            for (hop in 0..5) {
                val conn = URL(current).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = false
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308) && hop < 5) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc.isNullOrBlank()) {
                        return@withContext ProbeResult(code, null, "Redirect without Location", "")
                    }
                    current = URL(URL(current), loc).toString()
                    continue
                }

                val contentType = conn.contentType
                val stream = if (code < 400) conn.inputStream else conn.errorStream
                val bytes = stream?.use { readUpTo(it, 4096) } ?: ByteArray(0)
                conn.disconnect()

                return@withContext ProbeResult(
                    status = code,
                    contentType = contentType,
                    verdict = verdictFor(code, bytes),
                    snippet = snippetOf(bytes)
                )
            }
            ProbeResult(null, null, "Too many redirects", "")
        } catch (e: Exception) {
            ProbeResult(null, null, "Request failed: ${e.javaClass.simpleName} – server unreachable or blocked", e.message.orEmpty())
        }
    }

    private fun readUpTo(input: java.io.InputStream, max: Int): ByteArray {
        val buf = ByteArray(max)
        var total = 0
        while (total < max) {
            val n = input.read(buf, total, max - total)
            if (n <= 0) break
            total += n
        }
        return buf.copyOf(total)
    }

    private fun snippetOf(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes[0] == 0x47.toByte()) return "(binary MPEG-TS data)"
        return String(bytes, Charsets.UTF_8)
            .take(200)
            .replace(Regex("[\\r\\n]+"), " ⏎ ")
            .filter { it == ' ' || !it.isISOControl() }
    }

    private fun verdictFor(code: Int, bytes: ByteArray): String {
        when (code) {
            401, 403, 451 -> return "Blocked by server (HTTP $code): needs different headers/cookie/token, or geo/IP block"
            404, 410 -> return "Link is dead (HTTP $code)"
            429 -> return "Rate limited by server (HTTP 429)"
        }
        if (code >= 500) return "Server error (HTTP $code) – channel side"
        if (code >= 400) return "Request rejected (HTTP $code)"

        if (bytes.isEmpty()) return "Empty response – token expired or server refused"
        if (bytes[0] == 0x47.toByte()) return "Raw MPEG-TS stream – must be played as TS/progressive, not HLS"

        val text = String(bytes, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return when {
            text.startsWith("#EXTM3U") && text.contains("#EXT-X-") ->
                "Valid HLS playlist – link is OK, issue is player-side (codec/headers/DRM)"
            text.startsWith("#EXTM3U") ->
                "This is an IPTV channel list, not a stream – wrong link"
            text.contains("<MPD", ignoreCase = true) ->
                "DASH manifest (.mpd) – must be played as DASH, not HLS"
            text.startsWith("<") ->
                "HTML page – token expired, geo-block, login/consent page"
            text.startsWith("{") || text.startsWith("[") ->
                "JSON response, not a playlist – wrong endpoint or auth error"
            else -> "Unknown content – not an HLS playlist"
        }
    }
}
