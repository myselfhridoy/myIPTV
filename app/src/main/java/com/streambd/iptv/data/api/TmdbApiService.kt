package com.streambd.iptv.data.api

import com.streambd.iptv.data.models.TmdbMedia
import com.streambd.iptv.data.models.TmdbResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApiService {
    
    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbResponse<TmdbMedia>

    @GET("trending/tv/week")
    suspend fun getTrendingTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbResponse<TmdbMedia>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbResponse<TmdbMedia>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @retrofit2.http.Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): com.streambd.iptv.data.models.TmdbMovieDetails

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @retrofit2.http.Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): com.streambd.iptv.data.models.TmdbTvShowDetails

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("with_original_language") withOriginalLanguage: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbResponse<TmdbMedia>

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("api_key") apiKey: String,
        @Query("with_original_language") withOriginalLanguage: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbResponse<TmdbMedia>
}
