package com.streambd.iptv.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.ui.components.EqualizerBars
import com.streambd.iptv.ui.components.FocusSurface
import com.streambd.iptv.ui.components.LiveBadge
import com.streambd.iptv.ui.theme.PrimeGold

// Semantic quality colours — matches SourceSelectionScreen.
private val Quality4k = PrimeGold
private val QualityFhd = Color(0xFF4CAF50)
private val QualityCam = Color(0xFFFF5722)

private fun qualityColorFor(quality: String, fallback: Color): Color {
    val q = quality.lowercase()
    return when {
        q.contains("4k") || q.contains("2160") -> Quality4k
        q.contains("1080") || q.contains("fhd") -> QualityFhd
        q.contains("720") || q.contains("hd") -> fallback
        q.contains("cam") -> QualityCam
        else -> fallback
    }
}

@Composable
fun PortraitSourcePane(
    streams: List<StreamSource>,
    currentStream: StreamSource?,
    mediaTitle: String?,
    mediaSubtitle: String?,
    onSelectStream: (StreamSource) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val currentIndex = remember(currentStream, streams) {
        if (currentStream == null) -1
        else streams.indexOfFirst {
            it.url == currentStream.url &&
                it.provider == currentStream.provider &&
                it.quality == currentStream.quality
        }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)
    )
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    Column(modifier = modifier.background(scheme.background)) {
        // ── Now playing ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaTitle ?: "Now Playing",
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!mediaSubtitle.isNullOrBlank()) {
                    Text(
                        text = mediaSubtitle,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))

        Text(
            text = "Sources  •  ${streams.size}",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )

        if (streams.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No sources available",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Go back and refresh sources.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = streams,
                    key = { index, s -> "$index|${s.provider}|${s.quality}|${s.url}" }
                ) { index, source ->
                    SourceRow(
                        source = source,
                        isCurrent = index == currentIndex,
                        onClick = { onSelectStream(source) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: StreamSource,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val qualityColor = qualityColorFor(source.quality, scheme.primary)

    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = if (isCurrent) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        focusedContainerColor = scheme.surfaceVariant,
        restingBorderColor = if (isCurrent) scheme.primary.copy(alpha = 0.6f) else Color.Transparent,
        focusScale = 1f,
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true
    ) { _ ->
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Provider icon ──
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(scheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))

            // ── Provider + quality ──
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.provider.ifBlank { "Unknown" },
                    color = if (isCurrent) scheme.primary else scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.quality,
                        color = qualityColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!source.type.isNullOrEmpty()) {
                        Text(
                            text = " • ${source.type.uppercase()}",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // ── Playing badge — now with an animated equalizer ──
            if (isCurrent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EqualizerBars(
                        color = scheme.primary,
                        barCount = 3,
                        barWidth = 2.5.dp,
                        maxHeight = 12.dp,
                        minHeight = 3.dp
                    )
                    Text(
                        text = "Playing",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PortraitStreamInfoPane(
    stream: StreamSource,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val qualityColor = qualityColorFor(stream.quality, scheme.primary)

    Column(
        modifier = modifier
            .background(scheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveBadge()
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Stream",
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stream.provider.ifBlank { "Custom stream" },
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stream.quality,
                color = qualityColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stream.url,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "To play a different URL, go back and edit the stream address.",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
