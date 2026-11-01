package com.streambd.iptv.ui.screens.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation.NavController
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.ui.components.ChannelBrowser
import com.streambd.iptv.ui.components.ChannelGridSkeleton
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.GroupChipsRow
import com.streambd.iptv.ui.components.GroupPickerDialog
import com.streambd.iptv.ui.components.SearchableTopBar
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.MainViewModel

/**
 * Channels of one category. Logic unchanged (group filter, search,
 * playlist hand-off to the player); the list/grid, group chips and group
 * dialog now come from shared components.
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

    // Tap callbacks must see the LATEST list even if items are not recomposed.
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
            .background(MaterialTheme.colorScheme.background)
    ) {
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

        GroupChipsRow(
            groups = groups,
            selectedGroup = selectedGroup,
            onSelect = { selectedGroup = it },
            onShowAll = { isGroupsModalExpanded = true }
        )

        when {
            allChannels == null -> ChannelGridSkeleton(minCellWidth = d.channelCardWidth)

            filteredChannels.isNullOrEmpty() -> EmptyState(
                title = "No channels available",
                message = if (searchQuery.isNotBlank()) "Try a different search." else null
            )

            else -> ChannelBrowser(
                channels = filteredChannels,
                selectedGroup = selectedGroup,
                onChannelClick = openChannel
            )
        }
    }

    if (isGroupsModalExpanded) {
        GroupPickerDialog(
            groups = groups,
            selectedGroup = selectedGroup,
            onSelect = {
                selectedGroup = it
                isGroupsModalExpanded = false
            },
            onDismiss = { isGroupsModalExpanded = false }
        )
    }
}
