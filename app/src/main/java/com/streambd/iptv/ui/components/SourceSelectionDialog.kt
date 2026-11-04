package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Source picker shown from the live player.
 *
 * FIX: The old version used the source URL as the LazyColumn key. IPTV
 * channels frequently have two or more named sources that share the same
 * URL (e.g. "HD" and "FHD" pointing to the same CDN edge), which made
 * Compose throw a duplicate-key crash the moment the dialog opened.
 * Now the key is index-based, which is guaranteed unique. Items never
 * reorder inside a single dialog session, so index keys are safe here.
 */
@Composable
fun SourceSelectionDialog(
    sources: Map<String, String>,
    currentSourceUrl: String,
    onSourceSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    // Snapshot the map once; it doesn't change while the dialog is open.
    val items = remember(sources) {
        sources.entries.map { it.key to it.value }
    }
    val selectedIndex = remember(items, currentSourceUrl) {
        items.indexOfFirst { it.second == currentSourceUrl }
    }

    PremiumDialogScaffold(
        title = "Select Source",
        subtitle = if (items.isEmpty())
            "No alternative sources"
        else
            "${items.size} source${if (items.size > 1) "s" else ""}",
        onDismiss = onDismiss,
        scrollContent = false
    ) {
        if (items.isEmpty()) {
            Text(
                text = "This channel has no additional sources.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
            return@PremiumDialogScaffold
        }

        val listFocus = remember { FocusRequester() }
        LaunchedEffect(items) {
            delay(60)
            try { listFocus.requestFocus() } catch (_: Exception) {}
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = rememberDialogListMaxHeight(360.dp)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(rememberDialogRowGap())
        ) {
            // ── FIX: index-based key is guaranteed unique even when two
            //         different source names share the same URL. ──
            itemsIndexed(
                items = items,
                key = { index, item -> "$index|${item.first}" }
            ) { index, (name, url) ->
                val isSelected = url == currentSourceUrl
                val fallbackFocusIndex = if (selectedIndex >= 0) selectedIndex else 0
                PremiumOptionRow(
                    title = name.ifBlank { "Source ${index + 1}" },
                    subtitle = null,
                    selected = isSelected,
                    focusRequester = if (index == fallbackFocusIndex) listFocus else null,
                    onClick = {
                        onSourceSelected(name, url)
                        onDismiss()
                    }
                )
            }
        }
    }
}

