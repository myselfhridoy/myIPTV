package com.myselfhridoy.streambdplayer.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myselfhridoy.streambdplayer.data.api.RetrofitClient
import com.myselfhridoy.streambdplayer.data.models.TmdbMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TmdbViewModel : ViewModel() {

    private val tmdbApiService = RetrofitClient.tmdbApiService
    private val apiKey = "460327acf6e0235a391222cb530de9c8"

    private val _trendingMovies = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val trendingMovies: StateFlow<List<TmdbMedia>> = _trendingMovies.asStateFlow()

    private val _trendingTv = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val trendingTv: StateFlow<List<TmdbMedia>> = _trendingTv.asStateFlow()
    
    // Regional Content
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

    private val _selectedTvDetails = MutableStateFlow<com.myselfhridoy.streambdplayer.data.models.TmdbTvShowDetails?>(null)
    val selectedTvDetails: StateFlow<com.myselfhridoy.streambdplayer.data.models.TmdbTvShowDetails?> = _selectedTvDetails.asStateFlow()

    init {
        fetchTrending()
    }

    private fun fetchTrending() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Trending
                val moviesResponse = tmdbApiService.getTrendingMovies(apiKey)
                _trendingMovies.value = moviesResponse.results.filter { it.poster_path != null }

                val tvResponse = tmdbApiService.getTrendingTv(apiKey)
                _trendingTv.value = tvResponse.results.filter { it.poster_path != null }
                
                // Regional Movies
                val hindiMRes = tmdbApiService.discoverMovies(apiKey, "hi")
                _hindiMovies.value = hindiMRes.results.filter { it.poster_path != null }.map { it.copy(media_type = "movie") }
                
                val southMRes = tmdbApiService.discoverMovies(apiKey, "te|ta|ml|kn")
                _southMovies.value = southMRes.results.filter { it.poster_path != null }.map { it.copy(media_type = "movie") }
                
                val banglaMRes = tmdbApiService.discoverMovies(apiKey, "bn")
                _banglaMovies.value = banglaMRes.results.filter { it.poster_path != null }.map { it.copy(media_type = "movie") }
                
                // Regional TV
                val hindiTvRes = tmdbApiService.discoverTv(apiKey, "hi")
                _hindiTv.value = hindiTvRes.results.filter { it.poster_path != null }.map { it.copy(media_type = "tv") }
                
                val southTvRes = tmdbApiService.discoverTv(apiKey, "te|ta|ml|kn")
                _southTv.value = southTvRes.results.filter { it.poster_path != null }.map { it.copy(media_type = "tv") }
                
                val banglaTvRes = tmdbApiService.discoverTv(apiKey, "bn")
                _banglaTv.value = banglaTvRes.results.filter { it.poster_path != null }.map { it.copy(media_type = "tv") }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val searchResponse = tmdbApiService.searchMulti(apiKey, query)
                // Filter out persons and items without posters
                _searchResults.value = searchResponse.results.filter { 
                    it.media_type != "person" && it.poster_path != null 
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectMedia(media: TmdbMedia) {
        _selectedMedia.value = media
        _selectedTvDetails.value = null // reset when new media selected
    }

    fun clearSelectedMedia() {
        _selectedMedia.value = null
        _selectedTvDetails.value = null
    }

    fun fetchTvDetails(tvId: Int) {
        viewModelScope.launch {
            try {
                val response = tmdbApiService.getTvDetails(tvId, apiKey)
                _selectedTvDetails.value = response
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
