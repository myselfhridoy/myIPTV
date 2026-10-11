package com.streambd.iptv.utils

import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.DrmConfig
import org.json.JSONArray
import org.json.JSONObject

object M3UParser {

    // ---------------------------------------------------------------------
    // Pre-compiled regexes
    // ---------------------------------------------------------------------
    private val IC = RegexOption.IGNORE_CASE

    private val RE_LOGO = Regex("""(?<![\w-])(?:tvg-logo|logo|icon|poster)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""", IC)
    private val RE_GROUP = Regex("""group-title\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""", IC)
    private val RE_UA     = Regex("""http-user-agent\s*=\s*(?:"([^"]*)"|'([^']*)')""", IC)
    private val RE_REF    = Regex("""http-refer(?:r)?er\s*=\s*(?:"([^"]*)"|'([^']*)')""", IC)
    private val RE_ORIGIN = Regex("""http-origin\s*=\s*(?:"([^"]*)"|'([^']*)')""", IC)
    private val RE_COOKIE = Regex("""http-cookie\s*=\s*(?:"([^"]*)"|'([^']*)')""", IC)
    private val RE_TOKEN_ID = Regex("""(?:^|\s)(?:token-id|tokenid)\s*=\s*"?([^",\s]+)"?""", IC)

    private val RE_ATTR_PREFIX = Regex("""^#\s*EXTATTRFROMURL:\s*""", IC)
    private val RE_ATTR_ANYWHERE = Regex("""#\s*EXTATTRFROMURL:""", IC)
    private val RE_CLEARKEY_PAIR = Regex("""^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$""")
    private val RE_CLEARKEY_ANYWHERE = Regex("""[0-9a-fA-F]{32}:[0-9a-fA-F]{32}""")

    private val RE_PLS_LINE = Regex("""^(file|title|group)(\d*)\s*=\s*(.*)$""", IC)
    private val RE_PLS_TITLE_GROUP = Regex("""^(.+?)\s+[-–—]\s+(.+)$""")

    private val RE_XSPF_TRACK = Regex("""<track>([\s\S]*?)</track>""", IC)
    private val RE_XSPF_TITLE = Regex("""<title>([\s\S]*?)</title>""", IC)
    private val RE_XSPF_LOCATION = Regex("""<location>([\s\S]*?)</location>""", IC)
    private val RE_XSPF_IMAGE = Regex("""<image>([\s\S]*?)</image>""", IC)
    private val RE_XSPF_CREATOR = Regex("""<creator>([\s\S]*?)</creator>""", IC)
    private val RE_XSPF_ANNOTATION = Regex("""<annotation>([\s\S]*?)</annotation>""", IC)

    private val RE_VOD = Regex("""\.(mp4|mkv|avi|mov|webm|flv|wmv|m4v)(?:$|[?#|])""", IC)
    private val RE_TS  = Regex("""\.ts(?:$|[?#|])""", IC)
    private val RE_LOGO_NUMERIC = Regex("""^\d{10,}\.(png|jpg|jpeg|webp)$""", IC)

    private val RE_NUMERIC_ENTITY = Regex("""&#(x?)([0-9a-fA-F]+);""")

    // ─────────────────────────────────────────────────────────────────────
    // PIPE / AMPERSAND HEADER DETECTION
    //
    // The header section begins at the first `|` that is followed by:
    //   • Key= or Key:            → standard pipe header
    //   • 32-hex-chars followed by `:` → raw ClearKey pair
    //   • clearkey / widevine / playready / fairplay → bare DRM marker
    //
    // Anything before that `|` is the URL — including any `&` or `|` in
    // its query string, since we only look for a header-shaped token.
    // ─────────────────────────────────────────────────────────────────────
    private val RE_HEADER_SECTION_START = Regex(
        """\|(?=(?:[A-Za-z][A-Za-z0-9_.-]*\s*[=:])|(?:[0-9a-fA-F]{32}:)|(?:clearkey\b|widevine\b|playready\b|fairplay\b))""",
        IC
    )

    // Split a header block into individual pieces.
    //   `|` is ALWAYS a separator (Kodi/TiviMate convention).
    //   `&` is a separator only when followed by a header-shaped token.
    private val RE_PIPE_SPLIT = Regex("""\|""")
    private val RE_AMP_SPLIT = Regex(
        """&(?=(?:[A-Za-z][A-Za-z0-9_.-]*\s*[=:])|(?:[0-9a-fA-F]{32}:))""",
        IC
    )

    // Standard headers stored in dedicated fields, not in httpHeaders map.
    private val STANDARD_HEADERS = setOf("user-agent", "referer", "origin", "cookie")

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    fun preprocessM3U(content: String): String = content.removePrefix("\uFEFF")

    fun parsePlaylist(content: String): List<Channel> {
        val trimmed = content.removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.contains("#EXTM3U") ||
            trimmed.contains("#EXTINF:") ||
            RE_ATTR_ANYWHERE.containsMatchIn(trimmed)
        ) {
            return parseM3U(trimmed)
        }

        val lower = trimmed.lowercase()
        if (lower.contains("[playlist]") || lower.contains("numberofentries=")) {
            return parsePLS(trimmed)
        }

        if (lower.contains("<playlist") && lower.contains("<tracklist")) {
            return parseXSPF(trimmed)
        }

        try {
            if (trimmed.startsWith("[")) {
                return parseJSONArray(JSONArray(trimmed))
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                for (key in listOf("channels", "streams", "items", "data")) {
                    val arr = obj.optJSONArray(key)
                    if (arr != null) return parseJSONArray(arr)
                }
            }
        } catch (e: Exception) {
            // Not JSON — try M3U below.
        }

        return try { parseM3U(trimmed) } catch (e: Exception) { emptyList() }
    }

    fun parseM3U(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()

        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentUserAgent: String? = null
        var currentCookie: String? = null
        var currentHttpReferer: String? = null
        var currentOrigin: String? = null
        var currentTokenUrl: String? = null
        var currentTokenMatch: String? = null
        var currentTokenReplace: String? = null
        var currentTokenId: Int? = null
        var currentDrm: DrmConfig? = null
        var currentHttpHeaders: MutableMap<String, String>? = null

        fun resetCurrentChannel() {
            currentName = ""
            currentLogo = ""
            currentGroup = ""
            currentUserAgent = null
            currentCookie = null
            currentHttpReferer = null
            currentOrigin = null
            currentTokenUrl = null
            currentTokenMatch = null
            currentTokenReplace = null
            currentTokenId = null
            currentDrm = null
            currentHttpHeaders = null
        }

        fun mergeExtraHeaders(extra: Map<String, String>) {
            if (extra.isEmpty()) return
            val map = currentHttpHeaders ?: mutableMapOf()
            // Case-insensitive replace to avoid duplicate headers with
            // different casing ending up in the output.
            extra.forEach { (k, v) ->
                val existing = map.keys.firstOrNull { it.equals(k, ignoreCase = true) }
                if (existing != null) map.remove(existing)
                map[k] = v
            }
            currentHttpHeaders = map
        }

        fun applyParsed(parsed: ParsedUrl) {
            parsed.headers["referer"]?.let { currentHttpReferer = it }
            parsed.headers["user-agent"]?.let { currentUserAgent = it }
            parsed.headers["origin"]?.let { currentOrigin = it }
            parsed.headers["cookie"]?.let { currentCookie = it }

            extraHeaders(parsed.headers)?.let { mergeExtraHeaders(it) }

            parsed.custom["tokenurl"]?.let { currentTokenUrl = it }
            parsed.custom["tokenmatch"]?.let { currentTokenMatch = it }
            parsed.custom["tokenreplace"]?.let { currentTokenReplace = it }
            parsed.custom["tokenid"]?.toIntOrNull()?.let { currentTokenId = it }

            buildDrm(parsed.custom)?.let { currentDrm = it }
        }

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                currentLogo = RE_LOGO.find(line)?.let { m ->
                    m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[3]?.value
                }?.trim() ?: ""

                val groupFromAttr = RE_GROUP.find(line)?.let { m ->
                    m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[3]?.value
                }?.trim()
                if (!groupFromAttr.isNullOrBlank()) currentGroup = groupFromAttr

                RE_UA.find(line)?.let { m ->
                    currentUserAgent = m.groups[1]?.value ?: m.groups[2]?.value
                }
                RE_REF.find(line)?.let { m ->
                    currentHttpReferer = m.groups[1]?.value ?: m.groups[2]?.value
                }
                RE_ORIGIN.find(line)?.let { m ->
                    currentOrigin = m.groups[1]?.value ?: m.groups[2]?.value
                }
                RE_COOKIE.find(line)?.let { m ->
                    currentCookie = m.groups[1]?.value ?: m.groups[2]?.value
                }
                RE_TOKEN_ID.find(line)?.let {
                    currentTokenId = it.groupValues[1].toIntOrNull() ?: currentTokenId
                }

                val commaIndex = firstCommaOutsideQuotes(line)
                currentName = if (commaIndex != -1) {
                    line.substring(commaIndex + 1).trim().ifBlank { "Unknown Channel" }
                } else "Unknown Channel"

            } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                val grp = line.substringAfter(":").trim()
                if (currentGroup.isBlank() && grp.isNotEmpty()) currentGroup = grp

            } else if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = line.substringAfter(":").trim()
                val key = opt.substringBefore('=').trim().lowercase()
                val value = opt.substringAfter('=', "").trim()
                when (key) {
                    "http-user-agent" -> currentUserAgent = value
                    "http-referrer", "http-referer" -> currentHttpReferer = value
                    "http-origin" -> currentOrigin = value
                    "cookie", "http-cookie" -> currentCookie = value
                    else -> {
                        // Any other EXTVLCOPT becomes a passthrough header.
                        if (value.isNotEmpty()) {
                            mergeExtraHeaders(mapOf(opt.substringBefore('=').trim() to value))
                        }
                    }
                }

            } else if (line.startsWith("#KODIPROP:", ignoreCase = true) ||
                line.startsWith("#KODIPROP.", ignoreCase = true)) {
                // ── KODIPROP DRM (Kodi / TiviMate / OTT Navigator) ──
                val payload = line.substringAfter(":").trim()
                when {
                    payload.startsWith("inputstream.adaptive.license_type", ignoreCase = true) ||
                        payload.startsWith("inputstream.adaptive.drm_type", ignoreCase = true) -> {
                        val type = payload.substringAfter("=", "").trim().lowercase()
                        val parsedType = when {
                            type.contains("clearkey") || type.contains("org.w3.clearkey") -> "clearkey"
                            type.contains("widevine") || type.contains("com.widevine") -> "widevine"
                            type.contains("playready") || type.contains("com.microsoft.playready") -> "playready"
                            type.contains("fairplay") || type.contains("com.apple.fps") -> "fairplay"
                            else -> null
                        }
                        currentDrm = currentDrm?.copy(type = parsedType) ?: DrmConfig(type = parsedType)
                    }
                    payload.startsWith("inputstream.adaptive.license_key", ignoreCase = true) ||
                        payload.startsWith("inputstream.adaptive.drm_key", ignoreCase = true) -> {
                        val raw = payload.substringAfter("=", "").trim()
                        // Kodi sometimes embeds the key as JSON: {"keys":[...]}
                        currentDrm = when {
                            raw.isEmpty() -> currentDrm
                            raw.startsWith("{") -> {
                                // Inline JWK / ClearKey JSON.
                                (currentDrm ?: DrmConfig(type = "clearkey"))
                                    .copy(type = "clearkey", licenseServer = raw)
                            }
                            RE_CLEARKEY_ANYWHERE.containsMatchIn(raw) -> {
                                val pairs = RE_CLEARKEY_ANYWHERE.findAll(raw).map { it.value }.distinct().joinToString(",")
                                (currentDrm ?: DrmConfig(type = "clearkey"))
                                    .copy(type = "clearkey", rawKeyPair = pairs)
                            }
                            else -> {
                                // Treat as license server URL.
                                currentDrm?.copy(licenseServer = raw)
                                    ?: DrmConfig(licenseServer = raw)
                            }
                        }
                    }
                    payload.startsWith("inputstream.adaptive.license_headers", ignoreCase = true) -> {
                        // Not commonly used, but pass through if present.
                        val raw = payload.substringAfter("=", "").trim()
                        if (raw.isNotEmpty()) {
                            try {
                                val obj = JSONObject(raw)
                                val map = mutableMapOf<String, String>()
                                obj.keys().forEach { k -> obj.str(k)?.let { v -> map[k] = v } }
                                if (map.isNotEmpty()) mergeExtraHeaders(map)
                            } catch (_: Exception) { /* ignore malformed */ }
                        }
                    }
                }

            } else if (line.startsWith("#EXTHTTP:", ignoreCase = true)) {
                try {
                    val payload = line.substringAfter(":").trim()
                    if (payload.startsWith("{")) {
                        val jsonObj = JSONObject(payload)
                        val map = mutableMapOf<String, String>()

                        fun processHeader(k: String, v: String) {
                            when (k.lowercase()) {
                                "cookie" -> currentCookie = v
                                "user-agent" -> currentUserAgent = v
                                "referer", "referrer" -> currentHttpReferer = v
                                "origin" -> currentOrigin = v
                                "authorization" -> map["Authorization"] = v
                                else -> map[k] = v
                            }
                        }

                        jsonObj.optJSONObject("headers")?.let { headersObj ->
                            headersObj.keys().forEach { k ->
                                val v = headersObj.str(k) ?: return@forEach
                                processHeader(k, v)
                            }
                        }
                        jsonObj.keys().forEach { k ->
                            if (!k.equals("headers", ignoreCase = true)) {
                                val v = jsonObj.str(k) ?: return@forEach
                                processHeader(k, v)
                            }
                        }
                        if (map.isNotEmpty()) mergeExtraHeaders(map)
                    } else if (payload.isNotEmpty()) {
                        currentCookie = payload
                    }
                } catch (_: Exception) { /* ignore malformed JSON */ }

            } else if (RE_ATTR_PREFIX.containsMatchIn(line)) {
                val attrUrl = line.replaceFirst(RE_ATTR_PREFIX, "").trim()
                if (attrUrl.contains("|") || attrUrl.contains("&")) {
                    applyParsed(parseUrlHeaders(attrUrl))
                }

            } else if (!line.startsWith("#")) {
                val cleanUrl: String
                if (RE_HEADER_SECTION_START.containsMatchIn(line)) {
                    val parsed = parseUrlHeaders(line)
                    cleanUrl = parsed.cleanUrl
                    applyParsed(parsed)
                } else {
                    cleanUrl = line
                }

                if (cleanUrl.isNotEmpty()) {
                    channels.add(
                        Channel(
                            name = currentName.ifBlank { "Unknown Channel" },
                            logo = currentLogo,
                            group = currentGroup,
                            url = line, // preserve original pipe/amp headers
                            userAgent = currentUserAgent,
                            cookie = currentCookie,
                            httpReferer = currentHttpReferer,
                            origin = currentOrigin,
                            httpHeaders = currentHttpHeaders,
                            tokenUrl = currentTokenUrl,
                            tokenMatch = currentTokenMatch,
                            tokenReplace = currentTokenReplace,
                            tokenId = currentTokenId,
                            drm = currentDrm,
                            isLiveEvent = isLiveUrl(cleanUrl)
                        )
                    )
                }
                resetCurrentChannel()
            }
        }
        return channels
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun firstCommaOutsideQuotes(line: String): Int {
        var inQuotes = false
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == ',' && !inQuotes) return i
        }
        return -1
    }

    private fun isLiveUrl(cleanUrl: String): Boolean {
        if (RE_VOD.containsMatchIn(cleanUrl)) return false
        return cleanUrl.contains(".m3u8", ignoreCase = true) ||
                cleanUrl.contains(".mpd", ignoreCase = true) ||
                RE_TS.containsMatchIn(cleanUrl) ||
                cleanUrl.contains("live", ignoreCase = true)
    }

    private fun extraHeaders(headers: Map<String, String>): Map<String, String>? =
        headers.filterKeys { it.lowercase() !in STANDARD_HEADERS }.takeIf { it.isNotEmpty() }

    private fun buildDrm(custom: Map<String, String>): DrmConfig? {
        val type = custom["drm-type"] ?: return null
        val key = custom["drm-url"] ?: custom["drm-key"] ?: return DrmConfig(type = type)
        return if (RE_CLEARKEY_ANYWHERE.containsMatchIn(key)) {
            val pairs = RE_CLEARKEY_ANYWHERE.findAll(key).map { it.value }.distinct().joinToString(",")
            DrmConfig(type = "clearkey", rawKeyPair = pairs)
        } else {
            DrmConfig(type = type, licenseServer = key)
        }
    }

    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key).trim()
        return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
    }

    private fun JSONObject.firstStr(vararg keys: String): String? {
        for (k in keys) str(k)?.let { return it }
        return null
    }

    // ---------------------------------------------------------------------
    // PLS
    // ---------------------------------------------------------------------

    private fun parsePLS(content: String): List<Channel> {
        val entries = sortedMapOf<Int, MutableMap<String, String>>()
        for (rawLine in content.lineSequence()) {
            val m = RE_PLS_LINE.find(rawLine.trim()) ?: continue
            val idx = m.groupValues[2].toIntOrNull() ?: 0
            val entry = entries.getOrPut(idx) { mutableMapOf() }
            entry[m.groupValues[1].lowercase()] = m.groupValues[3].trim()
        }

        val channels = mutableListOf<Channel>()
        for ((_, entry) in entries) {
            val url = entry["file"]?.takeIf { it.isNotEmpty() } ?: continue
            var title = entry["title"].orEmpty()
            var group = entry["group"].orEmpty()

            if (group.isEmpty()) {
                RE_PLS_TITLE_GROUP.find(title)?.let { gm ->
                    group = gm.groupValues[1].trim()
                    title = gm.groupValues[2].trim()
                }
            }

            val parsed = parseUrlHeaders(url)
            channels.add(
                Channel(
                    name = title.ifEmpty { "Unknown Channel" },
                    url = url,
                    group = group,
                    userAgent = parsed.headers["user-agent"],
                    httpReferer = parsed.headers["referer"],
                    origin = parsed.headers["origin"],
                    cookie = parsed.headers["cookie"],
                    httpHeaders = extraHeaders(parsed.headers),
                    tokenUrl = parsed.custom["tokenurl"],
                    tokenMatch = parsed.custom["tokenmatch"],
                    tokenReplace = parsed.custom["tokenreplace"],
                    tokenId = parsed.custom["tokenid"]?.toIntOrNull(),
                    drm = buildDrm(parsed.custom),
                    isLiveEvent = true
                )
            )
        }
        return channels
    }

    // ---------------------------------------------------------------------
    // XSPF
    // ---------------------------------------------------------------------

    private fun xmlUnescape(s: String): String {
        val t = s.trim()
        if (t.startsWith("<![CDATA[") && t.endsWith("]]>")) {
            return t.removePrefix("<![CDATA[").removeSuffix("]]>").trim()
        }
        val withNumeric = RE_NUMERIC_ENTITY.replace(t) { m ->
            val isHex = m.groupValues[1].isNotEmpty()
            val code = m.groupValues[2].toIntOrNull(if (isHex) 16 else 10)
            if (code != null && code in 1..0x10FFFF) {
                try { String(Character.toChars(code)) } catch (_: Exception) { m.value }
            } else m.value
        }
        return withNumeric
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun xmlValue(regex: Regex, block: String): String? =
        regex.find(block)?.groupValues?.get(1)?.let { xmlUnescape(it) }

    private fun parseXSPF(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()

        for (match in RE_XSPF_TRACK.findAll(content)) {
            val trackContent = match.groupValues[1]
            val title = xmlValue(RE_XSPF_TITLE, trackContent)?.ifBlank { null } ?: "Unknown Channel"
            val locations = RE_XSPF_LOCATION.findAll(trackContent)
                .mapNotNull { xmlUnescape(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .toList()
            val location = locations.firstOrNull()
            val image = xmlValue(RE_XSPF_IMAGE, trackContent)
            val creator = xmlValue(RE_XSPF_CREATOR, trackContent)?.ifBlank { null }
            val annotation = xmlValue(RE_XSPF_ANNOTATION, trackContent)?.ifBlank { null }

            if (location != null) {
                val parsed = parseUrlHeaders(location)
                val group = creator ?: annotation ?: ""
                val extraSources = if (locations.size > 1) {
                    locations.drop(1).mapIndexed { i, u -> "alt${i + 1}" to u }.toMap()
                } else emptyMap()

                channels.add(
                    Channel(
                        name = title,
                        url = location,
                        logo = image ?: "",
                        group = group,
                        userAgent = parsed.headers["user-agent"],
                        httpReferer = parsed.headers["referer"],
                        origin = parsed.headers["origin"],
                        cookie = parsed.headers["cookie"],
                        httpHeaders = extraHeaders(parsed.headers),
                        tokenUrl = parsed.custom["tokenurl"],
                        tokenMatch = parsed.custom["tokenmatch"],
                        tokenReplace = parsed.custom["tokenreplace"],
                        tokenId = parsed.custom["tokenid"]?.toIntOrNull(),
                        drm = buildDrm(parsed.custom),
                        isLiveEvent = true,
                        sources = extraSources
                    )
                )
            }
        }
        return channels
    }

    // ---------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------

    private fun parseJSONArray(array: JSONArray): List<Channel> {
        val channels = mutableListOf<Channel>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.optJSONObject(i) ?: continue
                var rawUrl = obj.firstStr("url", "link") ?: ""
                val sourcesMap = mutableMapOf<String, String>()

                obj.optJSONObject("sources")?.let { sourcesObj ->
                    sourcesObj.keys().forEach { key ->
                        sourcesObj.str(key)?.let { sourcesMap[key] = it }
                    }
                    if (rawUrl.isEmpty() && sourcesMap.isNotEmpty()) {
                        rawUrl = sourcesMap.values.first()
                    }
                }
                if (rawUrl.isEmpty()) continue

                val parsed = parseUrlHeaders(rawUrl)
                var ua = parsed.headers["user-agent"]
                var ref = parsed.headers["referer"]
                var origin = parsed.headers["origin"]
                var cookie = parsed.headers["cookie"]

                obj.str("cookie")?.let { cookie = it }
                obj.firstStr("referer", "referrer")?.let { ref = it }
                obj.str("origin")?.let { origin = it }
                obj.firstStr("userAgent", "user-agent", "user_agent")?.let { ua = it }

                var httpHeaders: MutableMap<String, String>? = null
                obj.optJSONObject("headers")?.let { hObj ->
                    hObj.keys().forEach { k ->
                        val v = hObj.str(k) ?: return@forEach
                        when (k.lowercase()) {
                            "cookie" -> cookie = v
                            "user-agent" -> ua = v
                            "referer", "referrer" -> ref = v
                            "origin" -> origin = v
                            else -> {
                                if (httpHeaders == null) httpHeaders = mutableMapOf()
                                httpHeaders!![k] = v
                            }
                        }
                    }
                }

                extraHeaders(parsed.headers)?.let { extra ->
                    if (httpHeaders == null) httpHeaders = mutableMapOf()
                    httpHeaders!!.putAll(extra)
                }

                val name = obj.firstStr("name", "title") ?: "Unknown Channel"
                val logo = extractBestLogo(obj)
                val group = obj.firstStr("group-title", "group", "category", "groupTitle") ?: ""
                val isLive = obj.optBoolean("isLive", isLiveUrl(rawUrl))

                channels.add(
                    Channel(
                        name = name,
                        url = rawUrl,
                        logo = logo,
                        group = group,
                        userAgent = ua,
                        httpReferer = ref,
                        origin = origin,
                        cookie = cookie,
                        httpHeaders = httpHeaders,
                        tokenUrl = parsed.custom["tokenurl"] ?: obj.str("tokenUrl"),
                        tokenMatch = parsed.custom["tokenmatch"] ?: obj.str("tokenMatch"),
                        tokenReplace = parsed.custom["tokenreplace"] ?: obj.str("tokenReplace"),
                        tokenId = parsed.custom["tokenid"]?.toIntOrNull()
                            ?: obj.str("tokenId")?.toIntOrNull(),
                        drm = buildDrm(parsed.custom),
                        isLiveEvent = isLive,
                        sources = sourcesMap
                    )
                )
            } catch (_: Exception) { /* skip malformed entry */ }
        }
        return channels
    }

    // ---------------------------------------------------------------------
    // URL pipe headers
    //
    //   url|Key=Value|Key2=Value2
    //   url|Key=Value&Key2=Value2
    //   url|Key=Value|Key2=Value2&Key3=Value3
    //   url?a=1&b=2|Key=Value
    //   url|<kid32>:<key32>|clearkey
    //   url|clearkey|<kid32>:<key32>
    //   url|drmScheme=clearkey&drmLicense=<kid>:<key>
    //   url|drm_type=clearkey|drm_key=<kid>:<key>
    //   url|<anything>=<value>|sec-fetch-site=cross-site
    // ---------------------------------------------------------------------

    data class ParsedUrl(
        val cleanUrl: String,
        val headers: Map<String, String>,
        val custom: Map<String, String>
    )

    fun parseUrlHeaders(line: String): ParsedUrl {
        val headerStart = RE_HEADER_SECTION_START.find(line)?.range?.first

        val rawUrl: String
        val headerSection: String?
        if (headerStart == null) {
            rawUrl = line.trim()
            headerSection = null
        } else {
            rawUrl = line.substring(0, headerStart).trim()
            headerSection = line.substring(headerStart + 1)
        }

        val headers = linkedMapOf<String, String>()
        val custom = linkedMapOf<String, String>()

        if (headerSection == null) return ParsedUrl(rawUrl, headers, custom)

        for (block in headerSection.split(RE_PIPE_SPLIT)) {
            for (piece in block.split(RE_AMP_SPLIT)) {
                val trimmed = piece.trim()
                if (trimmed.isEmpty()) continue
                processHeaderPiece(trimmed, headers, custom)
            }
        }

        return ParsedUrl(rawUrl, headers, custom)
    }

    /**
     * Process a single `Key=Value`, `Key:Value`, `kid:key`, or bare
     * `clearkey` / `widevine` token. Populates [headers] and [custom].
     */
    private fun processHeaderPiece(
        piece: String,
        headers: MutableMap<String, String>,
        custom: MutableMap<String, String>
    ) {
        // ── 1) Raw ClearKey pair(s): <32hex>:<32hex> ──
        if (RE_CLEARKEY_ANYWHERE.containsMatchIn(piece)) {
            val pairs = RE_CLEARKEY_ANYWHERE.findAll(piece).map { it.value }.distinct().joinToString(",")
            custom["drm-key"] = pairs
            if (!custom.containsKey("drm-type")) custom["drm-type"] = "clearkey"
            return
        }

        // ── 2) Bare DRM scheme marker ──
        when (piece.lowercase()) {
            "clearkey" -> {
                if (!custom.containsKey("drm-type")) custom["drm-type"] = "clearkey"
                return
            }
            "widevine" -> {
                if (!custom.containsKey("drm-type")) custom["drm-type"] = "widevine"
                return
            }
            "playready" -> {
                if (!custom.containsKey("drm-type")) custom["drm-type"] = "playready"
                return
            }
            "fairplay" -> {
                if (!custom.containsKey("drm-type")) custom["drm-type"] = "fairplay"
                return
            }
        }

        // ── 3) key=value or key:value ──
        val sepIdx = indexOfSeparator(piece)
        if (sepIdx <= 0) return

        val key = piece.substring(0, sepIdx).trim()
        val value = piece.substring(sepIdx + 1).trim()
        if (key.isEmpty()) return

        routeHeader(key, value, headers, custom)
    }

    /**
     * Returns the position of the first `=` or `:` that acts as a key /
     * value separator. If both appear, the earlier one wins — this lets
     * `Referer=https://host/path` split on `=` before the `:` inside the
     * URL. If the `:` precedes any `=` but is followed by `//` (URL
     * scheme), we prefer the `=` instead.
     */
    private fun indexOfSeparator(piece: String): Int {
        val eq = piece.indexOf('=')
        val co = piece.indexOf(':')
        return when {
            eq == -1 && co == -1 -> -1
            eq == -1 -> co
            co == -1 -> eq
            else -> minOf(eq, co)
        }
    }

    /**
     * Route a parsed header to the right destination.
     *
     *  • Standard HTTP headers (user-agent / referer / origin / cookie /
     *    authorization) go into [headers] with lowercase canonical keys.
     *  • DRM + token custom parameters go into [custom].
     *  • PlayRef aliases go into both — headers["PlayRef"] for downstream
     *    token resolvers, and custom["playref"] for the MediaResolver.
     *  • Everything else passes through verbatim — the caller decides
     *    what to do with it.
     */
    private fun routeHeader(
        key: String,
        value: String,
        headers: MutableMap<String, String>,
        custom: MutableMap<String, String>
    ) {
        val lower = key.lowercase()

        // ── PlayRef family (dual-write) ──
        val playRefAliases = setOf(
            "playref", "play-ref", "streamref", "stream-ref",
            "stream-referer", "m3u8-referer", "cdnref", "mediaref", "liveref"
        )
        if (lower in playRefAliases) {
            custom["playref"] = value
            headers["PlayRef"] = value
            return
        }

        val canonical = when (lower) {
            // ── HTTP headers ──
            "user-agent", "http-user-agent", "useragent", "http-useragent", "ua" ->
                "user-agent"
            "referer", "referrer", "http-referer", "http-referrer", "httpreferer", "ref" ->
                "referer"
            "origin", "http-origin" -> "origin"
            "cookie", "http-cookie" -> "cookie"
            "authorization", "auth", "http-authorization" -> "authorization"

            // ── DRM scheme ──
            "drmscheme", "drm-scheme", "drm_scheme", "drmtype", "drm-type", "drm_type",
            "license_type", "license-type", "licensetype" -> "drm-type"

            // ── DRM key / license ──
            "drmlicense", "drm-license", "drm_license",
            "drmkey", "drm-key", "drm_key",
            "license_key", "license-key", "licensekey",
            "drm_license_url", "drm-license-url" -> "drm-key"

            "drm_url", "drm-url", "drmurl", "drm-license-server" -> "drm-url"

            // ── Token ──
            "tokenurl", "token-url", "token_url" -> "tokenurl"
            "tokenid", "token-id", "token_id" -> "tokenid"
            "tokenmatch", "token-match", "token_match" -> "tokenmatch"
            "tokenreplace", "token-replace", "token_replace" -> "tokenreplace"

            else -> null
        }

        when (canonical) {
            // Standard HTTP headers → lowercase into `headers`.
            "user-agent", "referer", "origin", "cookie", "authorization" -> {
                headers[canonical] = value
            }
            // DRM + token → custom.
            "drm-type", "drm-key", "drm-url",
            "tokenurl", "tokenid", "tokenmatch", "tokenreplace" -> {
                custom[canonical] = value
            }
            // Unknown key → passthrough with original casing.
            null -> {
                headers[key] = value
            }
        }
    }

    // ---------------------------------------------------------------------
    // Logo
    // ---------------------------------------------------------------------

    fun extractBestLogo(element: JSONObject, configuredField: String = ""): String {
        val candidates = mutableListOf<String>()
        if (configuredField.isNotBlank()) candidates.add(configuredField)
        candidates.addAll(
            listOf(
                "tvg-logo", "logo", "logo_url", "poster_url", "poster",
                "image", "icon", "thumb", "channel_logo", "tvgLogo", "logoUrl"
            )
        )

        var firstNonEmpty = ""
        for (field in candidates) {
            val valStr = element.str(field) ?: continue
            if (valStr.startsWith("http://", true) ||
                valStr.startsWith("https://", true) ||
                valStr.startsWith("//")
            ) {
                val url = if (valStr.startsWith("//")) "https:$valStr" else valStr
                return url.replace(" ", "%20")
            }
            if (firstNonEmpty.isEmpty()) firstNonEmpty = valStr
        }

        if (firstNonEmpty.isNotEmpty() && !firstNonEmpty.startsWith("http", true)) {
            if (firstNonEmpty.endsWith(".png", true) ||
                firstNonEmpty.endsWith(".webp", true) ||
                firstNonEmpty.endsWith(".jpg", true) ||
                firstNonEmpty.endsWith(".jpeg", true)
            ) {
                return firstNonEmpty.replace(" ", "%20")
            }
        }
        return firstNonEmpty.replace(" ", "%20")
    }
}
