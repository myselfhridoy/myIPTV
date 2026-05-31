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

    private val _searchResults = MutableStateFlow<List<TmdbMedia>>(emptyList())
    val searchResults: StateFlow<List<TmdbMedia>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedMedia = MutableStateFlow<TmdbMedia?>(null)
    val selectedMedia: StateFlow<TmdbMedia?> = _selectedMedia.asStateFlow()

    init {
        fetchTrending()
    }

    private fun fetchTrending() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val moviesResponse = tmdbApiService.getTrendingMovies(apiKey)
                _trendingMovies.value = moviesResponse.results.filter { it.poster_path != null }

                val tvResponse = tmdbApiService.getTrendingTv(apiKey)
                _trendingTv.value = tvResponse.results.filter { it.poster_path != null }
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
    }
}
