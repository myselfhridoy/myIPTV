package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.AppTheme

/**
 * "Groups" button + up to 10 group chips. Hidden when there is at most
 * one group. Shared by Live categories and Playlist channels.
 */
@Composable
fun GroupChipsRow(
    groups: List<String>,
    selectedGroup: String,
    onSelect: (String) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val valid = groups.filter { it.isNotBlank() }
    if (valid.size <= 1) return

    val d = AppTheme.dimens
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .restoreFocus(),
        contentPadding = PaddingValues(horizontal = d.screenPaddingH, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(d.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "groups_button") {
            CategoryChip(
                label = "Groups",
                selected = false,
                icon = Icons.AutoMirrored.Filled.List,
                onClick = onShowAll
            )
        }
        itemsIndexed(
            items = valid.take(10),
            key = { index, group -> "chip_${index}_$group" }
        ) { _, group ->
            val isSelected = selectedGroup == group
            CategoryChip(
                label = group,
                selected = isSelected,
                icon = if (isSelected) Icons.Default.Check else null,
                onClick = { onSelect(group) }
            )
        }
    }
}

/**
 * Full group list in a dialog. Opens scrolled to the current group and
 * (on TV) puts focus on it.
 */
@Composable
fun GroupPickerDialog(
    groups: List<String>,
    selectedGroup: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIndex = groups.indexOf(selectedGroup).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    PremiumDialogScaffold(
        title = "Select Group",
        subtitle = "${groups.size} group${if (groups.size > 1) "s" else ""}",
        onDismiss = onDismiss,
        scrollContent = false
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(
                items = groups,
                key = { index, group -> "modal_${index}_$group" }
            ) { index, group ->
                PremiumOptionRow(
                    title = group,
                    selected = selectedGroup == group,
                    modifier = if (index == selectedIndex) Modifier.initialFocus() else Modifier,
                    onClick = { onSelect(group) }
                )
            }
        }
    }
}
