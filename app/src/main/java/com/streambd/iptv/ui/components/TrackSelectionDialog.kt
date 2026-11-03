package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val title: String,
    val subtitle: String?,
    val badge: String?,
    val selected: Boolean
)

/**
 * Playback settings dialog: Video / Audio / Subtitles.
 *
 * Takes the [Player] itself (not a snapshot), so the list stays live.
 * Selection is applied through `player.trackSelectionParameters`.
 */
@Composable
fun TrackSelectionDialog(
    player: Player,
    onDismiss: () -> Unit
) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var params by remember { mutableStateOf(player.trackSelectionParameters) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) { tracks = newTracks }
            override fun onTrackSelectionParametersChanged(p: TrackSelectionParameters) {
                params = p
            }
        }
        // Catch anything that changed between first composition and registration.
        tracks = player.currentTracks
        params = player.trackSelectionParameters
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val availableTabs = listOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT)
        .filter { type -> tracks.groups.any { it.type == type } }

    var selectedTab by remember { mutableStateOf(C.TRACK_TYPE_VIDEO) }
    val activeTab = if (selectedTab in availableTabs) selectedTab else availableTabs.firstOrNull()

    val options = remember(tracks, params, activeTab) {
        if (activeTab == null) emptyList() else buildOptions(activeTab, tracks, params)
    }

    PremiumDialogScaffold(
        title = "Playback settings",
        subtitle = "Video, audio and subtitles",
        onDismiss = onDismiss,
        scrollContent = false  // the LazyColumn below handles its own scroll
    ) {
        if (activeTab == null) {
            Text(
                text = "No tracks available yet. Try again once playback starts.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
            return@PremiumDialogScaffold
        }

        // ── Tabs ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableTabs.forEach { type ->
                TvFocusableItem(
                    onClick = { selectedTab = type },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    restBackground = if (type == activeTab)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when (type) {
                            C.TRACK_TYPE_VIDEO -> "Video"
                            C.TRACK_TYPE_AUDIO -> "Audio"
                            else -> "Subtitles"
                        },
                        color = if (type == activeTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val isText = activeTab == C.TRACK_TYPE_TEXT
        val defaultSelected = if (isText) options.none { it.selected }
        else params.overrides.values.none { it.type == activeTab }

        // FIX: guard against -1 when nothing is selected (edge case with unsupported overrides).
        val selectedIndex = options.indexOfFirst { it.selected }
        val focusIndex = if (defaultSelected) 0
        else (if (selectedIndex >= 0) selectedIndex + 1 else 0)

        val listState = rememberLazyListState()
        val listFocus = remember { FocusRequester() }

        // Put focus on the current choice once. Re-run when the tab changes.
        LaunchedEffect(activeTab) {
            delay(80)
            try {
                listState.scrollToItem(focusIndex)
                delay(40)
                listFocus.requestFocus()
            } catch (_: Exception) {}
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "default_$activeTab") {
                PremiumOptionRow(
                    title = if (isText) "Off" else "Auto",
                    subtitle = if (isText) null else "Let the player choose",
                    selected = defaultSelected,
                    focusRequester = if (focusIndex == 0) listFocus else null,
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(activeTab)
                            .setTrackTypeDisabled(activeTab, isText)
                            .build()
                        onDismiss()
                    }
                )
            }
            itemsIndexed(options, key = { i, _ -> "${activeTab}_$i" }) { i, option ->
                PremiumOptionRow(
                    title = option.title,
                    subtitle = option.subtitle,
                    badge = option.badge,
                    selected = option.selected,
                    focusRequester = if (focusIndex == i + 1) listFocus else null,
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(activeTab, false)
                            .setOverrideForType(
                                TrackSelectionOverride(option.group.mediaTrackGroup, option.index)
                            )
                            .build()
                        onDismiss()
                    }
                )
            }
        }
    }
}

private fun buildOptions(type: Int, tracks: Tracks, params: TrackSelectionParameters): List<TrackOption> {
    val typeDisabled = type in params.disabledTrackTypes
    val raw = mutableListOf<TrackOption>()

    tracks.groups.filter { it.type == type }.forEach { group ->
        val override = params.overrides[group.mediaTrackGroup]
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i, true)) continue
            val selected = if (type == C.TRACK_TYPE_TEXT) {
                !typeDisabled && group.isTrackSelected(i)
            } else {
                override?.trackIndices?.contains(i) == true
            }
            raw += describe(group, i, type, selected)
        }
    }

    // Same title twice (e.g. two English audio tracks) → add a number suffix.
    val counts = raw.groupingBy { it.title }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return raw.map { option ->
        if ((counts[option.title] ?: 0) > 1) {
            val n = (seen[option.title] ?: 0) + 1
            seen[option.title] = n
            option.copy(title = "${option.title} $n")
        } else option
    }
}

private fun describe(group: Tracks.Group, index: Int, type: Int, selected: Boolean): TrackOption {
    val format = group.getTrackFormat(index)
    val language = languageName(format)
    val label = format.label?.takeIf { it.isNotBlank() }
    return when (type) {
        C.TRACK_TYPE_VIDEO -> {
            val hasSize = format.width > 0 && format.height > 0
            val parts = listOfNotNull(
                format.frameRate.takeIf { it > 0f }?.let { "${it.roundToInt()} fps" },
                format.bitrate.takeIf { it > 0 }
                    ?.let { String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0) },
                codecName(format.sampleMimeType)
            )
            TrackOption(
                group, index,
                title = if (hasSize) "${format.width} × ${format.height}" else "Video",
                subtitle = parts.joinToString("  •  ").ifEmpty { null },
                badge = if (hasSize) qualityBadge(format.height) else null,
                selected = selected
            )
        }
        C.TRACK_TYPE_AUDIO -> {
            val title = language ?: label ?: "Audio"
            val parts = listOfNotNull(
                label?.takeIf { it != title },
                codecName(format.sampleMimeType),
                format.bitrate.takeIf { it > 0 }
                    ?.let { String.format(Locale.US, "%.0f kbps", it / 1000.0) }
            )
            TrackOption(
                group, index,
                title = title,
                subtitle = parts.joinToString("  •  ").ifEmpty { null },
                badge = channelBadge(format.channelCount),
                selected = selected
            )
        }
        else -> {
            val title = language ?: label ?: "Subtitle"
            TrackOption(
                group, index,
                title = title,
                subtitle = label?.takeIf { it != title },
                badge = null,
                selected = selected
            )
        }
    }
}

private fun languageName(format: Format): String? {
    val lang = format.language
    if (lang.isNullOrBlank() || lang == C.LANGUAGE_UNDETERMINED) return null
    // Handles common 3-letter codes that Locale.forLanguageTag misses.
    val normalized = when (lang.lowercase()) {
        "eng" -> "en"
        "ben" -> "bn"
        "hin" -> "hi"
        "ara" -> "ar"
        "spa" -> "es"
        "fra", "fre" -> "fr"
        "deu", "ger" -> "de"
        "jpn" -> "ja"
        "kor" -> "ko"
        "zho", "chi" -> "zh"
        else -> lang
    }
    val name = Locale.forLanguageTag(normalized).getDisplayLanguage(Locale.getDefault())
    return name.takeIf { it.isNotBlank() } ?: lang
}

private fun qualityBadge(height: Int): String? = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "2K"
    height >= 1080 -> "FHD"
    height >= 720 -> "HD"
    height > 0 -> "SD"
    else -> null
}

private fun channelBadge(count: Int): String? = when (count) {
    Format.NO_VALUE, 0 -> null
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "${count}ch"
}

private fun codecName(mime: String?): String? = when (mime) {
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_H265 -> "HEVC"
    MimeTypes.VIDEO_AV1 -> "AV1"
    MimeTypes.VIDEO_VP9 -> "VP9"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_E_AC3 -> "E-AC3"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_OPUS -> "Opus"
    else -> null
}

