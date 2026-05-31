package com.myselfhridoy.streambdplayer.data.api

import com.myselfhridoy.streambdplayer.data.models.LiveCategoryResponse
import retrofit2.http.GET

interface ApiService {
    @GET("playlists/IPTV.json")
    suspend fun getLiveCategories(): List<LiveCategoryResponse>
}
