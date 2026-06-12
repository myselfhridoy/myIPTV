package com.streambd.iptv.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SportzfyApiResponse(
    val ok: Boolean? = null,
    val source: String? = null,
    val count: Int? = null,
    val events: List<SportzfyEvent> = emptyList()
)

@Serializable
data class SportzfyEvent(
    @SerialName("_kind") val kind: String? = null,
    val id: String? = null,
    val parent: String? = null,
    val enc_parent: String? = null,
    val sport: String? = null,
    val league: String? = null,
    val round: String? = null,
    val team_a_name: String? = null,
    val team_a_logo: String? = null,
    val team_b_name: String? = null,
    val team_b_logo: String? = null,
    val starts_at: String? = null,
    val is_live: Int? = 0,
    val league_icon: String? = null,
    val priority: Int? = 0
)

@Serializable
data class SportzfyStreamResponse(
    val ok: Boolean? = null,
    val source: String? = null,
    val parent: String? = null,
    val event: SportzfyEvent? = null,
    val streams: List<SportzfyStream> = emptyList()
)

@Serializable
data class SportzfyStream(
    val id: String? = null,
    val label: String? = null,
    val stream_type: String? = null, // "dash", "hls", "embed"
    val stream_url: String? = null,
    val drm_kid: String? = null,
    val drm_key: String? = null,
    val sort_order: Int? = 0
)
