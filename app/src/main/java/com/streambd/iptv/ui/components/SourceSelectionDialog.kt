package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.GrayText
import kotlinx.coroutines.delay

/**
 * Source picker used from the live player. Rebuilt on top of the shared
 * `PremiumDialogScaffold` so all dialogs in the app feel the same.
 *
 * Signature kept identical to the previous version so no call-site change is needed.
 */
@Composable
fun SourceSelectionDialog(
    sources: Map<String, String>,
    currentSourceUrl: String,
    onSourceSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val items = remember(sources) {
        sources.entries.map { it.key to it.value }
    }
    val selectedIndex = remember(items, currentSourceUrl) {
        items.indexOfFirst { it.second == currentSourceUrl }
    }

    PremiumDialogScaffold(
        title = "Select Source",
        subtitle = if (items.isEmpty()) "No alternative sources" else "${items.size} source${if (items.size > 1) "s" else ""}",
        onDismiss = onDismiss,
        scrollContent = false
    ) {
        if (items.isEmpty()) {
            Text(
                text = "This channel has no additional sources.",
                color = GrayText,
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
                .heightIn(max = 360.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.second }
            ) { index, (name, url) ->
                val isSelected = url == currentSourceUrl
                PremiumOptionRow(
    title = name.ifBlank { "Source ${index + 1}" },
    subtitle = null,   // URL লুকানো
    selected = isSelected,
    focusRequester = if (index == (if (selectedIndex >= 0) selectedIndex else 0)) listFocus else null,
    onClick = {
        onSourceSelected(name, url)
        onDismiss()
    }
)
            }
        }
    }
}
