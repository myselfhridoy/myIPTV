package com.myselfhridoy.streambdplayer.data.repository

import com.myselfhridoy.streambdplayer.data.api.RetrofitClient
import com.myselfhridoy.streambdplayer.data.models.Channel
import com.myselfhridoy.streambdplayer.data.models.LiveCategory
import com.myselfhridoy.streambdplayer.utils.M3UParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class LiveRepository {

    suspend fun getLiveCategories(): List<LiveCategory> = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.apiService.getLiveCategories()
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
            emptyList()
        }
    }
}
