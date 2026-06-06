package com.streambd.iptv.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbResponse<T>(
    val page: Int,
    val results: List<T>,
    val total_pages: Int,
    val total_results: Int
)

@Serializable
data class TmdbMedia(
    val id: Int,
    val title: String? = null,
    val name: String? = null, // For TV Shows
    val original_title: String? = null,
    val original_name: String? = null,
    val overview: String? = null,
    val adult: Boolean? = false,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val media_type: String? = null,
    val genre_ids: List<Int> = emptyList(),
    val popularity: Double? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null
) {
    val displayTitle: String
        get() = title ?: name ?: original_title ?: original_name ?: "Unknown"

    val displayDate: String
        get() = release_date ?: first_air_date ?: ""

    val posterUrl: String
        get() = if (poster_path != null) "https://image.tmdb.org/t/p/w500$poster_path" else ""

    val backdropUrl: String
        get() = if (backdrop_path != null) "https://image.tmdb.org/t/p/w1280$backdrop_path" else ""

    val isMovie: Boolean
        get() = media_type == "movie" || title != null
}

@Serializable
data class TmdbGenre(
    val id: Int,
    val name: String
)

@Serializable
data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    val runtime: Int? = null,
    val tagline: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val status: String? = null
)

@Serializable
data class TmdbTvShowDetails(
    val id: Int,
    val name: String,
    val number_of_episodes: Int? = null,
    val number_of_seasons: Int? = null,
    val seasons: List<TmdbSeason> = emptyList(),
    val tagline: String? = null,
    val genres: List<TmdbGenre> = emptyList(),
    val status: String? = null
)

@Serializable
data class TmdbSeason(
    val id: Int,
    val name: String? = null,
    val season_number: Int,
    val episode_count: Int
)

