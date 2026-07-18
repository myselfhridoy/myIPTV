package com.streambd.iptv.utils

import android.net.Uri
import com.streambd.iptv.data.models.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OxooPortalHandler {

    private val client = UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Parses the portal URL with dual header strategy and fetches all channels grouped by category.
     * Example input URL: https://otapp.store/rest-api/v130?portal=oxoo&key=ottbangla@android&token=Basic YWRtaW46MTIzNA==
     */
    suspend fun fetchChannels(portalUrl: String): List<Channel> = withContext(Dispatchers.IO) {
        val channelsList = mutableListOf<Channel>()
        try {
            val uri = Uri.parse(portalUrl)
            val scheme = uri.scheme ?: "https"
            val authority = uri.authority ?: ""
            val path = uri.path?.removeSuffix("/") ?: ""
            val baseUrl = "$scheme://$authority$path"

            val headers = extractHeaders(uri)

            val targetUrl = if (baseUrl.endsWith("/all_tv_channel_by_category", ignoreCase = true)) {
                baseUrl
            } else {
                "$baseUrl/all_tv_channel_by_category"
            }

            val requestBuilder = Request.Builder().url(targetUrl)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            if (!body.trim().startsWith("[")) {
                return@withContext emptyList()
            }

            val categoriesArray = JSONArray(body)
            for (i in 0 until categoriesArray.length()) {
                val categoryObj = categoriesArray.optJSONObject(i) ?: continue
                val categoryTitle = categoryObj.optString("title", "Uncategorized").takeIf { it.isNotBlank() } ?: "Uncategorized"
                val channelsArray = categoryObj.optJSONArray("channels") ?: continue

                for (j in 0 until channelsArray.length()) {
                    val channelObj = channelsArray.optJSONObject(j) ?: continue
                    val liveTvId = channelObj.optInt("live_tv_id", -1).takeIf { it != -1 }
                        ?: channelObj.optString("live_tv_id").toIntOrNull()
                    val tvName = channelObj.optString("tv_name", channelObj.optString("title", "Unknown Channel")).takeIf { it.isNotBlank() } ?: "Unknown Channel"
                    val streamUrl = channelObj.optString("stream_url", channelObj.optString("url", "")).trim()
                    val streamLabel = channelObj.optString("stream_label", "Default").takeIf { it.isNotBlank() } ?: "Default"
                    val logoUrl = channelObj.optString("thumbnail_url", channelObj.optString("poster_url", "")).takeIf { it.isNotBlank() } ?: ""

                    if (streamUrl.isNotBlank()) {
                        val initialSources = mapOf(streamLabel to streamUrl)
                        val portalQuery = uri.query ?: ""
                        val singleDetailsUrl = if (portalQuery.isNotBlank()) "$baseUrl/single_details?type=tv&$portalQuery" else "$baseUrl/single_details?type=tv"
                        val safeUserAgent = headers["User-Agent"] ?: "okhttp/4.9.0"
                        val streamHeaders = mapOf("User-Agent" to safeUserAgent)
                        channelsList.add(
                            Channel(
                                name = tvName,
                                url = streamUrl,
                                logo = logoUrl,
                                group = categoryTitle,
                                tokenId = liveTvId,
                                tokenUrl = singleDetailsUrl, // Kept as indicator for lazy additional source fetching with portal credentials
                                userAgent = safeUserAgent,
                                httpHeaders = streamHeaders,
                                sources = initialSources
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext channelsList
    }

    /**
     * Lazy loader that fetches additional sources (LQ, SD, HD) for a channel when selected/played.
     */
    suspend fun fetchAdditionalSources(channel: Channel): Map<String, String> = withContext(Dispatchers.IO) {
        val currentSources = channel.sources.toMutableMap()
        val channelId = channel.tokenId ?: return@withContext currentSources
        val baseSingleDetailsUrl = channel.tokenUrl ?: return@withContext currentSources

        try {
            val portalHeaders = try { extractHeaders(Uri.parse(baseSingleDetailsUrl)) } catch (e: Exception) { emptyMap() }
            val cleanBase = baseSingleDetailsUrl.substringBefore("?")
            val targetUrl = "$cleanBase?type=tv&id=$channelId"

            val requestBuilder = Request.Builder().url(targetUrl)
            portalHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }
            val request = requestBuilder.build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext currentSources
            }

            val body = response.body?.string() ?: return@withContext currentSources
            if (!body.trim().startsWith("{")) {
                return@withContext currentSources
            }

            val rootObj = JSONObject(body)
            val additionalArray = rootObj.optJSONArray("additional_media_source")
            if (additionalArray != null && additionalArray.length() > 0) {
                for (i in 0 until additionalArray.length()) {
                    val srcObj = additionalArray.optJSONObject(i) ?: continue
                    val label = srcObj.optString("label", "Server ${i + 2}").takeIf { it.isNotBlank() } ?: "Server ${i + 2}"
                    val url = srcObj.optString("url", srcObj.optString("stream_url", "")).trim()
                    if (url.isNotBlank() && !currentSources.containsValue(url)) {
                        currentSources[label] = url
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext currentSources
    }

    private fun extractHeaders(uri: Uri): Map<String, String> {
        val headers = mutableMapOf<String, String>()

        // Shortcut convenience check
        val apiKey = uri.getQueryParameter("api-key") ?: uri.getQueryParameter("key") ?: uri.getQueryParameter("api_key")
        if (apiKey != null) headers["api-key"] = apiKey

        val auth = uri.getQueryParameter("authorization") ?: uri.getQueryParameter("token") ?: uri.getQueryParameter("auth")
        if (auth != null) headers["authorization"] = auth

        val ua = uri.getQueryParameter("user-agent") ?: uri.getQueryParameter("user_agent") ?: "okhttp/4.9.0"
        headers["User-Agent"] = ua

        // Real Header support: add any other query parameter that isn't reserved
        val reservedParams = setOf(
            "portal", "type", "key", "api_key", "api-key", "token", "auth",
            "authorization", "user-agent", "user_agent", "url"
        )
        uri.queryParameterNames.forEach { paramName ->
            if (!reservedParams.contains(paramName.lowercase())) {
                val valStr = uri.getQueryParameter(paramName)
                if (valStr != null) {
                    headers[paramName] = valStr
                }
            }
        }

        return headers
    }
}
