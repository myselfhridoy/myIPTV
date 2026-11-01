package com.streambd.iptv.ui.screens.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.TmdbMedia
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.AppIconButton
import com.streambd.iptv.ui.components.AppSearchField
import com.streambd.iptv.ui.components.AppTopBar
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.PosterCard
import com.streambd.iptv.ui.components.PosterCardSkeleton
import com.streambd.iptv.ui.components.SectionHeader
import com.streambd.iptv.ui.components.ShimmerBox
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private data class MediaRow(
    val key: String,
    val title: String,
    val list: List<TmdbMedia>
)

/** "7.8" for a positive score, null otherwise (no badge). */
private fun TmdbMedia.ratingText(): String? =
    vote_average?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it) }

/**
 * Top-level VOD ("Media") screen.
 *
 * IMPORTANT — kept exactly as before: this screen deliberately uses NO Lazy
 * containers.
 *
 * Compose 1.6.x has a bug in LazyLayoutBeyondBoundsModifierLocal that
 * crashes the focus system on Android TV when D-pad navigation triggers
 * a beyond-bounds focus search:
 *   IllegalStateException: LayoutCoordinate operations are only valid
 *   when isAttached is true
 * The fix needs Compose Foundation 1.7.0+ (Kotlin 1.9.23+), so every row is a
 * plain Column/Row + scroll modifier. Focus then walks a fully composed tree.
 * Do not convert these rows to LazyRow without upgrading the toolchain.
 */
@Composable
fun VodScreen(
    viewModel: TmdbViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val trendingTv by viewModel.trendingTv.collectAsState()

    val hindiMovies by viewModel.hindiMovies.collectAsState()
    val hindiTv by viewModel.hindiTv.collectAsState()
    val southMovies by viewModel.southMovies.collectAsState()
    val southTv by viewModel.southTv.collectAsState()
    val banglaMovies by viewModel.banglaMovies.collectAsState()
    val banglaTv by viewModel.banglaTv.collectAsState()

    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val firstCardRequester = remember { FocusRequester() }

    val context = LocalContext.current
    val device = LocalDeviceProfile.current
    val isTV = device.isTv
    val isMobilePortrait = !isTV && !device.isLandscape

    val mediaRows = remember(
        trendingMovies, trendingTv,
        hindiMovies, hindiTv,
        southMovies, southTv,
        banglaMovies, banglaTv
    ) {
        listOf(
            MediaRow("trending_movies", "Trending Movies", trendingMovies),
            MediaRow("trending_tv", "Trending TV Shows", trendingTv),
            MediaRow("hindi_movies", "Bollywood Hits", hindiMovies),
            MediaRow("hindi_tv", "Hindi TV Shows", hindiTv),
            MediaRow("south_movies", "South Indian Blockbusters", southMovies),
            MediaRow("south_tv", "South Indian TV Shows", southTv),
            MediaRow("bangla_movies", "Bengali Movies", banglaMovies),
            MediaRow("bangla_tv", "Bengali TV Shows", banglaTv)
        ).filter { it.list.isNotEmpty() }
    }

    val showInitialSkeleton = isLoading && trendingMovies.isEmpty()

    val handleItemClick: (TmdbMedia) -> Unit = { media ->
        viewModel.selectMedia(media)
        navController.navigate(Routes.TMDB_DETAILS) { launchSingleTop = true }
    }

    val closeSearch: () -> Unit = {
        isSearching = false
        searchQuery = ""
        viewModel.search("", context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            showInitialSkeleton -> VodSkeleton(isMobilePortrait = isMobilePortrait)

            isSearching -> SearchMode(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.search(it, context)
                },
                results = searchResults,
                isLoading = isLoading,
                isMobilePortrait = isMobilePortrait,
                onClose = closeSearch,
                onClickItem = { media ->
                    closeSearch()
                    handleItemClick(media)
                }
            )

            else -> BrowseMode(
                mediaRows = mediaRows,
                isMobilePortrait = isMobilePortrait,
                onMenuClick = onMenuClick,
                onSearchClick = { isSearching = true },
                firstCardRequester = firstCardRequester,
                onClickItem = handleItemClick
            )
        }
    }

    // TV: once the rows are on screen, put focus on the first poster.
    LaunchedEffect(isSearching, mediaRows.isNotEmpty(), isTV) {
        if (isTV && !isSearching && mediaRows.isNotEmpty()) {
            delay(300)
            runCatching { firstCardRequester.requestFocus() }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════
 * BROWSE MODE — non-lazy
 * ═══════════════════════════════════════════════════════════════════ */

@Composable
private fun BrowseMode(
    mediaRows: List<MediaRow>,
    isMobilePortrait: Boolean,
    onMenuClick: (() -> Unit)?,
    onSearchClick: () -> Unit,
    firstCardRequester: FocusRequester,
    onClickItem: (TmdbMedia) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    val menuLeading: (@Composable () -> Unit)? = if (onMenuClick != null) {
        { AppIconButton(Icons.Default.Menu, "Menu", onMenuClick) }
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AppTopBar(
            title = "Media",
            modifier = Modifier.focusGroup(),
            leading = menuLeading,
            actions = {
                AppIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    onClick = onSearchClick
                )
            }
        )
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))

        Spacer(modifier = Modifier.height(if (isMobilePortrait) 8.dp else 16.dp))

        mediaRows.forEachIndexed { index, row ->
            MediaSection(
                title = row.title,
                mediaList = row.list,
                firstCardRequester = if (index == 0) firstCardRequester else null,
                isMobilePortrait = isMobilePortrait,
                onClick = onClickItem
            )
            Spacer(modifier = Modifier.height(if (isMobilePortrait) 12.dp else 20.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/* ═══════════════════════════════════════════════════════════════════
 * SEARCH MODE — non-lazy, manual grid
 * ═══════════════════════════════════════════════════════════════════ */

@Composable
private fun SearchMode(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<TmdbMedia>,
    isLoading: Boolean,
    isMobilePortrait: Boolean,
    onClose: () -> Unit,
    onClickItem: (TmdbMedia) -> Unit
) {
    val d = AppTheme.dimens
    val firstResultRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    var pendingJumpToResults by remember { mutableStateOf(false) }

    // After pressing Search on the keyboard, jump focus into the results.
    LaunchedEffect(pendingJumpToResults, results) {
        if (pendingJumpToResults && results.isNotEmpty()) {
            delay(200)
            if (runCatching { firstResultRequester.requestFocus() }.isSuccess) {
                pendingJumpToResults = false
            }
        }
    }

    BackHandler { onClose() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.screenPaddingH, vertical = 12.dp)
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search movies, TV shows…",
                onClose = onClose,
                modifier = Modifier.fillMaxWidth(),
                onSearch = {
                    keyboard?.hide()
                    if (results.isNotEmpty()) {
                        pendingJumpToResults = true
                    }
                }
            )
        }

        when {
            results.isNotEmpty() -> {
                val columns = if (isMobilePortrait) 3 else 5
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = d.screenPaddingH, vertical = 8.dp)
                ) {
                    results.chunked(columns).forEachIndexed { rowIdx, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEachIndexed { colIdx, media ->
                                val isFirst = rowIdx == 0 && colIdx == 0
                                Box(modifier = Modifier.weight(1f)) {
                                    PosterCard(
                                        title = media.displayTitle,
                                        imageUrl = media.posterUrl,
                                        rating = media.ratingText(),
                                        focusRequester = if (isFirst) firstResultRequester else null,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { onClickItem(media) }
                                    )
                                }
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            query.isNotEmpty() && !isLoading -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    EmptyState(
                        title = "No results for “$query”",
                        message = "Try a different title or check your spelling.",
                        icon = Icons.Default.Search
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    EmptyState(
                        title = "Search movies & TV shows",
                        icon = Icons.Default.Search
                    )
                }
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════
 * ROW + SKELETON — non-lazy
 * ═══════════════════════════════════════════════════════════════════ */

@Composable
private fun MediaSection(
    title: String,
    mediaList: List<TmdbMedia>,
    firstCardRequester: FocusRequester?,
    isMobilePortrait: Boolean,
    onClick: (TmdbMedia) -> Unit
) {
    val d = AppTheme.dimens
    val spacing = if (isMobilePortrait) 10.dp else 16.dp
    val verticalPadding = if (isMobilePortrait) 6.dp else 12.dp
    val cardWidth = if (isMobilePortrait) 115.dp else 180.dp

    Column {
        SectionHeader(title = title)

        // Non-lazy horizontal row (see class comment on VodScreen).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .focusGroup()
                .padding(vertical = verticalPadding)
                .padding(horizontal = d.screenPaddingH),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            mediaList.forEachIndexed { index, media ->
                PosterCard(
                    title = media.displayTitle,
                    imageUrl = media.posterUrl,
                    rating = media.ratingText(),
                    focusRequester = if (index == 0) firstCardRequester else null,
                    modifier = Modifier.width(cardWidth),
                    onClick = { onClick(media) }
                )
            }
        }
    }
}

@Composable
private fun VodSkeleton(isMobilePortrait: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (isMobilePortrait) 60.dp else 120.dp)
    ) {
        repeat(3) {
            SkeletonRow(isMobilePortrait = isMobilePortrait)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SkeletonRow(isMobilePortrait: Boolean) {
    val d = AppTheme.dimens
    val cardWidth = if (isMobilePortrait) 115.dp else 180.dp
    val spacing = if (isMobilePortrait) 10.dp else 16.dp

    Column {
        ShimmerBox(
            modifier = Modifier
                .padding(horizontal = d.screenPaddingH, vertical = 8.dp)
                .width(160.dp)
                .height(20.dp),
            shape = RoundedCornerShape(4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), enabled = false)
                .padding(horizontal = d.screenPaddingH, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            repeat(6) {
                PosterCardSkeleton(modifier = Modifier.width(cardWidth))
            }
        }
    }
}
