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
import com.streambd.iptv.data.models.TvModeChannel
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

    var tvModeChannels: List<TvModeChannel> = emptyList()
    private var appContext: Context? = null
    
    val jsonFormat = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun initTvMode(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
        _isTvModeEnabled.value = prefs.getBoolean("is_tv_mode_enabled", false)
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonString = context.assets.open("channels.json").bufferedReader().use { it.readText() }
                tvModeChannels = jsonFormat.decodeFromString(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val lastChannelJson = prefs.getString("last_channel", null)
        val lastPlaylistJson = prefs.getString("last_playlist", null)
        if (lastChannelJson != null) {
            try {
                val channel = jsonFormat.decodeFromString<Channel>(lastChannelJson)
                _selectedChannel.value = channel
                if (lastPlaylistJson != null) {
                    val playlist = jsonFormat.decodeFromString<List<Channel>>(lastPlaylistJson)
                    _currentPlaylist = playlist
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleTvMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_tv_mode_enabled", enabled).apply()
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
                        .putString("last_playlist", jsonFormat.encodeToString(_currentPlaylist.take(200))) // Limit to 200 channels to save space
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
        if (tvModeChannels.isEmpty()) return
        val matchedTvChannel = tvModeChannels.find { it.channelNo == number } ?: return
        
        // Find matching source in loaded playlists
        val searchKeywords = matchedTvChannel.keywords.map { it.lowercase() } + matchedTvChannel.name.lowercase()
        
        // Search in current playlist first, then category channels
        val allAvailableChannels = _currentPlaylist + _categoryChannels.value.values.flatten()
        val matchingChannels = allAvailableChannels.distinctBy { it.url }.filter { channel ->
            val channelNameLower = channel.name.lowercase()
            searchKeywords.any { kw -> channelNameLower.contains(kw) }
        }

        if (matchingChannels.isNotEmpty()) {
            // Combine sources if multiple matching channels found to create fallback
            val combinedSources = mutableMapOf<String, String>()
            matchingChannels.forEach { ch ->
                if (ch.sources.isNotEmpty()) {
                    combinedSources.putAll(ch.sources)
                } else if (ch.url.isNotEmpty()) {
                    val resolution = if (ch.name.contains("HD", true)) "HD" else "Auto"
                    combinedSources[resolution] = ch.url
                }
            }
            
            val bestMatch = matchingChannels.first()
            val finalChannel = bestMatch.copy(
                name = matchedTvChannel.name,
                logo = matchedTvChannel.tvgLogo.ifEmpty { bestMatch.logo },
                sources = if (combinedSources.isNotEmpty()) combinedSources else bestMatch.sources
            )
            selectChannel(finalChannel)
        }
    }

    fun tuneToNextChannel(isNumberMapped: Boolean) {
        val current = _selectedChannel.value ?: return
        if (isNumberMapped && tvModeChannels.isNotEmpty()) {
            // Try to find current channel in TV Mode Channels
            val currentTvIndex = tvModeChannels.indexOfFirst { tvCh -> 
                tvCh.name.equals(current.name, ignoreCase = true) || 
                tvCh.keywords.any { it.equals(current.name, ignoreCase = true) } 
            }
            if (currentTvIndex != -1) {
                val nextIndex = if (currentTvIndex < tvModeChannels.size - 1) currentTvIndex + 1 else 0
                tuneToChannelNumber(tvModeChannels[nextIndex].channelNo)
                return
            }
        }
        selectNextChannel()
    }

    fun tuneToPrevChannel(isNumberMapped: Boolean) {
        val current = _selectedChannel.value ?: return
        if (isNumberMapped && tvModeChannels.isNotEmpty()) {
            val currentTvIndex = tvModeChannels.indexOfFirst { tvCh -> 
                tvCh.name.equals(current.name, ignoreCase = true) || 
                tvCh.keywords.any { it.equals(current.name, ignoreCase = true) } 
            }
            if (currentTvIndex != -1) {
                val prevIndex = if (currentTvIndex > 0) currentTvIndex - 1 else tvModeChannels.size - 1
                tuneToChannelNumber(tvModeChannels[prevIndex].channelNo)
                return
            }
        }
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
