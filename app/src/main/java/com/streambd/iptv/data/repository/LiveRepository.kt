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
            // Fetch the M3U content directly from the URL
            val m3uContent = URL(categoryUrl).readText()
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
