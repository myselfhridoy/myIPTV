package com.streambd.iptv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.Addon
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.LiveCategory
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.data.repository.LiveRepository
import com.streambd.iptv.utils.AddonManager
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val liveRepository = LiveRepository()

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

    private var appContext: Context? = null
    
    val jsonFormat = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun initTvMode(context: Context) {
        appContext = context.applicationContext
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
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val allChannels = mutableListOf<Channel>()
                    
                    // 1. Fetch Selected Playlists
                    val categories = liveRepository.getLiveCategories()
                    val selectedCategories = categories.filter { savedPlaylists.contains(it.name) }
                    
                    for (category in selectedCategories) {
                        try {
                            val channels = liveRepository.getChannelsForCategory(category.url)
                            allChannels.addAll(channels)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    
                    // 2. Fetch Live Events (if included)
                    if (includeEvents) {
                        try {
                            val fullApiUrl = com.streambd.iptv.utils.RemoteConfigManager.getLiveEventsApiUrl()
                            val baseDirectory = fullApiUrl.substringBeforeLast("/")
                            val fileName = fullApiUrl.substringAfterLast("/")
                            
                            val response = com.streambd.iptv.data.api.RetrofitClient.apiService.getLiveEvents(fullApiUrl)
                            val liveEvents = response.events.filter { it.is_live == 1 }
                            
                            for (event in liveEvents) {
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
                                        
                                        val eventChannel = Channel(
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
                                        
                                        allChannels.add(eventChannel)
                                    }
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    
                    _currentPlaylist = allChannels
                    
                    // If no channel selected, pick the first one
                    if (_selectedChannel.value == null && allChannels.isNotEmpty()) {
                        _selectedChannel.value = allChannels.first()
                    }
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun saveTvModeConfig(context: Context, enabled: Boolean, playlists: Set<String>, includeEvents: Boolean) {
        val prefs = context.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_tv_mode_enabled", enabled)
            .putStringSet("tv_mode_playlists", playlists)
            .putBoolean("tv_mode_include_events", includeEvents)
            .remove("last_channel")
            .commit()
        _isTvModeEnabled.value = enabled
        _tvModePlaylists.value = playlists
    }

    fun toggleTvMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_tv_mode_enabled", enabled).commit()
        _isTvModeEnabled.value = enabled
    }

    fun selectCategory(category: LiveCategory) {
        _selectedCategory.value = category
        fetchChannelsForCategory(category.name, category.url)
    }

    fun selectChannel(channel: Channel) {
        _selectedChannel.value = channel

        appContext?.let { context ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val prefs = context.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("last_channel", jsonFormat.encodeToString(channel))
                        .apply()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        // Lazy load additional quality/server options for portal channels (like Oxoo)
        if (channel.tokenId != null && channel.tokenId != -1 && channel.sources.size <= 1 && channel.tokenUrl?.contains("single_details") == true) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val enrichedSources = com.streambd.iptv.utils.OxooPortalHandler.fetchAdditionalSources(channel)
                    if (enrichedSources.size > channel.sources.size) {
                        val updatedChannel = channel.copy(sources = enrichedSources)
                        _selectedChannel.value = updatedChannel
                        _currentPlaylist = _currentPlaylist.map {
                            if (it.tokenId == channel.tokenId && it.name == channel.name) updatedChannel else it
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private var _currentPlaylist = emptyList<Channel>()

    fun setCurrentPlaylist(channels: List<Channel>) {
        _currentPlaylist = channels
    }

    fun selectNextChannel() {
        val currentChannel = _selectedChannel.value ?: return
        if (_currentPlaylist.isEmpty()) return

        val currentIndex = _currentPlaylist.indexOf(currentChannel)
        if (currentIndex != -1) {
            val nextIndex = if (currentIndex < _currentPlaylist.size - 1) currentIndex + 1 else 0
            selectChannel(_currentPlaylist[nextIndex])
        }
    }

    fun selectPreviousChannel() {
        val currentChannel = _selectedChannel.value ?: return
        if (_currentPlaylist.isEmpty()) return

        val currentIndex = _currentPlaylist.indexOf(currentChannel)
        if (currentIndex != -1) {
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else _currentPlaylist.size - 1
            selectChannel(_currentPlaylist[prevIndex])
        }
    }

    fun tuneToChannelNumber(number: String) {
        val index = number.toIntOrNull()?.minus(1) ?: return
        if (index in _currentPlaylist.indices) {
            selectChannel(_currentPlaylist[index])
        }
    }

    fun tuneToNextChannel(isNumberMapped: Boolean) {
        selectNextChannel()
    }

    fun tuneToPrevChannel(isNumberMapped: Boolean) {
        selectPreviousChannel()
    }

    fun selectAddon(addon: Addon) {
        _selectedAddon.value = addon
    }

    fun selectStream(stream: StreamSource) {
        _selectedStream.value = stream
    }

    var lastFetchedTmdbId: Int? = null
    var lastFetchedSeason: Int? = null
    var lastFetchedEpisode: Int? = null

    var selectedSeason: Int? = null
    var selectedEpisode: Int? = null

    fun fetchVodStreams(context: Context, type: String, tmdbId: Int, season: Int? = null, episode: Int? = null) {
        if (tmdbId == lastFetchedTmdbId && season == lastFetchedSeason && episode == lastFetchedEpisode) {
            return // Prevent re-fetching identical request
        }
        lastFetchedTmdbId = tmdbId
        lastFetchedSeason = season
        lastFetchedEpisode = episode

        viewModelScope.launch {
            _isResolvingStreams.value = true
            _availableStreams.value = emptyList()
            AddonManager.resolveFromAddons(context, type, tmdbId, season, episode) { sources ->
                val current = _availableStreams.value.toMutableList()
                current.addAll(sources)
                _availableStreams.value = current
            }
            _isResolvingStreams.value = false
        }
    }

    init {
        fetchLiveCategories()
        // Addons are fetched when the context is available
    }

    fun fetchLiveCategories() {
        viewModelScope.launch {
            _isLoadingLive.value = true
            val categories = liveRepository.getLiveCategories()
            _liveCategories.value = categories
            _isLoadingLive.value = false
        }
    }

    fun fetchAddons(context: Context? = null) {
        viewModelScope.launch {
            if (context != null) {
                _isLoadingAddons.value = true
                val fetchedAddons = AddonManager.getAddons(context)
                _addons.value = fetchedAddons
                _isLoadingAddons.value = false
            }
        }
    }

    fun fetchChannelsForCategory(categoryName: String, url: String) {
        if (_categoryChannels.value.containsKey(categoryName)) return

        viewModelScope.launch {
            val channels = liveRepository.getChannelsForCategory(url)
            val currentMap = _categoryChannels.value.toMutableMap()
            currentMap[categoryName] = channels
            _categoryChannels.value = currentMap
        }
    }
}
