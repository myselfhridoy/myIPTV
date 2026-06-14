package com.streambd.iptv.data.api

import com.streambd.iptv.data.models.LiveCategoryResponse
import retrofit2.http.GET

interface ApiService {
    @GET
    suspend fun getLiveCategories(@retrofit2.http.Url url: String): List<LiveCategoryResponse>

    @GET
    suspend fun getLiveEvents(@retrofit2.http.Url url: String): com.streambd.iptv.data.models.SportzfyApiResponse

    @GET
    suspend fun getLiveEventStreams(@retrofit2.http.Url url: String): com.streambd.iptv.data.models.SportzfyStreamResponse
}
