package com.streambd.iptv.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.source.MediaSource
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.utils.MediaResolver
import com.streambd.iptv.utils.ResolvedMediaParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LivePlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _currentMediaSource = MutableStateFlow<MediaSource?>(null)
    val currentMediaSource: StateFlow<MediaSource?> = _currentMediaSource.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private data class CacheEntry(val params: ResolvedMediaParams, val timestamp: Long)

    private val prefetchCache = ConcurrentHashMap<String, CacheEntry>()

    private var resolveJob: Job? = null
    private var prefetchJob: Job? = null

    private companion object {
        const val TAG = "LivePlayerViewModel"
        const val MAX_CACHE_SIZE = 10
        // Resolved stream URLs/tokens can expire, so don't reuse them forever
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        // Don't prefetch while the user is quickly channel-surfing
        const val PREFETCH_DELAY_MS = 1000L
        // Re-resolve neighbours before CACHE_TTL_MS expires (must stay below the TTL)
        const val REFRESH_INTERVAL_MS = 4 * 60 * 1000L
    }

    private fun getCached(key: String): ResolvedMediaParams? {
        val entry = prefetchCache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS) {
            prefetchCache.remove(key)
            return null
        }
        return entry.params
    }

    private fun putCached(key: String, params: ResolvedMediaParams) {
        prefetchCache[key] = CacheEntry(params, System.currentTimeMillis())
    }

    fun resolveChannel(channel: Channel, sourceUrl: String) {
        resolveJob?.cancel()
        _isLoading.value = true
        _currentMediaSource.value = null

        val cacheKey = "${channel.name}|$sourceUrl"
        val cachedParams = getCached(cacheKey)
        if (cachedParams != null) {
            _currentMediaSource.value = MediaResolver.buildMediaSource(appContext, cachedParams)
            _isLoading.value = false
            return
        }

        resolveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val params = MediaResolver.resolveMediaParams(appContext, channel, sourceUrl)
                // If a newer resolve started meanwhile, drop this stale result
                ensureActive()
                if (params != null) {
                    putCached(cacheKey, params)
                    val source = withContext(Dispatchers.Main) {
                        MediaResolver.buildMediaSource(appContext, params)
                    }
                    ensureActive()
                    _currentMediaSource.value = source
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve ${channel.name}", e)
            } finally {
                // Only the latest (still active) job may turn the loading flag off,
                // otherwise a cancelled job hides the spinner of the new one.
                if (isActive) _isLoading.value = false
            }
        }
    }

    fun prefetchChannels(playlist: List<Channel>, currentIndex: Int) {
        if (playlist.size <= 1 || currentIndex == -1) return

        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(PREFETCH_DELAY_MS)

            // Determine next and previous channels to pre-resolve
            val nextIndex = if (currentIndex < playlist.size - 1) currentIndex + 1 else 0
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1

            val nextChannel = playlist[nextIndex]
            val prevChannel = playlist[prevIndex]

            val currentKey = "${playlist[currentIndex].name}|${playlist[currentIndex].url}"
            val nextCacheKey = "${nextChannel.name}|${nextChannel.url}"
            val prevCacheKey = "${prevChannel.name}|${prevChannel.url}"

            // Clean up old cache to prevent memory leaks
            if (prefetchCache.size > MAX_CACHE_SIZE) {
                val keysToKeep = setOf(currentKey, nextCacheKey, prevCacheKey)
                prefetchCache.keys.retainAll(keysToKeep)
            }

            prefetchNeighbors(nextChannel, nextCacheKey, prevChannel, prevCacheKey, force = false)

            // Refresh neighbours before the cache TTL runs out, so Up/Down still hits the
            // cache after a long time on one channel. Stops when the job is cancelled.
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                prefetchNeighbors(nextChannel, nextCacheKey, prevChannel, prevCacheKey, force = true)
            }
        }
    }

    /** Stop background prefetching (call when leaving the player screen). */
    fun stopPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    private suspend fun prefetchNeighbors(
        nextChannel: Channel,
        nextCacheKey: String,
        prevChannel: Channel,
        prevCacheKey: String,
        force: Boolean
    ) {
        coroutineScope {
            launch { prefetchOne(nextChannel, nextCacheKey, force) }
            launch { prefetchOne(prevChannel, prevCacheKey, force) }
        }
    }

    private suspend fun prefetchOne(channel: Channel, cacheKey: String, force: Boolean) {
        if (!force && getCached(cacheKey) != null) return
        try {
            MediaResolver.resolveMediaParams(appContext, channel, channel.url)
                ?.let { putCached(cacheKey, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed prefetch must never crash the app or cancel the other prefetch
            Log.w(TAG, "Prefetch failed for ${channel.name}", e)
        }
    }

    fun retryResolution(channel: Channel, sourceUrl: String) {
        val cacheKey = "${channel.name}|$sourceUrl"
        prefetchCache.remove(cacheKey)
        resolveChannel(channel, sourceUrl)
    }
}
