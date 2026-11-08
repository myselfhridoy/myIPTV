package com.streambd.iptv.ui.screens.events

import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.SportzfyEvent
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.PlayerListContext
import com.streambd.iptv.ui.viewmodels.PlayerListEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The list shown under the portrait player when a LIVE EVENT is playing:
 * live events first, then today's, then upcoming — not the last channel list.
 *
 * Tapping an entry fetches that event's streams, plays the first source
 * (the Sources button switches between them) and moves the highlight.
 */
internal fun buildEventPlayerContext(
    events: List<SportzfyEvent>,
    currentEventId: String?,
    eventsViewModel: LiveEventsViewModel,
    mainViewModel: MainViewModel
): PlayerListContext {
    val now = System.currentTimeMillis()
    val live = EventStatus.Live.apply(events, now)
    val today = EventStatus.Today.apply(events, now)
    val upcoming = EventStatus.Upcoming.apply(events, now).take(20)
    val ordered = (live + today + upcoming).distinctBy { it.id ?: it.hashCode().toString() }

    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val entries = ordered.map { e ->
        val title = listOfNotNull(e.team_a_name, e.team_b_name)
            .filter { it.isNotBlank() }
            .joinToString(" vs ")
            .ifBlank { e.league ?: "Live event" }
        val time = parseEventDate(e.starts_at)?.let { timeFormat.format(it) }
        PlayerListEntry(
            key = e.id ?: e.hashCode().toString(),
            title = title,
            subtitle = listOfNotNull(e.league, time).joinToString("  •  ").ifBlank { null },
            imageUrl = e.team_a_logo,
            live = e.is_live == 1
        )
    }

    fun make(currentKey: String): PlayerListContext = PlayerListContext(
        heading = "Live events",
        entries = entries,
        currentKey = currentKey
    ) { entry ->
        mainViewModel.viewModelScope.launch {
            val event = ordered.firstOrNull { (it.id ?: it.hashCode().toString()) == entry.key }
                ?: return@launch
            val streams = eventsViewModel.getStreamsForEvent(event.id ?: "")
            if (streams.isNotEmpty()) {
                val channel = buildEventChannel(event, streams.first(), streams)
                mainViewModel.setCurrentPlaylist(listOf(channel)) // also clears the old context
                mainViewModel.selectChannel(channel)
                mainViewModel.setPlayerListContext(make(entry.key))
            }
        }
    }
    return make(currentEventId ?: "")
}
