package com.streambd.iptv.ui.screens.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.ui.components.CategoryChip
import com.streambd.iptv.ui.components.ChannelCard
import com.streambd.iptv.ui.components.ChannelGridSkeleton
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.PremiumOptionRow
import com.streambd.iptv.ui.components.SearchableTopBar
import com.streambd.iptv.ui.components.SectionHeader
import com.streambd.iptv.ui.components.initialFocus
import com.streambd.iptv.ui.components.restoreFocus
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.MainViewModel

/**
 * Channels of one category: group chips on top, then a grid (single
 * group) or one horizontal row per group (All).
 *
 * Logic is unchanged (filtering, group list, playlist hand-off to the
 * player, duplicate-safe keys). UI is rebuilt on shared components.
 * TV: the first channel takes focus automatically.
 */
@Composable
fun LiveCategoryChannelsScreen(viewModel: MainViewModel, navController: NavController) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val allChannelsMap by viewModel.categoryChannels.collectAsState()

    val categoryName = selectedCategory?.name ?: ""
    val allChannels = allChannelsMap[categoryName]

    var selectedGroup by rememberSaveable { mutableStateOf("All") }
    var isGroupsModalExpanded by rememberSaveable { mutableStateOf(false) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val d = AppTheme.dimens
    val scheme = MaterialTheme.colorScheme

    val groups = remember(allChannels) {
        val uniqueGroups = allChannels?.map { it.group }?.distinct()?.sorted() ?: emptyList()
        listOf("All") + uniqueGroups
    }

    val filteredChannels = remember(allChannels, selectedGroup, searchQuery) {
        val groupFiltered = if (selectedGroup == "All") allChannels
        else allChannels?.filter { it.group == selectedGroup }
        if (searchQuery.isBlank()) groupFiltered
        else groupFiltered?.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Keep the tap callback referencing the LATEST list even if Compose skips
    // recomposing items whose Channel data hasn't changed.
    val currentPlaylist by rememberUpdatedState(filteredChannels ?: emptyList())

    val openChannel: (Channel) -> Unit = { channel ->
        viewModel.setCurrentPlaylist(currentPlaylist)
        viewModel.selectChannel(channel)
        navController.navigate(Routes.LIVE_PLAYER) {
            launchSingleTop = true
        }
    }

    BackHandler(enabled = isSearchActive) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            searchQuery = ""
            isSearchActive = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        // ── Top bar: back (also while searching) · title · search ──
        SearchableTopBar(
            title = categoryName,
            searchActive = isSearchActive,
            onSearchActiveChange = { isSearchActive = it },
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            searchPlaceholder = "Search channels…",
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navigationDescription = "Back",
            onNavigationClick = {
                if (isSearchActive) {
                    isSearchActive = false
                    searchQuery = ""
                } else {
                    navController.popBackStack()
                }
            },
            keepNavigationWhileSearching = true,
            onSearchFieldFocusChanged = { isSearchFocused = it }
        )

        // ── Group chips ──
        val validGroups = groups.filter { it.isNotBlank() }
        if (validGroups.size > 1) {
            LazyRow(
                modifier = Modifier
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
                        onClick = { isGroupsModalExpanded = true }
                    )
                }
                itemsIndexed(
                    items = validGroups.take(10),
                    key = { index, group -> "chip_${index}_$group" }
                ) { _, group ->
                    val isSelected = selectedGroup == group
                    CategoryChip(
                        label = group,
                        selected = isSelected,
                        icon = if (isSelected) Icons.Default.Check else null,
                        onClick = { selectedGroup = group }
                    )
                }
            }
        }

        // ── Body ──
        when {
            allChannels == null -> ChannelGridSkeleton(minCellWidth = d.channelCardWidth)

            filteredChannels.isNullOrEmpty() -> EmptyState(
                title = "No channels available",
                message = if (searchQuery.isNotBlank()) "Try a different search." else null
            )

            else -> {
                val groupedChannels = remember(filteredChannels) {
                    if (selectedGroup != "All") mapOf(selectedGroup to filteredChannels)
                    else filteredChannels.groupBy { it.group }
                }
                val useGridLayout = selectedGroup != "All" || groupedChannels.size <= 1

                if (useGridLayout) {
                    val channelsList = if (groupedChannels.size == 1)
                        groupedChannels.values.first()
                    else filteredChannels
                    val groupName = if (groupedChannels.size == 1)
                        groupedChannels.keys.first() else selectedGroup

                    Column {
                        if (!groupName.isNullOrBlank() && selectedGroup == "All") {
                            SectionHeader(title = groupName)
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = d.channelCardWidth),
                            contentPadding = PaddingValues(
                                horizontal = d.screenPaddingH,
                                vertical = d.spaceMd
                            ),
                            horizontalArrangement = Arrangement.spacedBy(d.spaceMd),
                            verticalArrangement = Arrangement.spacedBy(d.spaceMd),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Index-prefixed key so duplicate name+url pairs
                            // (identical channels listed twice) don't crash.
                            gridItemsIndexed(
                                items = channelsList ?: emptyList(),
                                key = { index, channel ->
                                    "$index|${channel.name}|${channel.url}"
                                }
                            ) { index, channel ->
                                ChannelCard(
                                    name = channel.name,
                                    logoUrl = channel.logo,
                                    // TV: first channel takes focus automatically.
                                    modifier = if (index == 0) Modifier.initialFocus() else Modifier,
                                    onClick = { openChannel(channel) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(d.spaceLg),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        groupedChannels.entries.forEachIndexed { groupIndex, (group, channels) ->
                            item(key = "grp_${groupIndex}_$group") {
                                Column {
                                    if (group.isNotBlank()) {
                                        SectionHeader(title = group)
                                    }
                                    LazyRow(
                                        modifier = Modifier.restoreFocus(),
                                        contentPadding = PaddingValues(
                                            horizontal = d.screenPaddingH,
                                            vertical = 6.dp
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(d.spaceMd)
                                    ) {
                                        itemsIndexed(
                                            items = channels,
                                            key = { index, channel ->
                                                "$index|${channel.name}|${channel.url}"
                                            }
                                        ) { index, channel ->
                                            ChannelCard(
                                                name = channel.name,
                                                logoUrl = channel.logo,
                                                modifier = Modifier
                                                    .width(d.channelCardWidth)
                                                    .then(
                                                        if (groupIndex == 0 && index == 0)
                                                            Modifier.initialFocus()
                                                        else Modifier
                                                    ),
                                                onClick = { openChannel(channel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Groups modal ──
    if (isGroupsModalExpanded) {
        val selectedIndex = groups.indexOf(selectedGroup).coerceAtLeast(0)
        // Start scrolled to the current group so it is composed and can take focus.
        val modalListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

        PremiumDialogScaffold(
            title = "Select Group",
            subtitle = "${groups.size} group${if (groups.size > 1) "s" else ""}",
            onDismiss = { isGroupsModalExpanded = false },
            scrollContent = false
        ) {
            LazyColumn(
                state = modalListState,
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
                        // TV: focus lands on the current group.
                        modifier = if (index == selectedIndex) Modifier.initialFocus() else Modifier,
                        onClick = {
                            selectedGroup = group
                            isGroupsModalExpanded = false
                        }
                    )
                }
            }
        }
    }
}
