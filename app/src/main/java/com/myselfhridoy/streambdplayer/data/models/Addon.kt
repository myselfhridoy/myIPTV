package com.myselfhridoy.streambdplayer.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Addon(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true
)

@Serializable
data class StreamSource(
    val url: String,
    val quality: String = "HD",
    val provider: String,
    val type: String? = null,
    val headers: Map<String, String>? = null,
    val subtitles: List<Subtitle>? = null
)

@Serializable
data class Subtitle(
    val language: String,
    val url: String,
    val label: String? = null
)
