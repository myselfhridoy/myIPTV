package com.myselfhridoy.streambdplayer.data.models

import kotlinx.serialization.Serializable

@Serializable
data class LiveCategoryResponse(
    val playlists: Map<String, PlaylistInfo>
)

@Serializable
data class PlaylistInfo(
    val url: String,
    val logo: String
)

data class LiveCategory(
    val name: String,
    val url: String,
    val logo: String,
    var channels: List<Channel> = emptyList()
)
