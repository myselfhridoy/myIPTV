package com.streambd.iptv.utils

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A playlist address plus OPTIONAL per-playlist HTTP headers, stored in one
 * string so no database change is needed and old playlists keep working:
 *
 *     https://host/list.m3u|User-Agent=Foo%2F1.0&Referer=https%3A%2F%2Fsite.com
 *     └──────── url ───────┘ └────── headers (URL-encoded, joined by &) ──────┘
 *
 * This is the same "url|Header=Value&Header2=Value2" convention other IPTV
 * players use, so addresses copied from elsewhere work too.
 *
 * Local files picked with the system file picker are stored as `content://…`.
 */
data class PlaylistSpec(
    val url: String,
    val headers: Map<String, String> = emptyMap()
) {
    val isLocal: Boolean
        get() = url.startsWith("content://") || url.startsWith("file://")

    companion object {
        // A header name = letters/digits/dashes; anything else after '|' is
        // treated as part of the URL (some URLs legitimately contain '|').
        private val HEADER_PAIR = Regex("^[A-Za-z][A-Za-z0-9-]*=.*$")

        fun parse(raw: String): PlaylistSpec {
            val text = raw.trim()
            val bar = text.indexOf('|')
            if (bar < 0) return PlaylistSpec(text)

            val rest = text.substring(bar + 1)
            val pairs = rest.split('&').filter { it.isNotEmpty() }
            if (pairs.isEmpty() || !pairs.all { HEADER_PAIR.matches(it) }) {
                return PlaylistSpec(text) // not a header suffix → whole string is the URL
            }

            val headers = linkedMapOf<String, String>()
            for (pair in pairs) {
                val eq = pair.indexOf('=')
                val name = decode(pair.substring(0, eq)).trim()
                val value = decode(pair.substring(eq + 1)).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) headers[name] = value
            }
            return PlaylistSpec(text.substring(0, bar).trim(), headers)
        }

        /** Inverse of [parse]. Blank names/values are dropped. */
        fun build(url: String, headers: Map<String, String>): String {
            val clean = headers.filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
            if (clean.isEmpty()) return url.trim()
            return url.trim() + "|" + clean.entries.joinToString("&") {
                "${encode(it.key.trim())}=${encode(it.value.trim())}"
            }
        }

        private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

        private fun decode(s: String): String =
            try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }
    }
}
