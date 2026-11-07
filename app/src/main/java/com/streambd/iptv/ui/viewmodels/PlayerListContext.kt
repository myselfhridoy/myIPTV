package com.streambd.iptv.ui.viewmodels

import androidx.compose.runtime.Immutable

/** One row in the portrait player's list. */
data class PlayerListEntry(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val live: Boolean = false
)

/**
 * What the list under the portrait video shows, when it is NOT simply
 * "the channels of the playlist you came from".
 *
 * Example: playing a live event shows the other live events (not the last
 * channel list). The screen that starts playback sets this; any screen that
 * sets a plain playlist (`setCurrentPlaylist`) clears it again.
 */
@Immutable
data class PlayerListContext(
    val heading: String,
    val entries: List<PlayerListEntry>,
    val currentKey: String,
    val onSelect: (PlayerListEntry) -> Unit
)
