package com.myselfhridoy.streambdplayer.data.models

import kotlinx.serialization.Serializable

@Serializable
data class DrmConfig(
    val type: String? = null,
    val licenseServer: String? = null,
    val rawKeyPair: String? = null
)
