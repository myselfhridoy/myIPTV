package com.streambd.iptv.ui.screens.stream

import android.content.ClipboardManager
import android.content.Context
import com.streambd.iptv.data.models.DrmConfig
import org.json.JSONObject

// Moved out of StreamScreen.kt UNCHANGED (only visibility widened from
// private to internal so the screen file can use them). No logic edits.

// ---------------------------------------------------------------------
// Constants / helpers
// ---------------------------------------------------------------------

internal val USER_AGENTS = listOf(
    "Default", "Chrome(Android)", "Chrome(PC)", "IE(PC)", "Firefox(PC)",
    "iPhone", "Nokia", "Custom"
)
internal val DRM_SCHEMES = listOf("None", "clearkey", "widevine", "playready")

private val UA_STRINGS = mapOf(
    "Chrome(Android)" to "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Chrome(PC)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "IE(PC)" to "Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko",
    "Firefox(PC)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0",
    "iPhone" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
    "Nokia" to "Nokia6300/2.0 (05.20) Profile/MIDP-2.0 Configuration/CLDC-1.1"
)

private val CLEARKEY_PAIR = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$")
internal val STD_HEADERS = setOf("user-agent", "referer", "origin", "cookie")
internal const val MAX_HISTORY = 50

internal fun resolveUserAgent(ua: String, custom: String): String? = when {
    ua == "Custom" -> custom.trim().takeIf { it.isNotEmpty() }
    ua.isBlank() || ua == "Default" -> null
    else -> UA_STRINGS[ua] ?: ua
}

internal fun normalizeDrmScheme(raw: String): String {
    val t = raw.trim().lowercase()
    return when {
        t.contains("widevine") -> "widevine"
        t.contains("playready") -> "playready"
        t.contains("clearkey") -> "clearkey"
        t == "none" || t.isEmpty() -> "None"
        else -> t
    }
}

internal fun buildDrmConfig(scheme: String, license: String): DrmConfig? {
    if (scheme.isBlank() || scheme.equals("None", ignoreCase = true)) return null
    val lic = license.trim().takeIf { it.isNotEmpty() }
    val type = scheme.lowercase()
    return if (type == "clearkey" && lic != null && CLEARKEY_PAIR.matches(lic)) {
        DrmConfig(type = "clearkey", rawKeyPair = lic)
    } else {
        DrmConfig(type = type, licenseServer = lic)
    }
}

private fun JSONObject.optStrOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key)
    return if (v.isEmpty() || v == "null") null else v
}

private fun mapToJson(map: Map<String, String>): JSONObject {
    val o = JSONObject()
    map.forEach { (k, v) -> o.put(k, v) }
    return o
}

private fun jsonToMap(obj: JSONObject?): Map<String, String> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<String, String>()
    obj.keys().forEach { k -> obj.optStrOrNull(k)?.let { out[k] = it } }
    return out
}

/** Read the current plain-text from the system clipboard, or null if empty. */
internal fun readClipboardText(context: Context): String? {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(context).toString()
            text.takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) {
        null
    }
}

data class PipeExtras(
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val tokenUrl: String? = null,
    val tokenMatch: String? = null,
    val tokenReplace: String? = null,
    val tokenId: Int? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("headers", mapToJson(headers))
        tokenUrl?.let { put("tokenUrl", it) }
        tokenMatch?.let { put("tokenMatch", it) }
        tokenReplace?.let { put("tokenReplace", it) }
        tokenId?.let { put("tokenId", it) }
    }

    companion object {
        fun fromJsonString(s: String?): PipeExtras {
            if (s.isNullOrBlank()) return PipeExtras()
            return try {
                val o = JSONObject(s)
                PipeExtras(
                    url = o.optString("url", ""),
                    headers = jsonToMap(o.optJSONObject("headers")),
                    tokenUrl = o.optStrOrNull("tokenUrl"),
                    tokenMatch = o.optStrOrNull("tokenMatch"),
                    tokenReplace = o.optStrOrNull("tokenReplace"),
                    tokenId = if (o.has("tokenId") && !o.isNull("tokenId")) o.optInt("tokenId") else null
                )
            } catch (e: Exception) {
                PipeExtras()
            }
        }
    }
}

data class StreamHistoryItem(
    val url: String,
    val cookie: String,
    val referer: String,
    val origin: String,
    val drmLicense: String,
    val userAgent: String,
    val drmScheme: String,
    val tokenUrl: String? = null,
    val tokenMatch: String? = null,
    val tokenReplace: String? = null,
    val tokenId: Int? = null,
    val customUa: String = "",
    val extraHeaders: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("url", url)
        obj.put("cookie", cookie)
        obj.put("referer", referer)
        obj.put("origin", origin)
        obj.put("drmLicense", drmLicense)
        obj.put("userAgent", userAgent)
        obj.put("drmScheme", drmScheme)
        obj.put("customUa", customUa)
        obj.put("extraHeaders", mapToJson(extraHeaders))
        tokenUrl?.let { obj.put("tokenUrl", it) }
        tokenMatch?.let { obj.put("tokenMatch", it) }
        tokenReplace?.let { obj.put("tokenReplace", it) }
        tokenId?.let { obj.put("tokenId", it) }
        return obj
    }

    companion object {
        fun fromJson(jsonStr: String): StreamHistoryItem? {
            return try {
                val obj = JSONObject(jsonStr)
                StreamHistoryItem(
                    url = obj.optString("url", ""),
                    cookie = obj.optString("cookie", ""),
                    referer = obj.optString("referer", ""),
                    origin = obj.optString("origin", ""),
                    drmLicense = obj.optString("drmLicense", ""),
                    userAgent = obj.optString("userAgent", "Default"),
                    drmScheme = obj.optString("drmScheme", "clearkey"),
                    tokenUrl = obj.optStrOrNull("tokenUrl"),
                    tokenMatch = obj.optStrOrNull("tokenMatch"),
                    tokenReplace = obj.optStrOrNull("tokenReplace"),
                    tokenId = if (obj.has("tokenId") && !obj.isNull("tokenId")) obj.optInt("tokenId") else null,
                    customUa = obj.optString("customUa", ""),
                    extraHeaders = jsonToMap(obj.optJSONObject("extraHeaders"))
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
