package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.AppTheme

/** Loading placeholder shaped like a [CategoryTile] grid. */
@Composable
fun CategoryGridSkeleton(
    minCellWidth: Dp,
    modifier: Modifier = Modifier,
    count: Int = 12
) {
    val d = AppTheme.dimens
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = d.spaceXl, vertical = d.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(d.spaceMd),
        verticalArrangement = Arrangement.spacedBy(d.spaceMd),
        userScrollEnabled = false
    ) {
        items(count) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}

/** Loading placeholder shaped like a [ChannelCard] grid. */
@Composable
fun ChannelGridSkeleton(
    minCellWidth: Dp,
    modifier: Modifier = Modifier,
    count: Int = 12
) {
    val d = AppTheme.dimens
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = d.screenPaddingH, vertical = d.spaceLg),
        horizontalArrangement = Arrangement.spacedBy(d.spaceMd),
        verticalArrangement = Arrangement.spacedBy(d.spaceMd),
        userScrollEnabled = false
    ) {
        items(count) {
            ChannelCardSkeleton()
        }
    }
}
