package com.streambd.iptv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.StreamedMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request

class LiveEventsViewModel : ViewModel() {
    private val _events = MutableStateFlow<List<StreamedMatch>>(emptyList())
    val events: StateFlow<List<StreamedMatch>> = _events

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
                    .url("https://streamed.pk/api/matches/all")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    val parsedResponse = json.decodeFromString<List<StreamedMatch>>(body)
                    _events.value = parsedResponse
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
