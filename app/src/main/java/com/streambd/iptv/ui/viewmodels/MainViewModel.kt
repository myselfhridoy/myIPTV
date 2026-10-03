package com.streambd.iptv.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.Addon
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.LiveCategory
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.data.repository.LiveRepository
import com.streambd.iptv.utils.AnalyticsLogger
import com.streambd.iptv.utils.QuickJsAddonManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val liveRepository = LiveRepository()

    private val appContext = application.applicationContext

    private val _liveCategories = MutableStateFlow<List<LiveCategory>>(emptyList())
    val liveCategories: StateFlow<List<LiveCategory>> = _liveCategories.asStateFlow()

    private val _isLoadingLive = MutableStateFlow(false)
    val isLoadingLive: StateFlow<Boolean> = _isLoadingLive.asStateFlow()

    private val _addons = MutableStateFlow<List<Addon>>(emptyList())
    val addons: StateFlow<List<Addon>> = _addons.asStateFlow()

    private val _isLoadingAddons = MutableStateFlow(false)
    val isLoadingAddons: StateFlow<Boolean> = _isLoadingAddons.asStateFlow()

    private val _categoryChannels = MutableStateFlow<Map<String, List<Channel>>>(emptyMap())
    val categoryChannels: StateFlow<Map<String, List<Channel>>> = _categoryChannels.asStateFlow()

    private val _selectedCategory = MutableStateFlow<LiveCategory?>(null)
    val selectedCategory: StateFlow<LiveCategory?> = _selectedCategory.asStateFlow()

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    private val _selectedAddon = MutableStateFlow<Addon?>(null)
    val selectedAddon: StateFlow<Addon?> = _selectedAddon.asStateFlow()

    private val _selectedStream = MutableStateFlow<StreamSource?>(null)
    val selectedStream: StateFlow<StreamSource?> = _selectedStream.asStateFlow()

    private val _availableStreams = MutableStateFlow<List<StreamSource>>(emptyList())
    val availableStreams: StateFlow<List<StreamSource>> = _availableStreams.asStateFlow()

    private val _isResolvingStreams = MutableStateFlow(false)
    val isResolvingStreams: StateFlow<Boolean> = _isResolvingStreams.asStateFlow()

    private val _isTvModeEnabled = MutableStateFlow(false)
    val isTvModeEnabled: StateFlow<Boolean> = _isTvModeEnabled.asStateFlow()

    private val _tvModePlaylists = MutableStateFlow<Set<String>>(emptySet())
    val tvModePlaylists: StateFlow<Set<String>> = _tvModePlaylists.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<List<Channel>>(emptyList())
    val currentPlaylist: StateFlow<List<Channel>> = _currentPlaylist.asStateFlow()

    private val jsonFormat = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private var tvModeJob: Job? = null
    private var saveChannelJob: Job? = null
    private var enrichJob: Job? = null
    private var vodJob: Job? = null
    private val categoryFetchInFlight: MutableSet<String> =
    java.util.concurrent.ConcurrentHashMap.newKeySet()

    // ==== Search-time background loader state (only used while search is active) ====
    private val _searchLoadProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val searchLoadProgress: StateFlow<Pair<Int, Int>?> = _searchLoadProgress.asStateFlow()

    private var searchLoadJob: Job? = null
    // ================================================================================

    fun initTvMode(context: Context) {
        val prefs = context.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
        _isTvModeEnabled.value = prefs.getBoolean("is_tv_mode_enabled", false)
        val savedPlaylists = prefs.getStringSet("tv_mode_playlists", emptySet()) ?: emptySet()
        _tvModePlaylists.value = savedPlaylists
        val includeEvents = prefs.getBoolean("tv_mode_include_events", true)

        val lastChannelJson = prefs.getString("last_channel", null)
        if (lastChannelJson != null) {
            try {
                val channel = jsonFormat.decodeFromString<Channel>(lastChannelJson)
                _selectedChannel.value = channel
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (_isTvModeEnabled.value) {
            // Avoid running several TV-mode loads at the same time
            tvModeJob?.cancel()
            tvModeJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val allChannels = mutableListOf<Channel>()

                    // 1. Fetch Selected Playlists
                    val categories = liveRepository.getLiveCategories()
                    val selectedCategories = categories.filter { savedPlaylists.contains(it.name) }

                    coroutineScope {
                        val categoryDeferreds = selectedCategories.map { category ->
                            async {
                                try {
                                    liveRepository.getChannelsForCategory(appContext, category.url)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                        }
                        allChannels.addAll(categoryDeferreds.awaitAll().flatten())
                    }

                    // 1.5 Fetch Selected User Playlists
                    try {
                        val userPlaylists = com.streambd.iptv.data.repository.AppDatabase.getDatabase(appContext).playlistDao().getAllPlaylistsSync()
                        val selectedUserPlaylists = userPlaylists.filter { savedPlaylists.contains(it.title) }

                        coroutineScope {
                            val userDeferreds = selectedUserPlaylists.map { playlist ->
                                async {
                                    try {
                                        val url = playlist.url ?: playlist.filePath ?: ""
                                        if (url.isNotBlank()) {
                                            liveRepository.getChannelsForCategory(appContext, url)
                                        } else emptyList()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                }
                            }
                            allChannels.addAll(userDeferreds.awaitAll().flatten())
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 2. Fetch Live Events (if included)
                    if (includeEvents) {
                        try {
                            val fullApiUrl = com.streambd.iptv.utils.RemoteConfigManager.getLiveEventsApiUrl()
                            val baseDirectory = fullApiUrl.substringBeforeLast("/")
                            val fileName = fullApiUrl.substringAfterLast("/")

                            val response = com.streambd.iptv.data.api.RetrofitClient.apiService.getLiveEvents(fullApiUrl)
                            val liveEvents = response.events.filter { it.is_live == 1 }

                            coroutineScope {
                                val eventDeferreds = liveEvents.map { event ->
                                    async {
                                        try {
                                            val streamUrl = "$baseDirectory/${event.id}/$fileName"
                                            val streamRes = com.streambd.iptv.data.api.RetrofitClient.apiService.getLiveEventStreams(streamUrl)
                                            if (streamRes.streams.isNotEmpty()) {
                                                val sourcesMap = mutableMapOf<String, String>()
                                                streamRes.streams.forEachIndexed { idx, s ->
                                                    var sName = s.label.takeIf { !it.isNullOrBlank() } ?: "Source ${idx + 1}"
                                                    if (sourcesMap.containsKey(sName)) sName = "$sName (${idx + 1})"
                                                    var srcUrl = s.stream_url ?: ""
                                                    if (s.drm_key != null && s.drm_kid != null) {
                                                        srcUrl += "|drm-type=clearkey|drm-key=${s.drm_kid}:${s.drm_key}"
                                                    }
                                                    sourcesMap[sName] = srcUrl
                                                }

                                                val firstStream = streamRes.streams.first()
                                                var initialUrl = firstStream.stream_url ?: ""
                                                if (firstStream.drm_key != null && firstStream.drm_kid != null) {
                                                    initialUrl += "|drm-type=clearkey|drm-key=${firstStream.drm_kid}:${firstStream.drm_key}"
                                                }

                                                Channel(
                                                    name = event.league ?: "Live Event",
                                                    url = initialUrl,
                                                    group = event.sport ?: "Live",
                                                    logo = event.league_icon ?: "",
                                                    isLiveEvent = true,
                                                    drm = if (firstStream.drm_key != null) com.streambd.iptv.data.models.DrmConfig(
                                                        type = "clearkey",
                                                        rawKeyPair = "${firstStream.drm_kid}:${firstStream.drm_key}"
                                                    ) else null,
                                                    sources = sourcesMap
                                                )
                                            } else null
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            null
                                        }
                                    }
                                }
                                allChannels.addAll(eventDeferreds.awaitAll().filterNotNull())
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Don't wipe an existing playlist if everything failed to load
                    if (allChannels.isNotEmpty()) {
                        _currentPlaylist.value = allChannels
                    }

                    // If no channel selected, pick the first one
                    if (_selectedChannel.value == null && allChannels.isNotEmpty()) {
                        _selectedChannel.value = allChannels.first()
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun saveTvModeConfig(enabled: Boolean, playlists: Set<String>, includeEvents: Boolean) {
        val prefs = appContext.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_tv_mode_enabled", enabled)
            .putStringSet("tv_mode_playlists", playlists)
            .putBoolean("tv_mode_include_events", includeEvents)
            .remove("last_channel")
            .apply()
        _isTvModeEnabled.value = enabled
        _tvModePlaylists.value = playlists
    }

    fun toggleTvMode(enabled: Boolean) {
        val prefs = appContext.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_tv_mode_enabled", enabled).apply()
        _isTvModeEnabled.value = enabled
    }

    fun selectCategory(category: LiveCategory) {
        _selectedCategory.value = category
        fetchChannelsForCategory(category.name, category.url)
    }

    fun selectChannel(channel: Channel) {
        _selectedChannel.value = channel

        // ── Analytics: track channel selection ──
        // Fires on every channel change: card taps from LiveTv /
        // LiveCategory / LiveEvents / PlaylistChannels screens,
        // player Up/Down channel surfing, number-pad tuning, and
        // saved-channel restore. Throttle-free by design.
        AnalyticsLogger.logChannelPlayed(
            context = appContext,
            name = channel.name,
            group = channel.group,
            url = channel.url
        )

        // Debounced + cancellable so fast channel surfing can't write an older channel last
        saveChannelJob?.cancel()
        saveChannelJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            try {
                val prefs = appContext.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("last_channel", jsonFormat.encodeToString(channel))
                    .apply()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Lazy load additional quality/server options for portal channels (like Oxoo)
        enrichJob?.cancel()
        if (channel.tokenId != null && channel.tokenId != -1 && channel.sources.size <= 1 && channel.tokenUrl?.contains("single_details") == true) {
            enrichJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val enrichedSources = com.streambd.iptv.utils.OxooPortalHandler.fetchAdditionalSources(channel)
                    if (enrichedSources.size > channel.sources.size) {
                        if (_selectedChannel.value === channel || (_selectedChannel.value?.tokenId == channel.tokenId && _selectedChannel.value?.url == channel.url)) {
                            val updatedChannel = channel.copy(sources = enrichedSources)
                            _selectedChannel.value = updatedChannel
                            _currentPlaylist.value = _currentPlaylist.value.map {
                                if (it.tokenId == channel.tokenId && it.name == channel.name) updatedChannel else it
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setCurrentPlaylist(channels: List<Channel>) {
        _currentPlaylist.value = channels
    }

    /**
     * Finds the channel in the playlist. Falls back to name + url/tokenId matching because the
     * selected channel (e.g. restored from saved JSON, or enriched with extra sources) is often
     * not `equals()` to the instance in the freshly loaded playlist.
     */
    private fun indexOfChannel(playlist: List<Channel>, channel: Channel): Int {
        val exact = playlist.indexOf(channel)
        if (exact != -1) return exact
        return playlist.indexOfFirst {
            it.name == channel.name &&
                (it.url == channel.url || (it.tokenId != null && it.tokenId == channel.tokenId))
        }
    }

    fun selectNextChannel() {
        val currentChannel = _selectedChannel.value ?: return
        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return

        val currentIndex = indexOfChannel(playlist, currentChannel)
        val nextIndex = when {
            currentIndex == -1 -> 0
            currentIndex < playlist.size - 1 -> currentIndex + 1
            else -> 0
        }
        selectChannel(playlist[nextIndex])
    }

    fun selectPreviousChannel() {
        val currentChannel = _selectedChannel.value ?: return
        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return

        val currentIndex = indexOfChannel(playlist, currentChannel)
        val prevIndex = when {
            currentIndex == -1 -> playlist.size - 1
            currentIndex > 0 -> currentIndex - 1
            else -> playlist.size - 1
        }
        selectChannel(playlist[prevIndex])
    }

    fun tuneToChannelNumber(number: String) {
        val index = number.toIntOrNull()?.minus(1) ?: return
        val playlist = _currentPlaylist.value
        if (index in playlist.indices) {
            selectChannel(playlist[index])
        }
    }

    fun tuneToNextChannel() {
        selectNextChannel()
    }

    fun tuneToPrevChannel() {
        selectPreviousChannel()
    }

    fun selectAddon(addon: Addon) {
        _selectedAddon.value = addon
    }

    fun selectStream(stream: StreamSource) {
        _selectedStream.value = stream
    }

    private var lastFetchedTmdbId: Int? = null
    private var lastFetchedSeason: Int? = null
    private var lastFetchedEpisode: Int? = null
    private var hasCompletedFetchForCurrent: Boolean = false

    // Identifies the latest VOD fetch so results of older/cancelled fetches are ignored
    @Volatile
    private var vodRequestId = 0

    private val _selectedSeason = MutableStateFlow<Int?>(null)
    val selectedSeason: StateFlow<Int?> = _selectedSeason.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<Int?>(null)
    val selectedEpisode: StateFlow<Int?> = _selectedEpisode.asStateFlow()

    fun setSelectedSeason(season: Int?) {
        _selectedSeason.value = season
    }

    fun setSelectedEpisode(episode: Int?) {
        _selectedEpisode.value = episode
    }

    // ═══════════════════════════════════════════════════════════════
    // VOD selection persistence
    //
    // Tracks which TMDB id the current season/episode selection belongs
    // to, so switching to a different title doesn't inherit the previous
    // title's S/E. Returning from SourceSelection on the SAME title
    // restores the last pick instead of resetting to S1E1.
    // ═══════════════════════════════════════════════════════════════
    private var vodSelectionMediaId: Int? = null

    /**
     * Returns the persisted (season, episode) pair only if it belongs
     * to the requested media id. Otherwise returns null so the caller
     * can fall back to defaults.
     */
    fun getVodSelectionFor(mediaId: Int): Pair<Int?, Int?>? {
        if (vodSelectionMediaId != mediaId) return null
        return _selectedSeason.value to _selectedEpisode.value
    }

    /**
     * Persists the user's S/E choice, scoped to a specific media id.
     * Also mirrors into `_selectedSeason` / `_selectedEpisode` so any
     * downstream consumer that reads those flows keeps working.
     */
    fun setVodSelection(mediaId: Int, season: Int?, episode: Int?) {
        vodSelectionMediaId = mediaId
        _selectedSeason.value = season
        _selectedEpisode.value = episode
    }

    /**
     * Clears the persisted S/E selection. Call when leaving VOD
     * entirely, or when the user navigates away from a title they no
     * longer intend to resume.
     */
    fun clearVodSelection() {
        vodSelectionMediaId = null
        _selectedSeason.value = null
        _selectedEpisode.value = null
    }

    /** Call this when leaving SourceSelectionScreen or resetting streams so the next entry triggers a fresh fetch. */
    fun resetVodStreams() {
        // Stop any in-flight fetch so it can't add streams after the reset
        vodJob?.cancel()
        vodRequestId++
        lastFetchedTmdbId = null
        lastFetchedSeason = null
        lastFetchedEpisode = null
        hasCompletedFetchForCurrent = false
        _availableStreams.value = emptyList()
        _isResolvingStreams.value = false
    }

    fun fetchVodStreams(context: Context, type: String, tmdbId: Int, season: Int? = null, episode: Int? = null, forceRefresh: Boolean = false) {
        val isSameMedia = (tmdbId == lastFetchedTmdbId && season == lastFetchedSeason && episode == lastFetchedEpisode)
        if (isSameMedia && !forceRefresh) {
            if (_isResolvingStreams.value || hasCompletedFetchForCurrent) {
                return // Same request is already in-flight or already has completed results
            }
        }
        lastFetchedTmdbId = tmdbId
        lastFetchedSeason = season
        lastFetchedEpisode = episode
        hasCompletedFetchForCurrent = false

        vodJob?.cancel()
        val requestId = ++vodRequestId

        vodJob = viewModelScope.launch {
            _isResolvingStreams.value = true
            _availableStreams.value = emptyList()
            try {
                QuickJsAddonManager.resolveFromAddons(context, type, tmdbId, season, episode) { sources ->
                    if (requestId == vodRequestId) {
                        _availableStreams.update { it + sources }
                    }
                }
                if (requestId == vodRequestId) {
                    hasCompletedFetchForCurrent = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Only the latest request may clear the loading flag
                if (requestId == vodRequestId) {
                    _isResolvingStreams.value = false
                }
            }
        }
    }

    init {
        fetchLiveCategories()
        // Addons are fetched when the context is available
    }

    fun fetchLiveCategories() {
        viewModelScope.launch {
            _isLoadingLive.value = true
            try {
                _liveCategories.value = liveRepository.getLiveCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingLive.value = false
            }
        }
    }

    fun fetchAddons(context: Context? = null) {
        if (context == null) return
        viewModelScope.launch {
            _isLoadingAddons.value = true
            try {
                _addons.value = QuickJsAddonManager.getAddons(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingAddons.value = false
            }
        }
    }

    fun fetchChannelsForCategory(categoryName: String, url: String) {
        if (_categoryChannels.value.containsKey(categoryName)) return
        // Skip if the same category is already being fetched
        if (!categoryFetchInFlight.add(categoryName)) return

        viewModelScope.launch {
            try {
                val channels = liveRepository.getChannelsForCategory(appContext, url)
                _categoryChannels.update { current ->
                    current + (categoryName to channels)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not cached on failure, so it can be retried next time
                e.printStackTrace()
            } finally {
                categoryFetchInFlight.remove(categoryName)
            }
        }
    }

    /**
     * সার্চের জন্য এখনো লোড না হওয়া ক্যাটেগরিগুলো সর্বোচ্চ ৩টা করে একসাথে লোড করে।
     * ব্যর্থ ক্যাটেগরি ক্যাশ হয় না, পরে আবার চেষ্টা হবে।
     * সার্চ না করলে এটি কখনো কল হয় না, তাই সাধারণ ব্রাউজিং-এ কোনো বাড়তি লোড নেই।
     */
    fun loadAllCategoriesForSearch() {
        if (searchLoadJob?.isActive == true) return

        val pending = _liveCategories.value.filter {
            !_categoryChannels.value.containsKey(it.name)
        }
        if (pending.isEmpty()) return

        searchLoadJob = viewModelScope.launch {
            val total = pending.size
            var done = 0
            _searchLoadProgress.value = 0 to total
            val semaphore = Semaphore(3)

            try {
                coroutineScope {
                    pending.map { category ->
                        async {
                            semaphore.withPermit {
                                // অন্য কেউ (ক্যাটেগরি খোলা) ইতিমধ্যে লোড করছে বা করে ফেলেছে
                                if (_categoryChannels.value.containsKey(category.name) ||
                                    !categoryFetchInFlight.add(category.name)
                                ) {
                                    return@withPermit
                                }
                                try {
                                    val channels = withTimeoutOrNull(15_000L) {
                                        liveRepository.getChannelsForCategory(appContext, category.url)
                                    }
                                    if (channels != null) {
                                        _categoryChannels.update { it + (category.name to channels) }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    categoryFetchInFlight.remove(category.name)
                                }
                            }
                            done++
                            _searchLoadProgress.value = done to total
                        }
                    }.awaitAll()
                }
            } finally {
                _searchLoadProgress.value = null
            }
        }
    }

    /** সার্চ বন্ধ হলে ব্যাকগ্রাউন্ড লোড থামাতে চাইলে */
    fun cancelSearchLoad() {
        searchLoadJob?.cancel()
        searchLoadJob = null
    }
}
