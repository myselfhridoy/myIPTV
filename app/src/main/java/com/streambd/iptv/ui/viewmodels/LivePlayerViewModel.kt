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
import java.util.concurrent.atomic.AtomicInteger

/**
 * The URL that will actually be handed to ExoPlayer when this channel is
 * tuned for the first time.
 *
 * CRITICAL: [LivePlayerScreen] must initialize `currentSourceUrl` via this
 * same helper so that the resolve-time cache key
 *   "${channel.name}|${sourceUrl}"
 * matches the prefetch-time cache key produced by [LivePlayerViewModel].
 * Without this, prefetch silently never hits.
 */
internal fun Channel.primarySourceUrl(): String =
    sources.values.firstOrNull() ?: url

class LivePlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _currentMediaSource = MutableStateFlow<MediaSource?>(null)
    val currentMediaSource: StateFlow<MediaSource?> = _currentMediaSource.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _resolutionFailed = MutableStateFlow(false)
    val resolutionFailed: StateFlow<Boolean> = _resolutionFailed.asStateFlow()

    private data class CacheEntry(val params: ResolvedMediaParams, val timestamp: Long)

    private val prefetchCache = ConcurrentHashMap<String, CacheEntry>()

    private var resolveJob: Job? = null
    private var prefetchJob: Job? = null

    private val resolveGeneration = AtomicInteger(0)

    private companion object {
        const val TAG = "LivePlayerViewModel"
        const val MAX_CACHE_SIZE = 12
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        // Don't prefetch while the user is quickly channel-surfing.
        const val PREFETCH_DELAY_MS = 1_200L
        // Refresh neighbours before CACHE_TTL_MS expires.
        const val REFRESH_INTERVAL_MS = 4 * 60 * 1000L

        // Tokens that require the resolver to actually visit a page / WebView
        // and therefore cannot be prefetched in the background. Kept in sync
        // with MediaResolver's mustUseResolver list.
        private val RESOLVER_ONLY_TOKENS = setOf(
            "streambd", "streamplay", "crichd", "dlhd"
        )
        private val NUMERIC_TOKEN_REGEX = Regex("""^[\\/]?\d+$""")
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

        // Always keep the cache bounded. Previously the trim only ran inside
        // prefetchChannels, so a workflow that never triggered prefetch could
        // grow the map without bound.
        if (prefetchCache.size > MAX_CACHE_SIZE) {
            val now = System.currentTimeMillis()
            // 1) Drop expired entries first.
            prefetchCache.entries
                .filter { now - it.value.timestamp > CACHE_TTL_MS }
                .forEach { prefetchCache.remove(it.key) }
            // 2) Evict oldest-first if still over budget.
            if (prefetchCache.size > MAX_CACHE_SIZE) {
                val excess = prefetchCache.size - MAX_CACHE_SIZE
                prefetchCache.entries
                    .sortedBy { it.value.timestamp }
                    .take(excess)
                    .forEach { prefetchCache.remove(it.key) }
            }
        }
    }

    fun resolveChannel(channel: Channel, sourceUrl: String) {
        val myGeneration = resolveGeneration.incrementAndGet()

        resolveJob?.cancel()
        _isLoading.value = true
        _resolutionFailed.value = false
        _currentMediaSource.value = null

        val cacheKey = "${channel.name}|$sourceUrl"
        val cachedParams = getCached(cacheKey)
        if (cachedParams != null) {
            if (myGeneration == resolveGeneration.get()) {
                try {
                    _currentMediaSource.value = MediaResolver.buildMediaSource(appContext, cachedParams)
                    _resolutionFailed.value = false
                } catch (e: Exception) {
                    Log.e(TAG, "buildMediaSource failed for cached ${channel.name}", e)
                    _resolutionFailed.value = true
                } finally {
                    _isLoading.value = false
                }
            }
            return
        }

        resolveJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val params = MediaResolver.resolveMediaParams(appContext, channel, sourceUrl)

                if (myGeneration != resolveGeneration.get()) {
                    Log.d(TAG, "Dropping stale resolve (pre-build) for ${channel.name}")
                    return@launch
                }
                ensureActive()

                if (params == null) {
                    _resolutionFailed.value = true
                    return@launch
                }

                putCached(cacheKey, params)

                val source = withContext(Dispatchers.Main) {
                    MediaResolver.buildMediaSource(appContext, params)
                }

                if (myGeneration != resolveGeneration.get()) {
                    Log.d(TAG, "Dropping stale resolve (post-build) for ${channel.name}")
                    return@launch
                }
                ensureActive()

                _currentMediaSource.value = source
                _resolutionFailed.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve ${channel.name}", e)
                if (myGeneration == resolveGeneration.get()) {
                    _resolutionFailed.value = true
                }
            } finally {
                if (myGeneration == resolveGeneration.get()) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun prefetchChannels(playlist: List<Channel>, currentIndex: Int) {
        if (playlist.size <= 1 || currentIndex == -1) return

        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(PREFETCH_DELAY_MS)

            val nextIndex = if (currentIndex < playlist.size - 1) currentIndex + 1 else 0
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else playlist.size - 1

            val nextChannel = playlist[nextIndex]
            val prevChannel = playlist[prevIndex]

            // Serialize the initial prefetch (next first, then prev). Running
            // them in parallel doubles bandwidth usage and — on congested
            // connections — delays the current live stream's segments, which
            // shows up as A/V drift.
            prefetchOne(nextChannel, force = false)
            prefetchOne(prevChannel, force = false)

            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                prefetchOne(nextChannel, force = true)
                prefetchOne(prevChannel, force = true)
            }
        }
    }

    /** Stop background prefetching (call when leaving the player screen). */
    fun stopPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    /**
     * True when resolving this channel requires an interactive WebView and
     * thus can't be safely backgrounded.
     */
    private fun needsWebView(channel: Channel): Boolean {
        val u = channel.url.lowercase()
        val token = channel.tokenUrl?.lowercase().orEmpty()
        if (u.contains("tokenurl=") && RESOLVER_ONLY_TOKENS.any { u.contains("tokenurl=$it") }) return true
        if (token.isNotEmpty() && token in RESOLVER_ONLY_TOKENS) return true
        if (token.isNotEmpty() && NUMERIC_TOKEN_REGEX.matches(token)) return true
        return false
    }

    private suspend fun prefetchOne(channel: Channel, force: Boolean) {
        if (needsWebView(channel)) return
        // CRITICAL: use the same URL the screen passes to resolveChannel.
        // Anything else produces a cache key mismatch.
        val sourceUrl = channel.primarySourceUrl()
        val cacheKey = "${channel.name}|$sourceUrl"
        if (!force && getCached(cacheKey) != null) return
        try {
            MediaResolver.resolveMediaParams(appContext, channel, sourceUrl)
                ?.let { putCached(cacheKey, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Prefetch failed for ${channel.name}", e)
        }
    }

    fun retryResolution(channel: Channel, sourceUrl: String) {
        val cacheKey = "${channel.name}|$sourceUrl"
        prefetchCache.remove(cacheKey)
        resolveChannel(channel, sourceUrl)
    }
}
