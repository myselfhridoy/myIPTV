package com.streambd.iptv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.Addon
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.LiveCategory
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.data.repository.LiveRepository
import com.streambd.iptv.utils.AddonManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context

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

    fun selectCategory(category: LiveCategory) {
        _selectedCategory.value = category
        fetchChannelsForCategory(category.name, category.url)
    }

    fun selectChannel(channel: Channel) {
        _selectedChannel.value = channel
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
