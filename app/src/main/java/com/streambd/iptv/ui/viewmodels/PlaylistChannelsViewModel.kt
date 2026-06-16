package com.streambd.iptv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.repository.LiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistChannelsViewModel : ViewModel() {
    private val liveRepository = LiveRepository()
    private var currentUrl: String? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _allChannels = MutableStateFlow<List<Channel>>(emptyList())
    
    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups: StateFlow<List<String>> = _groups.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String>("All")
    val selectedGroup: StateFlow<String> = _selectedGroup.asStateFlow()

    private val _displayedChannels = MutableStateFlow<List<Channel>>(emptyList())
    val displayedChannels: StateFlow<List<Channel>> = _displayedChannels.asStateFlow()
    
    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    fun loadPlaylist(url: String) {
        if (currentUrl == url && _allChannels.value.isNotEmpty()) return
        currentUrl = url
        
        viewModelScope.launch {
            _isLoading.value = true
            // Clear old state
            _allChannels.value = emptyList()
            _displayedChannels.value = emptyList()
            _groups.value = emptyList()
            _selectedGroup.value = "All"

            val channels = liveRepository.getChannelsForCategory(url)
            _allChannels.value = channels
            
            val uniqueGroups = channels.mapNotNull { it.group }.filter { it.isNotBlank() }.distinct().sorted()
            _groups.value = listOf("All") + uniqueGroups
            
            selectGroup("All")
            _isLoading.value = false
        }
    }

    fun selectGroup(group: String) {
        _selectedGroup.value = group
        if (group == "All") {
            _displayedChannels.value = _allChannels.value
        } else {
            _displayedChannels.value = _allChannels.value.filter { it.group == group }
        }
    }

    fun selectChannel(channel: Channel) {
        _selectedChannel.value = channel
    }
}
