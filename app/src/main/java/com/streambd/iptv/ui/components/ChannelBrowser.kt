package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.ui.theme.AppTheme

/**
 * Shows a list of channels the way the app always has:
 *   • one group selected (or only one group exists) → a grid
 *   • "All" with several groups → one horizontal row per group
 *
 * Used by Live categories and Playlist channels, so both behave — and
 * will keep behaving — identically. Callers pass the channels already
 * filtered (search etc.) and handle what a tap does.
 * TV: the first channel takes focus automatically; rows remember focus.
 */
@Composable
fun ChannelBrowser(
    channels: List<Channel>,
    selectedGroup: String,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val d = AppTheme.dimens

    val grouped = remember(channels, selectedGroup) {
        if (selectedGroup != "All") mapOf(selectedGroup to channels)
        else channels.groupBy { it.group }
    }
    val useGrid = selectedGroup != "All" || grouped.size <= 1

    if (useGrid) {
        val groupName = if (grouped.size == 1) grouped.keys.first() else selectedGroup
        Column(modifier = modifier) {
            if (groupName.isNotBlank() && selectedGroup == "All") {
                SectionHeader(title = groupName)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = d.channelCardWidth),
                contentPadding = PaddingValues(horizontal = d.screenPaddingH, vertical = d.spaceMd),
                horizontalArrangement = Arrangement.spacedBy(d.gridGap),
                verticalArrangement = Arrangement.spacedBy(d.gridGap),
                modifier = Modifier.fillMaxSize()
            ) {
                // Index-prefixed key so identical name+url pairs don't crash.
                gridItemsIndexed(
                    items = channels,
                    key = { index, channel -> "$index|${channel.name}|${channel.url}" }
                ) { index, channel ->
                    ChannelCard(
                        name = channel.name,
                        logoUrl = channel.logo,
                        modifier = if (index == 0) Modifier.initialFocus() else Modifier,
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(d.spaceLg)
        ) {
            grouped.entries.forEachIndexed { groupIndex, (group, groupChannels) ->
                item(key = "grp_${groupIndex}_$group") {
                    Column {
                        if (group.isNotBlank()) SectionHeader(title = group)
                        LazyRow(
                            modifier = Modifier.restoreFocus(),
                            contentPadding = PaddingValues(
                                horizontal = d.screenPaddingH,
                                vertical = 6.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(d.gridGap)
                        ) {
                            itemsIndexed(
                                items = groupChannels,
                                key = { index, channel -> "$index|${channel.name}|${channel.url}" }
                            ) { index, channel ->
                                ChannelCard(
                                    name = channel.name,
                                    logoUrl = channel.logo,
                                    modifier = Modifier
                                        .width(d.channelCardWidth)
                                        .then(
                                            if (groupIndex == 0 && index == 0) Modifier.initialFocus()
                                            else Modifier
                                        ),
                                    onClick = { onChannelClick(channel) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
