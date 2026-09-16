package com.streambd.iptv.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val group: String = "",
    val userAgent: String? = null,
    val cookie: String? = null,
    val httpReferer: String? = null,
    val origin: String? = null,
    val httpHeaders: Map<String, String>? = null,
    val tokenUrl: String? = null,
    val tokenMatch: String? = null,
    val tokenReplace: String? = null,
    val tokenId: Int? = null,
    val drm: DrmConfig? = null,
    val isLiveEvent: Boolean = true,
    val sources: Map<String, String> = emptyMap()
)
