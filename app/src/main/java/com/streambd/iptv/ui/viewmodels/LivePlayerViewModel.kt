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
 * CRITICAL: [LivePlayerScreen] must initialize its `currentSourceUrl` via
 * this same helper so that the resolve-time cache key
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

    /**
     * True when the last [resolveChannel] finished without producing a usable
     * MediaSource (params == null, or an exception was thrown).
     *
     * The screen reads this to render an error card instead of leaving the
     * user on a silent black screen. Reset to `false` at the start of every
     * new resolve.
     */
    private val _resolutionFailed = MutableStateFlow(false)
    val resolutionFailed: StateFlow<Boolean> = _resolutionFailed.asStateFlow()

    private data class CacheEntry(val params: ResolvedMediaParams, val timestamp: Long)

    private val prefetchCache = ConcurrentHashMap<String, CacheEntry>()

    private var resolveJob: Job? = null
    private var prefetchJob: Job? = null

    /**
     * Generation counter for channel resolution.
     *
     * Every call to [resolveChannel] increments this. Any result produced by a
     * resolve whose generation no longer matches the current one is discarded.
     * This protects against the case where [MediaResolver.resolveMediaParams]
     * swallows [CancellationException] or is otherwise slow to unwind —
     * cancelling the previous job alone would not prevent the stale write to
     * [_currentMediaSource] (or to [_resolutionFailed]).
     */
    private val resolveGeneration = AtomicInteger(0)

    private companion object {
        const val TAG = "LivePlayerViewModel"
        const val MAX_CACHE_SIZE = 12
        // Resolved stream URLs/tokens can expire, so don't reuse them forever.
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        // Don't prefetch while the user is quickly channel-surfing. Longer
        // here than the first-tune latency because we want the live stream
        // to fill its own buffer before we consume bandwidth for neighbours.
        const val PREFETCH_DELAY_MS = 1_500L
        // Re-resolve neighbours before CACHE_TTL_MS expires (must stay below
        // the TTL).
        const val REFRESH_INTERVAL_MS = 4 * 60 * 1000L

        // Tokens that force the resolver to actually go out to a page /
        // WebView. Mirrors MediaResolver's mustUseResolver token set.
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
            // 1) Drop expired entries first — they're free wins.
            prefetchCache.entries
                .filter { now - it.value.timestamp > CACHE_TTL_MS }
                .forEach { prefetchCache.remove(it.key) }
            // 2) If still over budget, evict oldest-first.
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
        // Bump the generation first so any in-flight older resolve is
        // invalidated even before its coroutine has finished unwinding.
        val myGeneration = resolveGeneration.incrementAndGet()

        resolveJob?.cancel()
        _isLoading.value = true
        _resolutionFailed.value = false
        _currentMediaSource.value = null

        val cacheKey = "${channel.name}|$sourceUrl"
        val cachedParams = getCached(cacheKey)
        if (cachedParams != null) {
            // Cache hit — synchronous on the caller's (main) dispatcher, so no
            // interleaving is possible. Still verify generation for
            // consistency.
            if (myGeneration == resolveGeneration.get()) {
                try {
                    _currentMediaSource.value =
                        MediaResolver.buildMediaSource(appContext, cachedParams)
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

                // ---- Generation check #1: bail out before expensive work. ----
                if (myGeneration != resolveGeneration.get()) {
                    Log.d(
                        TAG,
                        "Dropping stale resolve (pre-build) for ${channel.name} " +
                            "(gen=$myGeneration, current=${resolveGeneration.get()})"
                    )
                    return@launch
                }
                ensureActive()

                if (params == null) {
                    // Resolver could not produce a usable URL. Mark failure so
                    // the UI can show an error card instead of a
                    // forever-spinner.
                    _resolutionFailed.value = true
                    return@launch
                }

                putCached(cacheKey, params)

                val source = withContext(Dispatchers.Main) {
                    MediaResolver.buildMediaSource(appContext, params)
                }

                // ---- Generation check #2: another resolve may have started
                //      while we were hopping to the main thread. Drop if so. ----
                if (myGeneration != resolveGeneration.get()) {
                    Log.d(
                        TAG,
                        "Dropping stale resolve (post-build) for ${channel.name} " +
                            "(gen=$myGeneration, current=${resolveGeneration.get()})"
                    )
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
                // Only the latest generation may clear the loading flag; a
                // stale job must not hide the spinner belonging to a newer
                // job.
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

            // Refresh neighbours before the cache TTL runs out, so Up/Down
            // still hits the cache after a long time on one channel. Stops
            // when the job is cancelled (i.e. on channel change or leaving
            // the player).
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
     * True when resolving this SOURCE URL requires an interactive WebView and
     * thus can't be safely backgrounded. We inspect the source URL — not just
     * channel.url — because the token can live on the source URL while the
     * channel URL is a plain proxy.
     */
    private fun needsWebView(channel: Channel, sourceUrl: String): Boolean {
        val u = channel.url.lowercase()
        val s = sourceUrl.lowercase()
        val token = channel.tokenUrl?.lowercase().orEmpty()

        // Case 1: tokenurl= appears on the channel URL or the source URL.
        if (u.contains("tokenurl=") &&
            RESOLVER_ONLY_TOKENS.any { u.contains("tokenurl=$it") }
        ) return true
        if (s.contains("tokenurl=") &&
            RESOLVER_ONLY_TOKENS.any { s.contains("tokenurl=$it") }
        ) return true

        // Case 2: channel.tokenUrl declares a resolver-only token.
        if (token.isNotEmpty() && token in RESOLVER_ONLY_TOKENS) return true

        // Case 3: channel.tokenUrl is a bare numeric index (generic API id).
        if (token.isNotEmpty() && NUMERIC_TOKEN_REGEX.matches(token)) return true

        return false
    }

    private suspend fun prefetchOne(channel: Channel, force: Boolean) {
        // Use the same source URL the screen will pass to resolveChannel, so
        // the cache key matches. Anything else produces a permanent miss.
        val sourceUrl = channel.primarySourceUrl()

        // Skip prefetch for tokens that require an interactive WebView — we
        // must not launch a background WebView while the user is on a live
        // stream.
        if (needsWebView(channel, sourceUrl)) return

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
