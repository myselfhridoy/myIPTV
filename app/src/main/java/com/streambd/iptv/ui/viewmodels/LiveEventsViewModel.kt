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

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Mock JSON provided by user
    private val mockEventsJson = """
    {
        "ok": true,
        "source": "spweb",
        "count": 19,
        "events": [
            {
                "_kind": "upstream",
                "id": "FIFA World Cup",
                "parent": "FIFA World Cup",
                "sport": "Football",
                "league": "FIFA World Cup Opening Ceremony, 2026",
                "team_a_name": "FIFA World Cup Opening",
                "team_a_logo": "https://www.edigitalagency.com.au/wp-content/uploads/new-FIFA-World-Cup-2026-logo-white-PNG-large-size.png",
                "team_b_name": "Ceremony",
                "team_b_logo": "https://www.edigitalagency.com.au/wp-content/uploads/new-FIFA-World-Cup-2026-logo-white-PNG-large-size.png",
                "starts_at": "2026-06-12T17:00:00.000Z",
                "is_live": 1,
                "league_icon": "https://www.edigitalagency.com.au/wp-content/uploads/new-FIFA-World-Cup-2026-logo-white-PNG-large-size.png"
            },
            {
                "_kind": "upstream",
                "id": "BANvsAUS",
                "parent": "BANvsAUS",
                "sport": "Cricket",
                "league": "Australia in Bangladesh 2026",
                "team_a_name": "Bangladesh",
                "team_a_logo": "https://access-control-center-prod.freevision.live/ss/team/187744.png",
                "team_b_name": "Australia",
                "team_b_logo": "https://access-control-center-prod.freevision.live/ss/team/187746.png",
                "starts_at": "2026-06-14T05:00:00.000Z",
                "is_live": 0,
                "league_icon": "https://access-control-center-prod.freevision.live/ss/tournament/11190.png"
            },
            {
                "_kind": "upstream",
                "id": "EUT20",
                "parent": "EUT20",
                "sport": "Cricket",
                "league": "EUT20 Belgium",
                "team_a_name": "EUT20",
                "team_a_logo": "https://upload.wikimedia.org/wikipedia/en/d/d4/EUT20_Belgium.png",
                "team_b_name": "EUT20",
                "team_b_logo": "https://upload.wikimedia.org/wikipedia/en/d/d4/EUT20_Belgium.png",
                "starts_at": "2026-06-12T10:30:00.000Z",
                "is_live": 0,
                "league_icon": "https://upload.wikimedia.org/wikipedia/en/d/d4/EUT20_Belgium.png"
            },
            {
                "_kind": "upstream",
                "id": "F1",
                "parent": "F1",
                "sport": "Motorsports",
                "league": "Formula 1",
                "team_a_name": "Formula 1",
                "team_a_logo": "https://logodownload.org/wp-content/uploads/2016/11/formula-1-logo-5-2.png",
                "team_b_name": "Formula 1",
                "team_b_logo": "https://logodownload.org/wp-content/uploads/2016/11/formula-1-logo-5-2.png",
                "starts_at": "2026-06-12T15:00:00.000Z",
                "is_live": 1,
                "league_icon": "https://logodownload.org/wp-content/uploads/2016/11/formula-1-logo-5-2.png"
            },
            {
                "_kind": "upstream",
                "id": "Monday Night Raw",
                "parent": "Monday Night Raw",
                "sport": "Wrestling",
                "league": "Monday Night Raw",
                "team_a_name": "Monday Night Raw",
                "team_a_logo": "https://upload.wikimedia.org/wikipedia/commons/8/86/WWE_RAW_Logo_2025.svg",
                "team_b_name": "Monday Night Raw",
                "team_b_logo": "https://upload.wikimedia.org/wikipedia/commons/8/86/WWE_RAW_Logo_2025.svg",
                "starts_at": "2026-06-16T00:00:00.000Z",
                "is_live": 0,
                "league_icon": "https://www.wwe.com/themes/custom/wwe_theme/logo.svg"
            }
        ]
    }
    """.trimIndent()

    val mockStreamResponseJson = """
    {
        "ok": true,
        "source": "spweb",
        "parent": "F1",
        "streams": [
            {
                "id": "932",
                "label": "Server 1",
                "stream_type": "dash",
                "stream_url": "https://otte.cache.aiv-cdn.net/iad-nitro/live/clients/dash/enc/fdb3pubmek/out/v1/aefca6420f944a9482e117f315de535f/cenc.mpd",
                "drm_kid": "7e9239c1982d984a002df3ed049d0756",
                "drm_key": "1b8a17598129a3618535c8fb05f103fe",
                "sort_order": 0
            },
            {
                "id": "1879",
                "label": "Server 2 (HLS)",
                "stream_type": "hls",
                "stream_url": "https://live.kinescopecdn.net/on-air/5bf2c07e-3604-42cf-959a-67ed8914ffd5/68b6e4ec-e6ad-4cf5-bf52-b8979471a750/master.m3u8",
                "drm_kid": null,
                "drm_key": null,
                "sort_order": 2
            }
        ]
    }
    """.trimIndent()

    init {
        fetchEvents()
    }

    fun fetchEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            delay(800) // Simulate network delay
            try {
                val parsedResponse = json.decodeFromString<SportzfyApiResponse>(mockEventsJson)
                _events.value = parsedResponse.events
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getStreamsForEvent(eventId: String): List<SportzfyStream> {
        // Return mock streams
        delay(500)
        try {
            val response = json.decodeFromString<SportzfyStreamResponse>(mockStreamResponseJson)
            return response.streams
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }
}
