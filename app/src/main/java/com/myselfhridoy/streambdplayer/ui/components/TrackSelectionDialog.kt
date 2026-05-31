package com.myselfhridoy.streambdplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavySurface
import com.myselfhridoy.streambdplayer.ui.theme.TealAccent
import com.myselfhridoy.streambdplayer.ui.theme.WhiteText

@Composable
fun TrackSelectionDialog(
    trackSelector: DefaultTrackSelector,
    currentTracks: Tracks,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(C.TRACK_TYPE_VIDEO) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C3240)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222834))
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
                    // Auto Option
                    val hasOverride = parameters.overrides.keys.any { it.type == selectedTab }
                    val isAuto = trackGroups.isEmpty() || !hasOverride
                    item {
                        TrackOptionItem(
                            title = "Auto",
                            isSelected = isAuto,
                            onClick = {
                                trackSelector.setParameters(
                                    parameters.buildUpon().clearOverridesOfType(selectedTab).build()
                                )
                                onDismiss()
                            }
                        )
                    }

                    // Dynamic Options
                    trackGroups.forEach { group ->
                        val trackGroup = group.mediaTrackGroup
                        items(trackGroup.length) { trackIndex ->
                            val format = trackGroup.getFormat(trackIndex)
                            val isSelected = !isAuto && group.isTrackSelected(trackIndex)
                            val title = formatTrackName(format, selectedTab)

                            TrackOptionItem(
                                title = title,
                                isSelected = isSelected,
                                onClick = {
                                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                                    trackSelector.setParameters(
                                        trackSelector.buildUponParameters()
                                            .clearOverridesOfType(selectedTab)
                                            .addOverride(override)
                                            .build()
                                    )
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = WhiteText, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = onDismiss) {
                        Text("OK", color = WhiteText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabItem(title: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) TealAccent else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TrackOptionItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = WhiteText,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = TealAccent, unselectedColor = Color.White)
        )
    }
}

private fun formatTrackName(format: Format, trackType: Int): String {
    return when (trackType) {
        C.TRACK_TYPE_VIDEO -> {
            if (format.width > 0 && format.height > 0) {
                var name = "${format.width} × ${format.height}"
                if (format.bitrate > 0) {
                    name += ", ${format.bitrate / 1000000.0} Mbps"
                }
                name
            } else {
                "Unknown"
            }
        }
        C.TRACK_TYPE_AUDIO -> {
            format.language ?: "Unknown Language"
        }
        else -> "Unknown"
    }
}
