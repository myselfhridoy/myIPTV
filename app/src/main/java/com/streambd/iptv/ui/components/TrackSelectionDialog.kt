package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.streambd.iptv.ui.theme.DarkNavySurface
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.theme.WhiteText

@Composable
fun TrackSelectionDialog(
    trackSelector: DefaultTrackSelector,
    currentTracks: Tracks,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(C.TRACK_TYPE_VIDEO) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tabs - Video, Audio, Subtitle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF1A1F2E), Color(0xFF222834))
                            )
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabItem(
                        title = "VIDEO",
                        isSelected = selectedTab == C.TRACK_TYPE_VIDEO,
                        onClick = { selectedTab = C.TRACK_TYPE_VIDEO },
                        modifier = Modifier.weight(1f)
                    )
                    TabItem(
                        title = "AUDIO",
                        isSelected = selectedTab == C.TRACK_TYPE_AUDIO,
                        onClick = { selectedTab = C.TRACK_TYPE_AUDIO },
                        modifier = Modifier.weight(1f)
                    )
                    TabItem(
                        title = "SUBTITLE",
                        isSelected = selectedTab == C.TRACK_TYPE_TEXT,
                        onClick = { selectedTab = C.TRACK_TYPE_TEXT },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val trackGroups = currentTracks.groups.filter { it.type == selectedTab }
                val parameters = trackSelector.parameters

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    // Auto / Off Option
                    val hasOverride = parameters.overrides.keys.any { it.type == selectedTab }
                    val isAuto = trackGroups.isEmpty() || !hasOverride
                    item {
                        TrackOptionItem(
                            title = if (selectedTab == C.TRACK_TYPE_TEXT) "Off" else "Auto",
                            isSelected = isAuto,
                            onClick = {
                                if (selectedTab == C.TRACK_TYPE_TEXT) {
                                    // Disable subtitles
                                    trackSelector.setParameters(
                                        parameters.buildUpon()
                                            .clearOverridesOfType(selectedTab)
                                            .setRendererDisabled(getRendererIndex(currentTracks, selectedTab), true)
                                            .build()
                                    )
                                } else {
                                    trackSelector.setParameters(
                                        parameters.buildUpon().clearOverridesOfType(selectedTab).build()
                                    )
                                }
                                onDismiss()
                            }
                        )
                    }

                    // Dynamic Options
                    trackGroups.forEach { group ->
                        val trackGroup = group.mediaTrackGroup
                        items(trackGroup.length) { trackIndex ->
                            val format = trackGroup.getFormat(trackIndex)
                            val override = parameters.overrides[trackGroup]
                            val isSelected = !isAuto && override != null && override.trackIndices.contains(trackIndex)
                            val title = formatTrackName(format, selectedTab)

                            TrackOptionItem(
                                title = title,
                                isSelected = isSelected,
                                onClick = {
                                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                                    trackSelector.setParameters(
                                        trackSelector.buildUponParameters()
                                            .clearOverridesOfType(selectedTab)
                                            .setRendererDisabled(getRendererIndex(currentTracks, selectedTab), false)
                                            .addOverride(override)
                                            .build()
                                    )
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    var closeFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .onFocusChanged { closeFocused = it.isFocused }
                            .border(
                                width = if (closeFocused) 2.dp else 0.dp,
                                color = if (closeFocused) TealAccent else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Text("CLOSE", color = TealAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) TealAccent.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) TealAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) TealAccent else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun TrackOptionItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                color = if (isFocused) TealAccent.copy(alpha = 0.08f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) TealAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (isSelected) TealAccent else WhiteText,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = TealAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatTrackName(format: Format, trackType: Int): String {
    return when (trackType) {
        C.TRACK_TYPE_VIDEO -> {
            if (format.width > 0 && format.height > 0) {
                var name = "${format.width} × ${format.height}"
                if (format.bitrate > 0) {
                    name += ", ${String.format("%.1f", format.bitrate / 1000000.0)} Mbps"
                }
                name
            } else {
                "Unknown"
            }
        }
        C.TRACK_TYPE_AUDIO -> {
            val lang = format.language?.let { java.util.Locale(it).displayLanguage } ?: "Unknown"
            val channels = when (format.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
            }
            if (channels.isNotEmpty()) "$lang ($channels)" else lang
        }
        C.TRACK_TYPE_TEXT -> {
            val lang = format.language?.let { java.util.Locale(it).displayLanguage } ?: "Unknown"
            val label = format.label
            if (label != null && label != lang) "$lang — $label" else lang
        }
        else -> "Unknown"
    }
}

private fun getRendererIndex(tracks: Tracks, trackType: Int): Int {
    // Find the renderer index for the given track type
    var rendererIndex = 0
    for (group in tracks.groups) {
        if (group.type == trackType) return rendererIndex
        rendererIndex++
    }
    return rendererIndex
}
