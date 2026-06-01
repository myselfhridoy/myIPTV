package com.myselfhridoy.streambdplayer.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val url: String? = null,
    val filePath: String? = null,
    val type: String = "M3U", // M3U, XTREAM
    val createdAt: Long = System.currentTimeMillis()
)
