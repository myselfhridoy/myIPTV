package com.streambd.iptv.ui.screens.events

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.ui.graphics.vector.ImageVector
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.DrmConfig
import com.streambd.iptv.data.models.SportzfyEvent
import com.streambd.iptv.data.models.SportzfyStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// Pure helpers — no Compose here, so they are easy to test/reuse.
// Logic is copied 1:1 from the old LiveEventsScreen.
// ─────────────────────────────────────────────────────────────

internal fun parseEventDate(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val cleanStr = dateStr.replace(Regex("([+-]\\d\\d):(\\d\\d)$"), "$1$2")
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(cleanStr)
    } catch (_: Exception) {
        null
    }
}

internal fun sportIcon(sport: String?): ImageVector {
    val s = sport?.lowercase() ?: ""
    return when {
        s.contains("cricket") -> Icons.Default.SportsCricket
        s.contains("football") || s.contains("soccer") -> Icons.Default.SportsSoccer
        s.contains("motorsport") || s.contains("motor") || s.contains("racing") ->
            Icons.Default.SportsMotorsports
        s.contains("wrestling") || s.contains("wwe") || s.contains("mma") || s.contains("ufc") ->
            Icons.Default.SportsMma
        s.contains("basketball") -> Icons.Default.SportsBasketball
        s.contains("tennis") -> Icons.Default.SportsTennis
        s.contains("volleyball") -> Icons.Default.SportsVolleyball
        s.contains("hockey") -> Icons.Default.SportsHockey
        s.contains("baseball") -> Icons.Default.SportsBaseball
        s.contains("kabaddi") -> Icons.Default.SportsKabaddi
        s.contains("esport") || s.contains("gaming") -> Icons.Default.SportsEsports
        else -> Icons.Default.SportsSoccer
    }
}

/** Status filter row: All / Live / Today / Upcoming / Recent. */
internal enum class EventStatus(val label: String, val icon: ImageVector) {
    All("All", Icons.Default.Check),
    Live("Live", Icons.Default.Podcasts),
    Today("Today", Icons.Default.Schedule),
    Upcoming("Upcoming", Icons.Default.Schedule),
    Recent("Recent", Icons.Default.Schedule);

    fun apply(events: List<SportzfyEvent>, now: Long = System.currentTimeMillis()): List<SportzfyEvent> =
        when (this) {
            All -> events
            Live -> events.filter { it.is_live == 1 }
            Today -> {
                val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
                val today = fmt.format(Date(now))
                events.filter {
                    val d = parseEventDate(it.starts_at)
                    d != null && fmt.format(d) == today
                }
            }
            Upcoming -> events
                .filter {
                    it.is_live == 0 &&
                        (parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE) > now
                }
                .sortedBy { parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE }
            Recent -> events
                .filter {
                    it.is_live == 0 &&
                        (parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE) <= now
                }
                .sortedByDescending { parseEventDate(it.starts_at)?.time ?: 0L }
        }
}

/** Sport + status + text-search filtering (same order as before). */
internal fun filterEvents(
    events: List<SportzfyEvent>,
    category: String,
    status: EventStatus,
    query: String
): List<SportzfyEvent> {
    var list = if (category == "All") events else events.filter { it.sport == category }
    list = status.apply(list)
    if (query.isNotBlank()) {
        list = list.filter {
            (it.team_a_name?.contains(query, ignoreCase = true) == true) ||
                (it.team_b_name?.contains(query, ignoreCase = true) == true) ||
                (it.league?.contains(query, ignoreCase = true) == true)
        }
    }
    return list
}

// ─────────────────────────────────────────────────────────────
// Stream → Channel hand-off (was written out twice in the screen)
// ─────────────────────────────────────────────────────────────

/** Stream URL, with the ClearKey DRM suffix the player expects. */
private fun streamUrlWithDrm(stream: SportzfyStream): String {
    var url = stream.stream_url ?: ""
    if (stream.drm_key != null && stream.drm_kid != null) {
        url += "|drm-type=clearkey|drm-key=${stream.drm_kid}:${stream.drm_key}"
    }
    return url
}

/**
 * Every source of the event, keyed by a unique display name.
 *
 * ORDER MATTERS: the live player starts with the FIRST entry of this map
 * (`Channel.primarySourceUrl()` = `sources.values.first()`), not with
 * `Channel.url`. So the stream the user picked is placed first; the other
 * sources follow in their original order and stay available in the
 * player's source switcher.
 */
private fun buildSources(
    streams: List<SportzfyStream>,
    selectedIndex: Int
): MutableMap<String, String> {
    val sourcesMap = mutableMapOf<String, String>()
    var selectedName: String? = null
    streams.forEachIndexed { idx, s ->
        var name = s.label.takeIf { !it.isNullOrBlank() } ?: "Source ${idx + 1}"
        if (sourcesMap.containsKey(name)) name = "$name (${idx + 1})"
        sourcesMap[name] = streamUrlWithDrm(s)
        if (idx == selectedIndex) selectedName = name
    }

    val chosen = selectedName ?: return sourcesMap
    val ordered = mutableMapOf<String, String>()
    ordered[chosen] = sourcesMap.getValue(chosen)
    sourcesMap.forEach { (name, url) -> if (name != chosen) ordered[name] = url }
    return ordered
}

/** The Channel handed to the live player for [stream] of [event]. */
internal fun buildEventChannel(
    event: SportzfyEvent,
    stream: SportzfyStream,
    allStreams: List<SportzfyStream>
): Channel = Channel(
    name = event.league ?: "Live Event",
    url = streamUrlWithDrm(stream),
    group = event.sport ?: "Live",
    isLiveEvent = true,
    drm = if (stream.drm_key != null) DrmConfig(
        type = "clearkey",
        rawKeyPair = "${stream.drm_kid}:${stream.drm_key}"
    ) else null,
    sources = buildSources(allStreams, allStreams.indexOf(stream).coerceAtLeast(0))
)
