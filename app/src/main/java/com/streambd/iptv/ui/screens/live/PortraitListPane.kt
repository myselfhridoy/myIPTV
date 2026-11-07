package com.streambd.iptv.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
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
import com.streambd.iptv.ui.components.FocusSurface
import com.streambd.iptv.ui.components.LiveBadge
import com.streambd.iptv.ui.components.RemoteImage
import com.streambd.iptv.ui.viewmodels.PlayerListEntry

/**
 * Below the video in the portrait player: what is playing now, then a list to
 * switch to. The list is generic ([PlayerListEntry]) so the same pane serves
 * playlist channels, live events, and later VOD sources.
 * Phones / tablets only (LazyColumn is fine without a D-pad).
 */
@Composable
fun PortraitListPane(
    heading: String,
    entries: List<PlayerListEntry>,
    currentKey: String?,
    fallbackTitle: String,
    fallbackSubtitle: String?,
    onSelect: (PlayerListEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val currentIndex = remember(currentKey, entries) {
        entries.indexOfFirst { it.key == currentKey }
    }
    val current = entries.getOrNull(currentIndex)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)
    )
    // Follow the playing item when it changes (next/prev buttons, swipe, list tap).
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
            if (current?.live != false) {
                LiveBadge()
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current?.title ?: fallbackTitle,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = current?.subtitle ?: fallbackSubtitle
                if (!sub.isNullOrBlank()) {
                    Text(
                        text = sub,
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
            text = "$heading  •  ${entries.size}",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(
                items = entries,
                key = { index, entry -> "$index|${entry.key}" }
            ) { index, entry ->
                ListRow(
                    entry = entry,
                    isCurrent = index == currentIndex,
                    onClick = { onSelect(entry) }
                )
            }
        }
    }
}

@Composable
private fun ListRow(entry: PlayerListEntry, isCurrent: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
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
            Row(
                modifier = Modifier
                    .size(width = 64.dp, height = 40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color.White.copy(alpha = 0.16f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteImage(
                    url = entry.imageUrl,
                    label = entry.title,
                    describe = false,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = if (isCurrent) scheme.primary else scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.subtitle.isNullOrBlank()) {
                    Text(
                        text = entry.subtitle,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isCurrent) {
                Text(
                    text = "Playing",
                    color = scheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            } else if (entry.live) {
                Text(
                    text = "LIVE",
                    color = Color(0xFFE53935),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
