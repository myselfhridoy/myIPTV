package com.streambd.iptv.ui.screens.events

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.SportzfyEvent
import com.streambd.iptv.data.models.SportzfyStream
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.CategoryChip
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.PremiumOptionRow
import com.streambd.iptv.ui.components.SearchableTopBar
import com.streambd.iptv.ui.components.ShimmerBox
import com.streambd.iptv.ui.components.initialFocus
import com.streambd.iptv.ui.components.restoreFocus
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch

/**
 * Live Events: sport tabs, status chips and match cards.
 *
 * Behaviour is unchanged — same filters, same pull-to-refresh, same
 * stream → player hand-off (now one shared function instead of two
 * copies). The UI is rebuilt on shared components; wide screens show
 * the match cards in several columns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveEventsScreen(
    eventsViewModel: LiveEventsViewModel,
    mainViewModel: MainViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    val events by eventsViewModel.events.collectAsState()
    val isLoading by eventsViewModel.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val device = LocalDeviceProfile.current
    val isTv = device.isTv
    val isCompact = device.screenWidthDp < 400
    val d = AppTheme.dimens
    val scheme = MaterialTheme.colorScheme

    val tileSize = when { isTv -> 64.dp; isCompact -> 48.dp; else -> 52.dp }
    val tileIconSize = when { isTv -> 30.dp; isCompact -> 22.dp; else -> 24.dp }

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf(EventStatus.All) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Event + its sources while the "Select Stream" dialog is open.
    var streamPicker by remember {
        mutableStateOf<Pair<SportzfyEvent, List<SportzfyStream>>?>(null)
    }

    val sportsCount = remember(events) {
        val counts = mutableMapOf<String, Int>()
        counts["All"] = events.size
        events.forEach { e ->
            val s = e.sport ?: "Other"
            counts[s] = (counts[s] ?: 0) + 1
        }
        counts
    }

    val categories = remember(events) {
        listOf("All", "Cricket", "Football", "Motorsports", "Wrestling", "WWE")
            .filter { it == "All" || sportsCount.containsKey(it) }
    }

    val statusCounts = remember(events) {
        EventStatus.entries.associateWith { it.apply(events).size }
    }

    val filteredEvents = remember(events, selectedCategory, selectedStatus, searchQuery) {
        filterEvents(events, selectedCategory, selectedStatus, searchQuery)
    }

    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) eventsViewModel.fetchEvents()
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) pullToRefreshState.endRefresh()
    }

    BackHandler(enabled = isSearchActive) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            searchQuery = ""
            isSearchActive = false
        }
    }

    // Same hand-off as before: build the Channel, give it to the player screen.
    val play: (SportzfyEvent, SportzfyStream, List<SportzfyStream>) -> Unit =
        { event, stream, all ->
            mainViewModel.selectChannel(buildEventChannel(event, stream, all))
            navController.navigate(Routes.LIVE_PLAYER) { launchSingleTop = true }
        }

    val openEvent: (SportzfyEvent) -> Unit = { event ->
        coroutineScope.launch {
            val streams = eventsViewModel.getStreamsForEvent(event.id ?: "")
            if (streams.isNotEmpty()) {
                if (streams.size == 1) play(event, streams.first(), streams)
                else streamPicker = event to streams
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            SearchableTopBar(
                title = "Live Events",
                searchActive = isSearchActive,
                onSearchActiveChange = { isSearchActive = it },
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                searchPlaceholder = "Search teams or leagues…",
                // TV: Back opens the drawer, so no hamburger.
                navigationIcon = if (onMenuClick != null && !isTv) Icons.Default.Menu else null,
                navigationDescription = "Menu",
                onNavigationClick = { onMenuClick?.invoke() },
                onSearchFieldFocusChanged = { isSearchFocused = it }
            )

            // ── Sport tabs ──
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .restoreFocus(),
                contentPadding = PaddingValues(horizontal = d.screenPaddingH, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(d.spaceMd)
            ) {
                itemsIndexed(items = categories, key = { _, cat -> cat }) { _, cat ->
                    SportTab(
                        name = cat,
                        count = sportsCount[cat] ?: 0,
                        icon = if (cat == "All") Icons.Default.GridView else sportIcon(cat),
                        selected = selectedCategory == cat,
                        tileSize = tileSize,
                        iconSize = tileIconSize,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            // ── Status chips ──
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .restoreFocus(),
                contentPadding = PaddingValues(horizontal = d.screenPaddingH, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(d.spaceSm)
            ) {
                EventStatus.entries.forEach { status ->
                    item(key = "chip_${status.name}") {
                        CategoryChip(
                            label = "${status.label} (${statusCounts[status] ?: 0})",
                            selected = selectedStatus == status,
                            icon = status.icon,
                            onClick = { selectedStatus = status }
                        )
                    }
                }
            }

            Spacer(Modifier.height(d.spaceSm))

            // ── Content ──
            when {
                isLoading -> EventsSkeleton()

                filteredEvents.isEmpty() -> EmptyState(
                    title = if (selectedStatus == EventStatus.All) "No events right now"
                    else "No ${selectedStatus.label} events",
                    message = "Try a different filter"
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = d.screenPaddingH,
                        end = d.screenPaddingH,
                        top = 0.dp,
                        bottom = 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(d.gridGap + 2.dp),
                    verticalArrangement = Arrangement.spacedBy(d.gridGap + 2.dp)
                ) {
                    gridItemsIndexed(
                        items = filteredEvents,
                        key = { index, event -> "ev_${index}_${event.id ?: event.hashCode()}" }
                    ) { index, event ->
                        MatchCard(
                            event = event,
                            isTv = isTv,
                            isCompact = isCompact,
                            // TV: first match takes focus automatically.
                            modifier = if (index == 0 && !isSearchActive) Modifier.initialFocus()
                            else Modifier,
                            onClick = { openEvent(event) }
                        )
                    }
                }
            }
        }

        if (pullToRefreshState.isRefreshing) {
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = scheme.surface,
                contentColor = scheme.primary
            )
        }
    }

    // ── Multi-source picker ──
    streamPicker?.let { (event, streams) ->
        PremiumDialogScaffold(
            title = "Select Stream",
            subtitle = "${streams.size} source${if (streams.size > 1) "s" else ""}",
            onDismiss = { streamPicker = null },
            scrollContent = false
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = streams,
                    key = { index, stream ->
                        "s_${index}_${stream.label ?: ""}|${stream.stream_url ?: ""}"
                    }
                ) { index, stream ->
                    PremiumOptionRow(
                        title = stream.label?.takeIf { !it.isNullOrBlank() } ?: "Source",
                        selected = false,
                        modifier = if (index == 0) Modifier.initialFocus() else Modifier,
                        onClick = {
                            streamPicker = null
                            play(event, stream, streams)
                        }
                    )
                }
            }
        }
    }
}

/** Loading placeholder shaped like the match-card grid. */
@Composable
private fun EventsSkeleton() {
    val d = AppTheme.dimens
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = d.screenPaddingH, vertical = d.spaceSm),
        horizontalArrangement = Arrangement.spacedBy(d.gridGap + 2.dp),
        verticalArrangement = Arrangement.spacedBy(d.gridGap + 2.dp),
        userScrollEnabled = false
    ) {
        items(4) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}
