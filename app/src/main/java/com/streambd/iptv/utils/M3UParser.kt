package com.streambd.iptv.utils

import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.DrmConfig
import org.json.JSONArray
import org.json.JSONObject

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object M3UParser {

    // ---------------------------------------------------------------------
    // Pre-compiled regexes
    // ---------------------------------------------------------------------
    private val IC = RegexOption.IGNORE_CASE

    private val RE_LOGO = Regex("""(?<![\w-])(?:tvg-logo|logo|icon|poster)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""", IC)
    private val RE_GROUP = Regex("""group-title\s*=\s*(?:"([^"]*)"|'([^']*)')""", IC)
    private val RE_UA = Regex("""http-user-agent\s*=\s*"([^"]*)"""", IC)
    private val RE_REF = Regex("""http-refer(?:r)?er\s*=\s*"([^"]*)"""", IC)
    private val RE_ORIGIN = Regex("""http-origin\s*=\s*"([^"]*)"""", IC)
    private val RE_COOKIE = Regex("""http-cookie\s*=\s*"([^"]*)"""", IC)
    private val RE_TOKEN_ID = Regex("""(?:token-id|tokenid)\s*=\s*"?([^",\s]+)"?""", IC)

    private val RE_ATTR_PREFIX = Regex("""^#\s*EXTATTRFROMURL:\s*""", IC)
    private val RE_ATTR_ANYWHERE = Regex("""#\s*EXTATTRFROMURL:""", IC)
    private val RE_CLEARKEY_PAIR = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$")

    private val RE_PLS_LINE = Regex("""^(file|title|group)(\d*)\s*=\s*(.*)$""", IC)
    private val RE_PLS_TITLE_GROUP = Regex("""^(.+?)\s+[-–—]\s+(.+)$""")

    private val RE_XSPF_TRACK = Regex("""<track>([\s\S]*?)</track>""", IC)
    private val RE_XSPF_TITLE = Regex("""<title>([\s\S]*?)</title>""", IC)
    private val RE_XSPF_LOCATION = Regex("""<location>([\s\S]*?)</location>""", IC)
    private val RE_XSPF_IMAGE = Regex("""<image>([\s\S]*?)</image>""", IC)
    private val RE_XSPF_CREATOR = Regex("""<creator>([\s\S]*?)</creator>""", IC)
    private val RE_XSPF_ANNOTATION = Regex("""<annotation>([\s\S]*?)</annotation>""", IC)

    private val RE_VOD = Regex("""\.(mp4|mkv|avi|mov|webm|flv|wmv|m4v)(?:$|[?#|])""", IC)
    private val RE_TS = Regex("""\.ts(?:$|[?#|])""", IC)

    private val RE_LOGO_NUMERIC = Regex("""^\d{10,}\.(png|jpg|jpeg|webp)$""", IC)

    // '&' দিয়ে আলাদা করা হবে শুধু যখন পরের অংশ পরিচিত key দিয়ে শুরু হয়।
    // এতে Referer=https://x.com/?a=1&b=2 এর মতো ভ্যালু ভেঙে যায় না।
    // অপরিচিত কাস্টম হেডারের জন্য আলাদা '|' ব্যবহার করুন: url|MyHeader=1|Other=2
    private const val KNOWN_KEYS =
        "user-agent|referer|referrer|origin|cookie|authorization|http-[a-z-]+|x-[a-z0-9-]+|" +
        "token[a-z-]*|drm[a-z-]*|playref|play-ref|streamref|stream-ref|stream-referer|" +
        "m3u8-referer|cdnref|mediaref|liveref"
    private val RE_AMP_SPLIT = Regex("&(?=(?:$KNOWN_KEYS)\\s*[=:])", IC)

    private val STANDARD_HEADERS = setOf("user-agent", "referer", "origin", "cookie")

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    suspend fun preprocessM3U(content: String): String = withContext(Dispatchers.IO) {
        content.removePrefix("\uFEFF")
    }

    fun parsePlaylist(content: String): List<Channel> {
        // trim() BOM সরায় না, তাই আগে সরানো হচ্ছে
        val trimmed = content.removePrefix("\uFEFF").trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.contains("#EXTM3U") || trimmed.contains("#EXTINF:") || RE_ATTR_ANYWHERE.containsMatchIn(trimmed)) {
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
            // JSON নয়, নিচে M3U হিসেবে চেষ্টা হবে
        }

        // শুধু URL-এর তালিকা হলেও কাজ করবে
        return try {
            parseM3U(trimmed)
        } catch (e: Exception) {
            emptyList()
        }
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

        fun applyParsed(parsed: ParsedUrl) {
            currentHttpReferer = parsed.headers["referer"] ?: currentHttpReferer
            currentUserAgent = parsed.headers["user-agent"] ?: currentUserAgent
            currentOrigin = parsed.headers["origin"] ?: currentOrigin
            currentCookie = parsed.headers["cookie"] ?: currentCookie

            // পাইপের কাস্টম হেডার (X-Token, PlayRef ইত্যাদি) এখন httpHeaders এও যায়
            val extra = extraHeaders(parsed.headers)
            if (extra != null) {
                val map = currentHttpHeaders ?: mutableMapOf()
                map.putAll(extra)
                currentHttpHeaders = map
            }

            currentTokenUrl = parsed.custom["tokenurl"] ?: currentTokenUrl
            currentTokenMatch = parsed.custom["tokenmatch"] ?: currentTokenMatch
            currentTokenReplace = parsed.custom["tokenreplace"] ?: currentTokenReplace
            currentTokenId = parsed.custom["tokenid"]?.toIntOrNull() ?: currentTokenId

            // |drmScheme=clearkey&drmLicense=kid:key
            buildDrm(parsed.custom)?.let { currentDrm = it }
        }

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                // এখানে পুরো reset করা হয় না: অনেক প্লেলিস্টে #KODIPROP / #EXTVLCOPT
                // লাইন #EXTINF এর আগে থাকে
                currentLogo = RE_LOGO.find(line)?.let { m ->
                    m.groups[1]?.value ?: m.groups[2]?.value ?: m.groups[3]?.value
                }?.trim() ?: ""

                val groupFromAttr = RE_GROUP.find(line)?.let { m ->
                    m.groups[1]?.value ?: m.groups[2]?.value
                }?.trim()
                if (!groupFromAttr.isNullOrBlank()) currentGroup = groupFromAttr

                RE_UA.find(line)?.let { currentUserAgent = it.groupValues[1] }
                RE_REF.find(line)?.let { currentHttpReferer = it.groupValues[1] }
                RE_ORIGIN.find(line)?.let { currentOrigin = it.groupValues[1] }
                RE_COOKIE.find(line)?.let { currentCookie = it.groupValues[1] }
                RE_TOKEN_ID.find(line)?.let { currentTokenId = it.groupValues[1].toIntOrNull() }

                // চ্যানেলের নাম = quote-এর বাইরের প্রথম কমার পরের অংশ
                val commaIndex = firstCommaOutsideQuotes(line)
                currentName = if (commaIndex != -1) {
                    line.substring(commaIndex + 1).trim().ifBlank { "Unknown Channel" }
                } else {
                    "Unknown Channel"
                }
            } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                val grp = line.substringAfter(":").trim()
                if (currentGroup.isBlank() && grp.isNotEmpty()) currentGroup = grp
            } else if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = line.substring(11).trim()
                val key = opt.substringBefore('=').trim().lowercase()
                val value = opt.substringAfter('=', "").trim()
                when (key) {
                    "http-user-agent" -> currentUserAgent = value
                    "http-referrer", "http-referer" -> currentHttpReferer = value
                    "http-origin" -> currentOrigin = value
                    "cookie", "http-cookie" -> currentCookie = value
                }
            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=", ignoreCase = true)) {
                val type = line.substringAfter("=").trim().lowercase()
                val parsedType = when {
                    type.contains("widevine") -> "widevine"
                    type.contains("playready") -> "playready"
                    type.contains("clearkey") -> "clearkey"
                    else -> null
                }
                currentDrm = currentDrm?.copy(type = parsedType) ?: DrmConfig(type = parsedType)
            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key=", ignoreCase = true)) {
                val key = line.substringAfter("=").trim()
                val isRawClearKeyPair = RE_CLEARKEY_PAIR.matches(key)
                currentDrm = if (isRawClearKeyPair) {
                    currentDrm?.copy(type = "clearkey", rawKeyPair = key) ?: DrmConfig(type = "clearkey", rawKeyPair = key)
                } else {
                    currentDrm?.copy(licenseServer = key) ?: DrmConfig(licenseServer = key)
                }
            } else if (line.startsWith("#EXTHTTP:", ignoreCase = true)) {
                try {
                    val payload = line.substringAfter(":").trim()
                    if (payload.startsWith("{")) {
                        val jsonObj = JSONObject(payload)
                        val map = currentHttpHeaders ?: mutableMapOf()

                        fun processHeader(k: String, v: String) {
                            when (k.lowercase()) {
                                "cookie" -> currentCookie = v
                                "user-agent" -> currentUserAgent = v
                                "referer", "referrer" -> currentHttpReferer = v
                                "origin" -> currentOrigin = v
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
                        if (map.isNotEmpty()) currentHttpHeaders = map
                    } else if (payload.isNotEmpty()) {
                        currentCookie = payload
                    }
                } catch (e: Exception) {
                    // Ignore parse error
                }
            } else if (RE_ATTR_PREFIX.containsMatchIn(line)) {
                val attrUrl = line.replaceFirst(RE_ATTR_PREFIX, "").trim()
                if (attrUrl.contains("|")) {
                    applyParsed(parseUrlHeaders(attrUrl))
                }
            } else if (!line.startsWith("#")) {
                val cleanUrl: String
                if (line.contains("|")) {
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
                            url = line, // Preserve pipe headers for LivePlayerScreen
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
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                return i
            }
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
        val key = custom["drm-url"] ?: custom["drm-key"]
        return if (key != null && RE_CLEARKEY_PAIR.matches(key)) {
            DrmConfig(type = "clearkey", rawKeyPair = key)
        } else {
            DrmConfig(type = type, licenseServer = key)
        }
    }

    // org.json (Android) এ optString JSON null-কে "null" স্ট্রিং বানায়, তাই নিজস্ব helper
    private fun JSONObject.str(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key).trim()
        return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
    }

    private fun JSONObject.firstStr(vararg keys: String): String? {
        for (k in keys) {
            val v = str(k)
            if (v != null) return v
        }
        return null
    }

    // ---------------------------------------------------------------------
    // PLS  (File1 আগে, Title1 পরে - তাই নম্বর ধরে গ্রুপ করা হয়)
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
                    url = url, // Preserve pipe
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
        return t.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&") // সবার শেষে
    }

    private fun xmlValue(regex: Regex, block: String): String? =
        regex.find(block)?.groupValues?.get(1)?.let { xmlUnescape(it) }

    private fun parseXSPF(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()

        for (match in RE_XSPF_TRACK.findAll(content)) {
            val trackContent = match.groupValues[1]
            val title = xmlValue(RE_XSPF_TITLE, trackContent)?.ifBlank { null } ?: "Unknown Channel"
            val location = xmlValue(RE_XSPF_LOCATION, trackContent)?.ifBlank { null }
            val image = xmlValue(RE_XSPF_IMAGE, trackContent)
            val creator = xmlValue(RE_XSPF_CREATOR, trackContent)?.ifBlank { null }
            val annotation = xmlValue(RE_XSPF_ANNOTATION, trackContent)?.ifBlank { null }

            if (location != null) {
                val parsed = parseUrlHeaders(location)
                val group = creator ?: annotation ?: ""

                channels.add(
                    Channel(
                        name = title,
                        url = location, // Preserve pipe
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
                        isLiveEvent = true
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
                        val srcUrl = sourcesObj.str(key)
                        if (srcUrl != null) sourcesMap[key] = srcUrl
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

                val urlCustomHeaders = extraHeaders(parsed.headers)
                if (urlCustomHeaders != null) {
                    if (httpHeaders == null) httpHeaders = mutableMapOf()
                    httpHeaders!!.putAll(urlCustomHeaders)
                }

                val name = obj.firstStr("name", "title") ?: "Unknown Channel"
                val logo = extractBestLogo(obj)
                val group = obj.firstStr("group-title", "group", "category", "groupTitle") ?: ""
                val isLive = obj.optBoolean("isLive", true)

                channels.add(
                    Channel(
                        name = name,
                        url = rawUrl, // Preserve pipe
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
                        tokenId = parsed.custom["tokenid"]?.toIntOrNull() ?: obj.str("tokenId")?.toIntOrNull(),
                        drm = buildDrm(parsed.custom),
                        isLiveEvent = isLive,
                        sources = sourcesMap
                    )
                )
            } catch (e: Exception) {
                // এই এন্ট্রি বাদ, বাকিগুলো চলবে
            }
        }
        return channels
    }

    // ---------------------------------------------------------------------
    // URL pipe headers:  url|Key=Value|Key2:Value2&Key3=Value3
    // ---------------------------------------------------------------------

    data class ParsedUrl(
        val cleanUrl: String,
        val headers: Map<String, String>,
        val custom: Map<String, String>
    )

    fun parseUrlHeaders(line: String): ParsedUrl {
        val parts = line.split("|")
        val cleanUrl = parts.firstOrNull()?.trim() ?: ""
        val headers = mutableMapOf<String, String>()
        val custom = mutableMapOf<String, String>()

        for (i in 1 until parts.size) {
            val headerPart = parts[i].trim()
            if (headerPart.isEmpty()) continue

            val subParts = headerPart.split(RE_AMP_SPLIT)

            for (subPart in subParts) {
                val trimmed = subPart.trim()
                if (trimmed.isEmpty()) continue

                val colonIndex = trimmed.indexOf(':')
                val equalsIndex = trimmed.indexOf('=')

                val separatorIndex = when {
                    colonIndex != -1 && equalsIndex != -1 -> minOf(colonIndex, equalsIndex)
                    colonIndex != -1 -> colonIndex
                    else -> equalsIndex
                }
                if (separatorIndex <= 0) continue

                val key = trimmed.substring(0, separatorIndex).trim()
                val value = trimmed.substring(separatorIndex + 1).trim()
                if (key.isEmpty()) continue

                val normalized = when (val lower = key.lowercase()) {
                    "drmscheme" -> "drm-type"
                    "drmlicense", "drmkey" -> "drm-key"
                    "token-url" -> "tokenurl"
                    "http-referer", "http-referrer", "referrer" -> "referer"
                    "http-user-agent" -> "user-agent"
                    "http-origin" -> "origin"
                    "http-cookie" -> "cookie"
                    else -> lower
                }

                when (normalized) {
                    "tokenurl", "tokenid", "tokenmatch", "tokenreplace", "drm-type", "drm-url", "drm-key" -> {
                        custom[key] = value
                        custom[normalized] = value
                    }
                    "playref", "play-ref", "streamref", "stream-ref", "stream-referer",
                    "m3u8-referer", "cdnref", "mediaref", "liveref" -> {
                        custom["playref"] = value
                        custom[key] = value
                        custom[normalized] = value
                        headers["PlayRef"] = value
                    }
                    "user-agent", "referer", "origin", "cookie" -> {
                        headers[normalized] = value
                    }
                    else -> {
                        headers[key] = value
                    }
                }
            }
        }
        return ParsedUrl(cleanUrl, headers, custom)
    }

    // ---------------------------------------------------------------------
    // Logo
    // ---------------------------------------------------------------------

    fun extractBestLogo(element: JSONObject, configuredField: String = ""): String {
        val candidates = mutableListOf<String>()
        if (configuredField.isNotBlank()) {
            candidates.add(configuredField)
        }
        candidates.addAll(
            listOf(
                "tvg-logo", "logo", "logo_url", "poster_url", "poster",
                "image", "icon", "thumb", "channel_logo", "tvgLogo", "logoUrl"
            )
        )

        var firstNonEmpty = ""
        for (field in candidates) {
            val valStr = element.str(field) ?: continue
            if (valStr.startsWith("http://", ignoreCase = true) ||
                valStr.startsWith("https://", ignoreCase = true) ||
                valStr.startsWith("//")
            ) {
                val url = if (valStr.startsWith("//")) "https:$valStr" else valStr
                return url.replace(" ", "%20")
            }
            if (firstNonEmpty.isEmpty()) {
                firstNonEmpty = valStr
            }
        }

        if (firstNonEmpty.isNotEmpty() && !firstNonEmpty.startsWith("http", ignoreCase = true)) {
            if (firstNonEmpty.endsWith(".png", ignoreCase = true) || firstNonEmpty.endsWith(".webp", ignoreCase = true) ||
                firstNonEmpty.endsWith(".jpg", ignoreCase = true) || firstNonEmpty.endsWith(".jpeg", ignoreCase = true)
            ) {
                return if (RE_LOGO_NUMERIC.matches(firstNonEmpty)) {
                    "https://tstatic.akash-go.com/cms-ui/images/custom-content/$firstNonEmpty".replace(" ", "%20")
                } else {
                    "https://cdnhost.akashbd.net/assets/uploads/channels_images/$firstNonEmpty?v=10.5.10".replace(" ", "%20")
                }
            }
        }

        return firstNonEmpty.replace(" ", "%20")
    }
}
