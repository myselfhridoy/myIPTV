package com.streambd.iptv.data.models

import kotlinx.serialization.Serializable

@Serializable
data class EventResponse(
    val matches: List<LiveEvent> = emptyList()
)

@Serializable
data class LiveEvent(
    val matchText: String? = null,
    val matchstr: String? = null,
    val time: String? = null,
    val league: String? = null,
    val sport: String? = null,
    val team1: String? = null,
    val team2: String? = null,
    val channel: String? = null,
    val important: Boolean? = false,
    val matchDate: String? = null,
    val channels: List<EventChannel> = emptyList(),
    val slug: String? = null,
    val startTimestamp: Long? = null,
    val duration: Int? = null
)

@Serializable
data class EventChannel(
    val name: String? = null,
    val oldLinks: List<String> = emptyList(),
    val number: Int? = null,
    val language: String? = null,
    val links: List<String> = emptyList()
)
