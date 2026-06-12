package com.streambd.iptv.data.models

import kotlinx.serialization.Serializable

@Serializable
data class StreamedMatch(
    val id: String? = null,
    val title: String? = null,
    val category: String? = null,
    val date: Long? = null,
    val poster: String? = null,
    val popular: Boolean? = false,
    val teams: StreamedTeams? = null,
    val sources: List<StreamedSource> = emptyList()
)

@Serializable
data class StreamedTeams(
    val home: StreamedTeam? = null,
    val away: StreamedTeam? = null
)

@Serializable
data class StreamedTeam(
    val name: String? = null,
    val badge: String? = null
)

@Serializable
data class StreamedSource(
    val source: String? = null,
    val id: String? = null
)
