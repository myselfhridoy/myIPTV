package com.streambd.iptv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.api.RetrofitClient
import com.streambd.iptv.data.models.TmdbMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TmdbViewModel : ViewModel() {

    private val tmdbApiService = RetrofitClient.tmdbApiService
    private val apiKey get() = com.streambd.iptv.utils.RemoteConfigManager.getTmdbApiKey()

    // ── State flows ──
    private val _trendingMovies = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val trendingMovies: StateFlow<List<TmdbMedia>> = _trendingMovies.asStateFlow()

    private val _trendingTv = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val trendingTv: StateFlow<List<TmdbMedia>> = _trendingTv.asStateFlow()

    private val _hindiMovies = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val hindiMovies: StateFlow<List<TmdbMedia>> = _hindiMovies.asStateFlow()

    private val _hindiTv = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val hindiTv: StateFlow<List<TmdbMedia>> = _hindiTv.asStateFlow()

    private val _southMovies = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val southMovies: StateFlow<List<TmdbMedia>> = _southMovies.asStateFlow()

    private val _southTv = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val southTv: StateFlow<List<TmdbMedia>> = _southTv.asStateFlow()

    private val _banglaMovies = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val banglaMovies: StateFlow<List<TmdbMedia>> = _banglaMovies.asStateFlow()

    private val _banglaTv = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val banglaTv: StateFlow<List<TmdbMedia>> = _banglaTv.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val searchResults: StateFlow<List<TmdbMedia>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedMedia = MutableStateFlow<TmdbMedia?>(null)
    val selectedMedia: StateFlow<TmdbMedia?> = _selectedMedia.asStateFlow()

    private val _selectedTvDetails =
        MutableStateFlow<com.streambd.iptv.data.models.TmdbTvShowDetails?>(null)
    val selectedTvDetails: StateFlow<com.streambd.iptv.data.models.TmdbTvShowDetails?> =
        _selectedTvDetails.asStateFlow()

    private val _selectedMovieDetails =
        MutableStateFlow<com.streambd.iptv.data.models.TmdbMovieDetails?>(null)
    val selectedMovieDetails: StateFlow<com.streambd.iptv.data.models.TmdbMovieDetails?> =
        _selectedMovieDetails.asStateFlow()

    private var searchJob: Job? = null

    init {
        fetchTrending()
    }

    /**
     * Fetch all home rows IN PARALLEL.
     * Was: 8 sequential API calls (~3-5s cold start).
     * Now: all 8 fire together, first paint within ~500-800ms.
     */
    private fun fetchTrending() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                coroutineScope {
                    val key = apiKey

                    val trendingMoviesD = async { tmdbApiService.getTrendingMovies(key) }
                    val trendingTvD = async { tmdbApiService.getTrendingTv(key) }
                    val hindiMoviesD = async { tmdbApiService.discoverMovies(key, "hi") }
                    val southMoviesD = async { tmdbApiService.discoverMovies(key, "te|ta|ml|kn") }
                    val banglaMoviesD = async { tmdbApiService.discoverMovies(key, "bn") }
                    val hindiTvD = async { tmdbApiService.discoverTv(key, "hi") }
                    val southTvD = async { tmdbApiService.discoverTv(key, "te|ta|ml|kn") }
                    val banglaTvD = async { tmdbApiService.discoverTv(key, "bn") }

                    // Await all in parallel.
                    awaitAll(
                        trendingMoviesD, trendingTvD,
                        hindiMoviesD, southMoviesD, banglaMoviesD,
                        hindiTvD, southTvD, banglaTvD
                    )

                    // Apply results individually so a single failure doesn't blank everything.
                    runCatching {
                        _trendingMovies.value = trendingMoviesD.await().results
                            .filter { it.poster_path != null && it.adult != true }
                    }
                    runCatching {
                        _trendingTv.value = trendingTvD.await().results
                            .filter { it.poster_path != null && it.adult != true }
                    }
                    runCatching {
                        _hindiMovies.value = hindiMoviesD.await().results
                            .filter { it.poster_path != null && it.adult != true }
                            .map { it.copy(media_type = "movie") }
                    }
                    runCatching {
                        _southMovies.value = southMoviesD.await().results
                            .filter { it.poster_path != null && it.adult != true }
                            .map { it.copy(media_type = "movie") }
                    }
                    runCatching {
                        _banglaMovies.value = banglaMoviesD.await().results
                            .filter { it.poster_path != null && it.adult != true }
                            .map { it.copy(media_type = "movie") }
                    }
                    runCatching {
                        _hindiTv.value = hindiTvD.await().results
                            .filter { it.poster_path != null && it.adult != true }
                            .map { it.copy(media_type = "tv") }
                    }
                    runCatching {
                        _southTv.value = southTvD.await().results
                            .filter { it.poster_path != null && it.adult != true }
                            .map { it.copy(media_type = "tv") }
                    }
                    runCatching {
                        _banglaTv.value = banglaTvD.await().results
                            .filter { it.poster_path != null && it.adult != true }
                            .map { it.copy(media_type = "tv") }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Search TMDB with built-in debounce.
     * Was: fired on EVERY keystroke → 8-10 API calls while typing one word.
     * Now: 300ms after the user stops typing, one request fires.
     */
    fun search(query: String) {
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _isLoading.value = true
            try {
                val searchResponse = tmdbApiService.searchMulti(apiKey, query)
                _searchResults.value = searchResponse.results.filter {
                    it.media_type != "person" && it.poster_path != null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectMedia(media: TmdbMedia) {
        _selectedMedia.value = media
        _selectedTvDetails.value = null
        _selectedMovieDetails.value = null
    }

    fun clearSelectedMedia() {
        _selectedMedia.value = null
        _selectedTvDetails.value = null
        _selectedMovieDetails.value = null
    }

    fun fetchTvDetails(tvId: Int) {
        viewModelScope.launch {
            try {
                val response = tmdbApiService.getTvDetails(tvId, apiKey)
                _selectedTvDetails.value = response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchMovieDetails(movieId: Int) {
        viewModelScope.launch {
            try {
                val response = tmdbApiService.getMovieDetails(movieId, apiKey)
                _selectedMovieDetails.value = response
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
