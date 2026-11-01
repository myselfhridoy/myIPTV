package com.streambd.iptv.ui.screens.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.streambd.iptv.ui.viewmodels.PlaylistChannelsViewModel

/**
 * Channels of one user playlist. Same logic as before (load, group filter,
 * search, hand-off to the player); UI now uses the shared top bar, group
 * chips, channel browser and skeleton — identical to Live categories.
 *
 * Note: as before, the player receives the group-filtered list
 * (`displayedChannels`), not the search-filtered one.
 */
@Composable
fun PlaylistChannelsScreen(
    playlistTitle: String,
    playlistUrl: String,
    viewModel: PlaylistChannelsViewModel,
    mainViewModel: MainViewModel,
    navController: NavController
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val allGroups by viewModel.groups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val channels by viewModel.displayedChannels.collectAsState()

    var isGroupsModalExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val d = AppTheme.dimens
    val currentPlaylist by rememberUpdatedState(channels)

    LaunchedEffect(playlistUrl) {
        viewModel.loadPlaylist(playlistUrl)
    }

    val filteredChannels = remember(channels, searchQuery) {
        if (searchQuery.isBlank()) channels
        else channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val openChannel: (Channel) -> Unit = { channel ->
        viewModel.selectChannel(channel)
        mainViewModel.setCurrentPlaylist(currentPlaylist)
        mainViewModel.selectChannel(channel)
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

    val validGroups = allGroups.filter { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchableTopBar(
            title = playlistTitle,
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

        if (isLoading) {
            ChannelGridSkeleton(minCellWidth = d.channelCardWidth)
        } else {
            GroupChipsRow(
                groups = validGroups,
                selectedGroup = selectedGroup,
                onSelect = { viewModel.selectGroup(it) },
                onShowAll = { isGroupsModalExpanded = true }
            )

            if (filteredChannels.isEmpty()) {
                EmptyState(
                    title = "No channels available",
                    message = if (searchQuery.isNotBlank()) "Try a different search." else null
                )
            } else {
                ChannelBrowser(
                    channels = filteredChannels,
                    selectedGroup = selectedGroup,
                    onChannelClick = openChannel
                )
            }
        }
    }

    if (isGroupsModalExpanded) {
        GroupPickerDialog(
            groups = validGroups,
            selectedGroup = selectedGroup,
            onSelect = {
                viewModel.selectGroup(it)
                isGroupsModalExpanded = false
            },
            onDismiss = { isGroupsModalExpanded = false }
        )
    }
}
