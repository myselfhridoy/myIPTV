package com.streambd.iptv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.SportzfyApiResponse
import com.streambd.iptv.data.models.SportzfyEvent
import com.streambd.iptv.data.models.SportzfyStreamResponse
import com.streambd.iptv.data.models.SportzfyStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class LiveEventsViewModel : ViewModel() {
    private val _events = MutableStateFlow<List<SportzfyEvent>>(emptyList())
    val events: StateFlow<List<SportzfyEvent>> = _events

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val fullApiUrl: String
        get() = com.streambd.iptv.utils.RemoteConfigManager.getLiveEventsApiUrl()

    private val baseDirectory: String
        get() = fullApiUrl.substringBeforeLast("/")
        
    private val fileName: String
        get() = fullApiUrl.substringAfterLast("/")

    init {
        fetchEvents()
    }

    fun fetchEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val response = com.streambd.iptv.data.api.RetrofitClient.apiService.getLiveEvents(
                    fullApiUrl
                )
                _events.value = response.events
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getLiveEventsDirectly(): List<SportzfyEvent> {
        return try {
            val response = com.streambd.iptv.data.api.RetrofitClient.apiService.getLiveEvents(fullApiUrl)
            response.events
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getStreamsForEvent(eventId: String): List<SportzfyStream> {
        return try {
            val streamUrl = "$baseDirectory/${eventId}/$fileName"
            val response = com.streambd.iptv.data.api.RetrofitClient.apiService.getLiveEventStreams(
                streamUrl
            )
            response.streams
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
