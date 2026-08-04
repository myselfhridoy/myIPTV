package com.streambd.iptv.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TvModeChannel(
    val id: Int = 0,
    val name: String,
    val image: String = "",
    @SerialName("channel_no") val channelNo: String,
    val resolution: String = "",
    val keywords: List<String> = emptyList(),
    @SerialName("group-title") val groupTitle: String = "",
    @SerialName("tvg-logo") val tvgLogo: String = ""
)
