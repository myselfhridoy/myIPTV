package com.streambd.iptv.ui.screens.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.CategoryGridSkeleton
import com.streambd.iptv.ui.components.CategoryTile
import com.streambd.iptv.ui.components.ChannelCard
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.SearchableTopBar
import com.streambd.iptv.ui.components.initialFocus
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay

/**
 * Live TV home: category grid + (while searching) matching channels.
 *
 * ViewModel calls, navigation and search behaviour are unchanged; only the
 * UI is rebuilt on the shared components:
 *   SearchableTopBar · CategoryTile · ChannelCard · skeleton / EmptyState
 *
 * TV: the first category gets focus automatically.
 */
@Composable
fun LiveTvScreen(
    viewModel: MainViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    val categories by viewModel.liveCategories.collectAsState()
    val isLoading by viewModel.isLoadingLive.collectAsState()
    val allChannelsMap by viewModel.categoryChannels.collectAsState()
    val loadProgress by viewModel.searchLoadProgress.collectAsState()

    val device = LocalDeviceProfile.current
    val isTv = device.isTv
    val d = AppTheme.dimens
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }

    // Debounce only the filter input; blank clears immediately.
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            debouncedQuery = ""
        } else {
            delay(250)
            debouncedQuery = searchQuery
        }
    }

    val displayedCategories = remember(debouncedQuery, categories) {
        if (debouncedQuery.isBlank()) categories
        else categories.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
    }

    val matchedChannels = remember(debouncedQuery, allChannelsMap) {
        if (debouncedQuery.isBlank()) emptyList()
        else allChannelsMap.values.asSequence()
            .flatten()
            .filter { it.name.contains(debouncedQuery, ignoreCase = true) }
            .distinctBy { it.name + it.url }
            .take(100)
            .toList()
    }

    // Searching needs every category's channels loaded.
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) viewModel.loadAllCategoriesForSearch()
    }

    BackHandler(enabled = isSearchActive) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            searchQuery = ""
            isSearchActive = false
        }
    }

    // Categories are wide tiles; channels reuse the same grid.
    // 140dp guarantees TWO columns on every phone (a 360dp screen has 336dp
    // of usable width); tablets and TVs get more, wider columns.
    val cellMinWidth = when {
        isTv -> 220.dp
        device.isTablet -> 180.dp
        else -> 140.dp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        SearchableTopBar(
            title = "Live TV",
            searchActive = isSearchActive,
            onSearchActiveChange = { isSearchActive = it },
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            searchPlaceholder = "Search channels & categories…",
            // TV: Back opens the drawer, so no menu button and no search button.
            navigationIcon = if (onMenuClick != null && !isTv) Icons.Default.Menu else null,
            navigationDescription = "Menu",
            onNavigationClick = { onMenuClick?.invoke() },
            showSearchButton = !isTv,
            onSearchFieldFocusChanged = { isSearchFocused = it }
        )

        // ── Search hints + progress ──
        AnimatedVisibility(visible = isSearchActive, enter = fadeIn(), exit = fadeOut()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = d.screenPaddingH)
                    .padding(bottom = d.spaceSm)
            ) {
                if (debouncedQuery.isNotBlank()) {
                    Text(
                        text = "${displayedCategories.size} categories, " +
                            "${matchedChannels.size} channels for \"$debouncedQuery\"",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                loadProgress?.let { (done, total) ->
                    Text(
                        text = "Loading channels… $done/$total",
                        color = scheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        when {
            isLoading -> CategoryGridSkeleton(minCellWidth = cellMinWidth)

            displayedCategories.isEmpty() &&
                matchedChannels.isEmpty() &&
                debouncedQuery.isNotBlank() -> EmptyState(
                title = "No results found",
                message = "Nothing matches \"$debouncedQuery\"",
                icon = Icons.Default.Search
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = cellMinWidth),
                contentPadding = PaddingValues(horizontal = d.screenPaddingH, vertical = d.spaceMd),
                horizontalArrangement = Arrangement.spacedBy(d.gridGap),
                verticalArrangement = Arrangement.spacedBy(d.gridGap),
                modifier = Modifier.fillMaxSize()
            ) {
                if (debouncedQuery.isNotBlank() && displayedCategories.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_categories") {
                        GridSectionTitle("Categories")
                    }
                }

                itemsIndexed(
                    items = displayedCategories,
                    key = { index, category -> "cat_${index}_${category.name}" }
                ) { index, category ->
                    CategoryTile(
                        name = category.name,
                        logoUrl = category.logo,
                        // TV: first tile takes focus automatically (when not searching).
                        modifier = if (index == 0 && debouncedQuery.isBlank())
                            Modifier.initialFocus()
                        else Modifier,
                        onClick = {
                            viewModel.selectCategory(category)
                            navController.navigate(Routes.LIVE_CATEGORY_CHANNELS) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                if (debouncedQuery.isNotBlank() && matchedChannels.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_channels") {
                        GridSectionTitle("Channels")
                    }
                    itemsIndexed(
                        items = matchedChannels,
                        key = { index, channel -> "ch_${index}_${channel.name}|${channel.url}" }
                    ) { _, channel ->
                        ChannelCard(
                            name = channel.name,
                            logoUrl = channel.logo,
                            onClick = {
                                viewModel.setCurrentPlaylist(matchedChannels)
                                viewModel.selectChannel(channel)
                                navController.navigate(Routes.LIVE_PLAYER) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridSectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}
