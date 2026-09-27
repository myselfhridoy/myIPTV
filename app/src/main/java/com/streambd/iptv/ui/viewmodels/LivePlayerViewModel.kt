package com.streambd.iptv.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.source.MediaSource
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.utils.MediaResolver
import com.streambd.iptv.utils.ResolvedMediaParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LivePlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val appContext = application.applicationContext

    private val _currentMediaSource = MutableStateFlow<MediaSource?>(null)
    val currentMediaSource: StateFlow<MediaSource?> = _currentMediaSource.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val prefetchCache = ConcurrentHashMap<String, ResolvedMediaParams>()
    
    private var resolveJob: Job? = null
    private var prefetchJob: Job? = null

    fun resolveChannel(channel: Channel, sourceUrl: String) {
        resolveJob?.cancel()
        _isLoading.value = true
        _currentMediaSource.value = null

        val cacheKey = "${channel.name}|$sourceUrl"
        val cachedParams = prefetchCache[cacheKey]
        if (cachedParams != null) {
            _currentMediaSource.value = MediaResolver.buildMediaSource(appContext, cachedParams)
            _isLoading.value = false
            return
        }

        resolveJob = viewModelScope.launch(Dispatchers.IO) {
            val params = MediaResolver.resolveMediaParams(appContext, channel, sourceUrl)
            if (params != null) {
                prefetchCache[cacheKey] = params
                val source = kotlinx.coroutines.withContext(Dispatchers.Main) {
                    MediaResolver.buildMediaSource(appContext, params)
                }
                _currentMediaSource.value = source
            }
            _isLoading.value = false
        }
    }

    fun prefetchChannels(playlist: List<Channel>, currentIndex: Int) {
        if (playlist.size <= 1 || currentIndex == -1) return
        
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            // Determine next and previous channels to pre-resolve
            val nextIndex = if (currentIndex < playlist.size - 1) currentIndex + 1 else 0
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
            
            val nextChannel = playlist[nextIndex]
            val prevChannel = playlist[prevIndex]
            
            val currentKey = "${playlist[currentIndex].name}|${playlist[currentIndex].url}"
            val nextCacheKey = "${nextChannel.name}|${nextChannel.url}"
            val prevCacheKey = "${prevChannel.name}|${prevChannel.url}"
            
            // Clean up old cache to prevent memory leaks (keep max 10)
            if (prefetchCache.size > 10) {
                val keysToKeep = setOf(currentKey, nextCacheKey, prevCacheKey)
                prefetchCache.keys.retainAll(keysToKeep)
            }
            
            kotlinx.coroutines.coroutineScope {
                val nextDeferred = async {
                    if (!prefetchCache.containsKey(nextCacheKey)) {
                        MediaResolver.resolveMediaParams(appContext, nextChannel, nextChannel.url)
                    } else null
                }
                
                val prevDeferred = async {
                    if (!prefetchCache.containsKey(prevCacheKey)) {
                        MediaResolver.resolveMediaParams(appContext, prevChannel, prevChannel.url)
                    } else null
                }
                
                nextDeferred.await()?.let { prefetchCache[nextCacheKey] = it }
                prevDeferred.await()?.let { prefetchCache[prevCacheKey] = it }
            }
        }
    }

    fun retryResolution(channel: Channel, sourceUrl: String) {
        val cacheKey = "${channel.name}|$sourceUrl"
        prefetchCache.remove(cacheKey)
        resolveChannel(channel, sourceUrl)
    }
}
