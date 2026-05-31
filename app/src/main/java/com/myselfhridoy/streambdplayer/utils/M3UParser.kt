package com.myselfhridoy.streambdplayer.utils

import com.myselfhridoy.streambdplayer.data.models.Channel
import com.myselfhridoy.streambdplayer.data.models.DrmConfig
import org.json.JSONObject
import org.json.JSONArray
import java.net.URLDecoder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object M3UParser {

    suspend fun preprocessM3U(content: String): String = withContext(Dispatchers.IO) {
        content
    }

    fun parsePlaylist(content: String): List<Channel> {
        val trimmed = content.trim()
        
        if (trimmed.contains("#EXTM3U") || trimmed.contains("#EXTINF:") || Regex("#\\s*EXTATTRFROMURL:", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) {
            return parseM3U(content)
        }
        
        val lower = trimmed.lowercase()
        if (lower.contains("[playlist]") || lower.contains("numberofentries=")) {
            return parsePLS(content)
        }
        
        if (trimmed.contains("<playlist") && trimmed.contains("<trackList>")) {
            return parseXSPF(content)
        }
        
        try {
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                return parseJSONArray(array)
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                if (obj.has("channels")) {
                    val channels = obj.getJSONArray("channels")
                    return parseJSONArray(channels)
                }
            }
        } catch (e: Exception) {}
        
        return try {
            val m3u = parseM3U(content)
            if (m3u.isNotEmpty()) m3u else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun parseM3U(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.split("\n").map { it.trim() }
        
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "Uncategorized"
        var currentUserAgent: String? = null
        var currentCookie: String? = null
        var currentHttpReferer: String? = null
        var currentOrigin: String? = null
        var currentTokenUrl: String? = null
        var currentTokenMatch: String? = null
        var currentTokenReplace: String? = null
        var currentTokenId: Int? = null
        var currentDrm: DrmConfig? = null

        fun resetCurrentChannel() {
            currentName = ""
            currentLogo = ""
            currentGroup = "Uncategorized"
            currentUserAgent = null
            currentCookie = null
            currentHttpReferer = null
            currentOrigin = null
            currentTokenUrl = null
            currentTokenMatch = null
            currentTokenReplace = null
            currentTokenId = null
            currentDrm = null
        }

        for (line in lines) {
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:")) {
                val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(line)
                val groupMatch = Regex("""group-title="([^"]+)"""").find(line)
                
                currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                currentGroup = groupMatch?.groupValues?.get(1) ?: "Uncategorized"

                val uaMatch = Regex("""http-user-agent="([^"]+)"""", RegexOption.IGNORE_CASE).find(line)
                if (uaMatch != null) currentUserAgent = uaMatch.groupValues[1]

                val refMatch = Regex("""http-referrer="([^"]+)"""", RegexOption.IGNORE_CASE).find(line)
                if (refMatch != null) currentHttpReferer = refMatch.groupValues[1]
                
                val tokenIdMatch = Regex("""(?:token-id|tokenid)\s*=\s*"?([^",\s]+)"?""", RegexOption.IGNORE_CASE).find(line)
                if (tokenIdMatch != null) currentTokenId = tokenIdMatch.groupValues[1].toIntOrNull()
                
                var lastCommaOutsideQuotes = -1
                var inQuotes = false
                for (i in line.indices) {
                    val ch = line[i]
                    if (ch == '"') {
                        inQuotes = !inQuotes
                    } else if (ch == ',' && !inQuotes) {
                        lastCommaOutsideQuotes = i
                    }
                }
                if (lastCommaOutsideQuotes != -1) {
                    currentName = line.substring(lastCommaOutsideQuotes + 1).trim()
                } else {
                    currentName = "Unknown Channel"
                }
            } else if (line.startsWith("#EXTVLCOPT:")) {
                val opt = line.substring(11).trim()
                val lowerOpt = opt.lowercase()
                when {
                    lowerOpt.startsWith("http-user-agent=") -> currentUserAgent = opt.substring(16).trim()
                    lowerOpt.startsWith("http-referrer=") || lowerOpt.startsWith("http-referer=") -> {
                        currentHttpReferer = opt.substring(opt.indexOf('=') + 1).trim()
                    }
                    lowerOpt.startsWith("http-origin=") -> {
                        currentOrigin = opt.substring(opt.indexOf('=') + 1).trim()
                    }
                }
            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
                val type = line.substringAfter("=").trim().lowercase()
                val parsedType = when {
                    type.contains("widevine") -> "widevine"
                    type.contains("playready") -> "playready"
                    type.contains("clearkey") -> "clearkey"
                    else -> null
                }
                currentDrm = currentDrm?.copy(type = parsedType) ?: DrmConfig(type = parsedType)
            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key=")) {
                val key = line.substringAfter("=").trim()
                val isRawClearKeyPair = Regex("^[0-9a-fA-F]{32}:[0-9a-fA-F]{32}$").matches(key)
                currentDrm = if (isRawClearKeyPair) {
                    currentDrm?.copy(type = "clearkey", rawKeyPair = key) ?: DrmConfig(type = "clearkey", rawKeyPair = key)
                } else {
                    currentDrm?.copy(licenseServer = key) ?: DrmConfig(licenseServer = key)
                }
            } else if (line.startsWith("#EXTHTTP:")) {
                try {
                    val payload = line.removePrefix("#EXTHTTP:").trim()
                    if (payload.startsWith("{")) {
                        val jsonObj = JSONObject(payload)
                        if (jsonObj.has("headers")) {
                            val headersObj = jsonObj.getJSONObject("headers")
                            headersObj.keys().forEach { k ->
                                val v = headersObj.getString(k)
                                when (k.lowercase()) {
                                    "cookie" -> currentCookie = v
                                    "user-agent" -> currentUserAgent = v
                                    "referer", "referrer" -> currentHttpReferer = v
                                    "origin" -> currentOrigin = v
                                }
                            }
                        }
                        jsonObj.keys().forEach { k ->
                            if (k.lowercase() != "headers") {
                                val v = jsonObj.getString(k)
                                when (k.lowercase()) {
                                    "cookie" -> currentCookie = v
                                    "user-agent" -> currentUserAgent = v
                                    "referer", "referrer" -> currentHttpReferer = v
                                    "origin" -> currentOrigin = v
                                }
                            }
                        }
                    } else if (payload.isNotEmpty()) {
                        currentCookie = payload
                    }
                } catch (e: Exception) {
                    // Ignore parse error
                }
            } else if (line.startsWith("#EXTATTRFROMURL:")) {
                val attrUrl = line.removePrefix("#EXTATTRFROMURL:").trim()
                if (attrUrl.contains("|")) {
                    val parsed = parseUrlHeaders(attrUrl)
                    currentHttpReferer = parsed.headers["referer"] ?: currentHttpReferer
                    currentUserAgent = parsed.headers["user-agent"] ?: currentUserAgent
                    currentOrigin = parsed.headers["origin"] ?: currentOrigin
                    currentCookie = parsed.headers["cookie"] ?: currentCookie
                    
                    currentTokenUrl = parsed.custom["tokenurl"] ?: currentTokenUrl
                    currentTokenMatch = parsed.custom["tokenmatch"] ?: currentTokenMatch
                    currentTokenReplace = parsed.custom["tokenreplace"] ?: currentTokenReplace
                    currentTokenId = parsed.custom["tokenid"]?.toIntOrNull() ?: currentTokenId
                }
            } else if (!line.startsWith("#")) {
                val cleanUrl: String
                if (line.contains("|")) {
                    val parsed = parseUrlHeaders(line)
                    cleanUrl = parsed.cleanUrl
                    
                    currentHttpReferer = parsed.headers["referer"] ?: currentHttpReferer
                    currentUserAgent = parsed.headers["user-agent"] ?: currentUserAgent
                    currentOrigin = parsed.headers["origin"] ?: currentOrigin
                    currentCookie = parsed.headers["cookie"] ?: currentCookie
                    
                    currentTokenUrl = parsed.custom["tokenurl"] ?: currentTokenUrl
                    currentTokenMatch = parsed.custom["tokenmatch"] ?: currentTokenMatch
                    currentTokenReplace = parsed.custom["tokenreplace"] ?: currentTokenReplace
                    currentTokenId = parsed.custom["tokenid"]?.toIntOrNull() ?: currentTokenId
                } else {
                    cleanUrl = line.trim()
                }

                if (cleanUrl.isNotEmpty()) {
                    val isLive = cleanUrl.contains(".m3u8", ignoreCase = true) || 
                                 cleanUrl.contains(".ts", ignoreCase = true) || 
                                 cleanUrl.contains("live", ignoreCase = true)
                    channels.add(
                        Channel(
                            name = currentName,
                            logo = currentLogo,
                            group = currentGroup,
                            url = cleanUrl,
                            userAgent = currentUserAgent,
                            cookie = currentCookie,
                            httpReferer = currentHttpReferer,
                            origin = currentOrigin,
                            tokenUrl = currentTokenUrl,
                            tokenMatch = currentTokenMatch,
                            tokenReplace = currentTokenReplace,
                            tokenId = currentTokenId,
                            drm = currentDrm,
                            isLiveEvent = isLive
                        )
                    )
                }
                resetCurrentChannel()
            }
        }
        return channels
    }

    private fun parsePLS(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.split("\n").map { it.trim() }
        var currentTitle = ""
        var currentGroup = "Uncategorized"

        for (line in lines) {
            val lowerLine = line.lowercase()
            if (lowerLine.startsWith("file")) {
                val urlMatch = Regex("""file\d*=(.+)""", RegexOption.IGNORE_CASE).find(line)
                if (urlMatch != null) {
                    val url = urlMatch.groupValues[1].trim()
                    val parsed = parseUrlHeaders(url)
                    
                    channels.add(Channel(
                        name = currentTitle.ifEmpty { "Unknown Channel" },
                        url = parsed.cleanUrl,
                        group = currentGroup,
                        userAgent = parsed.headers["user-agent"],
                        httpReferer = parsed.headers["referer"],
                        origin = parsed.headers["origin"],
                        cookie = parsed.headers["cookie"],
                        tokenUrl = parsed.custom["tokenurl"],
                        tokenId = parsed.custom["tokenid"]?.toIntOrNull(),
                        isLiveEvent = true
                    ))
                    currentTitle = ""
                }
            } else if (lowerLine.startsWith("title")) {
                val titleMatch = Regex("""title\d*=(.+)""", RegexOption.IGNORE_CASE).find(line)
                if (titleMatch != null) {
                    currentTitle = titleMatch.groupValues[1].trim()
                    val groupMatch = Regex("""^(.+?)\s*[-–—]\s*(.+)$""").find(currentTitle)
                    if (groupMatch != null) {
                        currentGroup = groupMatch.groupValues[1].trim()
                        currentTitle = groupMatch.groupValues[2].trim()
                    }
                }
            } else if (lowerLine.startsWith("group")) {
                val groupMatch = Regex("""group\d*=(.+)""", RegexOption.IGNORE_CASE).find(line)
                if (groupMatch != null) {
                    currentGroup = groupMatch.groupValues[1].trim()
                }
            }
        }
        return channels
    }
    
    private fun parseXSPF(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val trackRegex = Regex("""<track>([\s\S]*?)</track>""", RegexOption.IGNORE_CASE)
        val matches = trackRegex.findAll(content)
        
        for (match in matches) {
            val trackContent = match.groupValues[1]
            val title = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE).find(trackContent)?.groupValues?.get(1)?.trim() ?: "Unknown Channel"
            val location = Regex("""<location>([^<]*)</location>""", RegexOption.IGNORE_CASE).find(trackContent)?.groupValues?.get(1)?.trim()
            val image = Regex("""<image>([^<]*)</image>""", RegexOption.IGNORE_CASE).find(trackContent)?.groupValues?.get(1)?.trim()
            val creator = Regex("""<creator>([^<]*)</creator>""", RegexOption.IGNORE_CASE).find(trackContent)?.groupValues?.get(1)?.trim()
            val annotation = Regex("""<annotation>([^<]*)</annotation>""", RegexOption.IGNORE_CASE).find(trackContent)?.groupValues?.get(1)?.trim()
            
            if (location != null) {
                val parsed = parseUrlHeaders(location)
                var group = creator ?: "Uncategorized"
                if (creator == null && annotation != null) {
                    group = annotation
                }
                
                channels.add(Channel(
                    name = title,
                    url = parsed.cleanUrl,
                    logo = image ?: "",
                    group = group,
                    userAgent = parsed.headers["user-agent"],
                    httpReferer = parsed.headers["referer"],
                    origin = parsed.headers["origin"],
                    cookie = parsed.headers["cookie"],
                    isLiveEvent = true
                ))
            }
        }
        return channels
    }
    
    private fun parseJSONArray(array: JSONArray): List<Channel> {
        val channels = mutableListOf<Channel>()
        
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val rawUrl = obj.optString("url", obj.optString("link", ""))
                if (rawUrl.isEmpty()) continue
                
                val parsed = parseUrlHeaders(rawUrl)
                var ua = parsed.headers["user-agent"]
                var ref = parsed.headers["referer"]
                var origin = parsed.headers["origin"]
                var cookie = parsed.headers["cookie"]
                
                if (obj.has("cookie")) cookie = obj.getString("cookie")
                if (obj.has("referer")) ref = obj.getString("referer")
                else if (obj.has("referrer")) ref = obj.getString("referrer")
                if (obj.has("origin")) origin = obj.getString("origin")
                
                if (obj.has("userAgent")) ua = obj.getString("userAgent")
                else if (obj.has("user-agent")) ua = obj.getString("user-agent")
                else if (obj.has("user_agent")) ua = obj.getString("user_agent")
                
                if (obj.has("headers")) {
                    val hObj = obj.getJSONObject("headers")
                    hObj.keys().forEach { k ->
                        val v = hObj.getString(k)
                        when (k.lowercase()) {
                            "cookie" -> cookie = v
                            "user-agent" -> ua = v
                            "referer", "referrer" -> ref = v
                            "origin" -> origin = v
                        }
                    }
                }
                
                val name = obj.optString("name", obj.optString("title", "Unknown Channel"))
                val logo = obj.optString("logo", obj.optString("image", obj.optString("tvgLogo", "")))
                val group = obj.optString("group", obj.optString("category", obj.optString("groupTitle", "Uncategorized")))
                val isLive = obj.optBoolean("isLive", true)
                
                channels.add(Channel(
                    name = name,
                    url = parsed.cleanUrl,
                    logo = logo,
                    group = group,
                    userAgent = ua,
                    httpReferer = ref,
                    origin = origin,
                    cookie = cookie,
                    isLiveEvent = isLive
                ))
            } catch (e: Exception) {}
        }
        return channels
    }

    private data class ParsedUrl(
        val cleanUrl: String, 
        val headers: Map<String, String>, 
        val custom: Map<String, String>
    )

    private fun parseUrlHeaders(line: String): ParsedUrl {
        val parts = line.split("|")
        val cleanUrl = parts.firstOrNull()?.trim() ?: ""
        val headers = mutableMapOf<String, String>()
        val custom = mutableMapOf<String, String>()

        for (i in 1 until parts.size) {
            val headerPart = parts[i].trim()
            val colonIndex = headerPart.indexOf(':')
            val equalsIndex = headerPart.indexOf('=')
            
            var separatorIndex = -1
            if (colonIndex != -1 && equalsIndex != -1) {
                separatorIndex = minOf(colonIndex, equalsIndex)
            } else if (colonIndex != -1) {
                separatorIndex = colonIndex
            } else if (equalsIndex != -1) {
                separatorIndex = equalsIndex
            }
            
            if (separatorIndex > -1) {
                val key = headerPart.substring(0, separatorIndex).trim()
                var value = headerPart.substring(separatorIndex + 1).trim()
                
                try {
                    val decoded = URLDecoder.decode(value.replace("+", " "), "UTF-8")
                    value = decoded
                } catch (e: Exception) {}
                
                val lower = key.lowercase()

                if (lower == "tokenurl" || lower == "token-url" || lower == "tokenid" || lower == "tokenmatch" || lower == "tokenreplace") {
                    custom[key] = value
                    custom[lower] = value
                } else {
                    headers[key] = value
                    if (lower == "http-referer" || lower == "http-referrer" || lower == "referrer") {
                        headers["referer"] = value
                    } else if (lower == "http-user-agent") {
                        headers["user-agent"] = value
                    } else if (lower == "http-origin") {
                        headers["origin"] = value
                    } else {
                        headers[lower] = value
                    }
                }
            }
        }
        return ParsedUrl(cleanUrl, headers, custom)
    }
}
