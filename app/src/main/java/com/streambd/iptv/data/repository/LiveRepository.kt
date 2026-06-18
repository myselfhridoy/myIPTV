package com.streambd.iptv.data.repository

import com.streambd.iptv.data.api.RetrofitClient
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.LiveCategory
import com.streambd.iptv.utils.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class LiveRepository {

    suspend fun getLiveCategories(context: android.content.Context? = null): List<LiveCategory> = withContext(Dispatchers.IO) {
        try {
            val url = com.streambd.iptv.utils.RemoteConfigManager.getIptvPlaylistUrl()
            val response = RetrofitClient.apiService.getLiveCategories(url)
            if (response.isNotEmpty()) {
                val playlists = response[0].playlists
                return@withContext playlists.map { (name, info) ->
                    LiveCategory(
                        name = name,
                        url = info.url,
                        logo = info.logo
                    )
                }
            }
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            } catch (ce: Exception) {}
            emptyList()
        }
    }

    suspend fun getChannelsForCategory(categoryUrl: String): List<Channel> = withContext(Dispatchers.IO) {
        try {
            if (categoryUrl.startsWith("myiptv://proxy")) {
                val uri = android.net.Uri.parse(categoryUrl)
                val targetUrl = uri.getQueryParameter("url") ?: return@withContext emptyList()
                val arrayPath = uri.getQueryParameter("array_path") ?: ""
                val streamField = uri.getQueryParameter("stream_field") ?: "stream_name"
                val titleField = uri.getQueryParameter("title_field") ?: "title"
                val logoField = uri.getQueryParameter("logo_field") ?: "logo"
                val categoryField = uri.getQueryParameter("category_field") ?: "category"
                val prefix = uri.getQueryParameter("prefix") ?: ""
                val suffix = uri.getQueryParameter("suffix") ?: ""
                val headers = uri.getQueryParameter("headers") ?: ""
                
                val client = okhttp3.OkHttpClient.Builder()
                    .addInterceptor(com.streambd.iptv.data.api.DecryptionInterceptor())
                    .build()
                val requestBuilder = okhttp3.Request.Builder().url(targetUrl)
                
                if (headers.isNotEmpty()) {
                    headers.split("\n").forEach { line ->
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                        }
                    }
                }
                
                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) return@withContext emptyList()
                
                val body = response.body?.string() ?: return@withContext emptyList()
                
                var jsonArray: org.json.JSONArray? = null
                if (body.trim().startsWith("[")) {
                    jsonArray = org.json.JSONArray(body)
                } else if (body.trim().startsWith("{")) {
                    val obj = org.json.JSONObject(body)
                    if (arrayPath.isNotEmpty() && obj.has(arrayPath)) {
                        jsonArray = obj.getJSONArray(arrayPath)
                    }
                }
                
                if (jsonArray == null) return@withContext emptyList()
                
                val channelList = mutableListOf<Channel>()
                for (i in 0 until jsonArray.length()) {
                    try {
                        val item = jsonArray.getJSONObject(i)
                        val title = item.optString(titleField, "Unknown")
                        val logo = item.optString(logoField, "")
                        val category = item.optString(categoryField, "Uncategorized")
                        
                        var stream = item.optString(streamField, "")
                        if (stream.isEmpty()) continue
                        
                        if (streamField == "stream_name" && prefix.contains("channel=")) {
                             stream = java.net.URLEncoder.encode(stream, "UTF-8")
                        }
                        
                        val finalUrl = "$prefix$stream$suffix"
                        
                        channelList.add(Channel(
                            name = title,
                            url = finalUrl,
                            logo = logo,
                            group = category
                        ))
                    } catch (e: Exception) {}
                }
                
                return@withContext channelList
            }

            // Fetch the M3U content directly from the normal URL
            var m3uContent = URL(categoryUrl).readText()
            
            // Decrypt manually since we are not using Retrofit here
            if (!m3uContent.trim().startsWith("#EXTM3U")) {
                m3uContent = com.streambd.iptv.utils.CryptoUtils.decryptJson(m3uContent)
            }
            
            // Parse using the provided M3UParser
            M3UParser.parsePlaylist(m3uContent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            } catch (ce: Exception) {}
            emptyList()
        }
    }
}
