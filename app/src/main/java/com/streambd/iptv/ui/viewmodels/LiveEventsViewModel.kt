package com.streambd.iptv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.LiveEvent
import com.streambd.iptv.data.models.EventResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class LiveEventsViewModel : ViewModel() {
    private val _events = MutableStateFlow<List<LiveEvent>>(emptyList())
    val events: StateFlow<List<LiveEvent>> = _events

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    init {
        fetchEvents()
    }

    fun fetchEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val request = Request.Builder()
                    .url("https://nowstreams.top/api_proxy.php")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val parsedResponse = json.decodeFromString<EventResponse>(body)
                    _events.value = parsedResponse.matches
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
