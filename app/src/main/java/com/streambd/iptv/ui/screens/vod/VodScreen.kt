package com.streambd.iptv.ui.screens.vod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.TmdbMedia
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.AppButton
import com.streambd.iptv.ui.components.AppIconButton
import com.streambd.iptv.ui.components.SearchHeader
import com.streambd.iptv.ui.components.AppTopBar
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.PosterCard
import com.streambd.iptv.ui.components.PosterCardSkeleton
import com.streambd.iptv.ui.components.RemoteImage
import com.streambd.iptv.ui.components.SectionHeader
import com.streambd.iptv.ui.components.ShimmerBox
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    // A 24" TV is only ~540dp tall: smaller posters let a whole row fit under the hero.
    val cardWidth = when {
        isMobilePortrait -> 115.dp
        isTV && device.screenHeightDp < 600 -> 150.dp
        else -> 180.dp
    }

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

    // Opening a title from the search results must NOT reset the screen first:
    // that made the browse rows flash during the transition to the details page.
    // Instead the search is closed once this screen has really been covered
    // (ON_STOP), so coming back lands on the normal browse view as before.
    var closeSearchAfterNavigation by remember { mutableStateOf(false) }
    val currentCloseSearch by rememberUpdatedState(closeSearch)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && closeSearchAfterNavigation) {
                closeSearchAfterNavigation = false
                currentCloseSearch()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            showInitialSkeleton -> VodSkeleton(isMobilePortrait = isMobilePortrait, cardWidth = cardWidth)

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
                    closeSearchAfterNavigation = true
                    handleItemClick(media)
                }
            )

            else -> BrowseMode(
                mediaRows = mediaRows,
                isMobilePortrait = isMobilePortrait,
                isTv = isTV,
                cardWidth = cardWidth,
                screenHeightDp = device.screenHeightDp,
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
    isTv: Boolean,
    cardWidth: Dp,
    screenHeightDp: Int,
    onMenuClick: (() -> Unit)?,
    onSearchClick: () -> Unit,
    firstCardRequester: FocusRequester,
    onClickItem: (TmdbMedia) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // TV: Back opens the drawer, so a hamburger button is just an extra stop for the remote.
    val menuLeading: (@Composable () -> Unit)? = if (onMenuClick != null && !isTv) {
        { AppIconButton(Icons.Default.Menu, "Menu", onMenuClick) }
    } else null

    // Featured titles for the hero banner: first trending row, with a backdrop.
    val heroItems = remember(mediaRows) {
        mediaRows.firstOrNull()?.list
            ?.filter { !it.backdrop_path.isNullOrBlank() }
            ?.take(5)
            ?: emptyList()
    }

    // Header stays pinned (same as the other tabs); only the content scrolls.
    Column(modifier = Modifier.fillMaxSize()) {
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            if (heroItems.isNotEmpty()) {
                HeroBanner(
                    items = heroItems,
                    isCompact = isMobilePortrait,
                    isTv = isTv,
                    screenHeightDp = screenHeightDp,
                    // D-pad Up onto the banner: scroll to the very top so the whole
                    // banner is visible (otherwise only its bottom edge showed).
                    onFocusedScrollToTop = { scope.launch { scrollState.animateScrollTo(0) } },
                    onOpen = onClickItem
                )
            }

            Spacer(modifier = Modifier.height(if (isMobilePortrait) 8.dp else 12.dp))

            mediaRows.forEachIndexed { index, row ->
                MediaSection(
                    title = row.title,
                    mediaList = row.list,
                    firstCardRequester = if (index == 0) firstCardRequester else null,
                    isMobilePortrait = isMobilePortrait,
                    cardWidth = cardWidth,
                    onClick = onClickItem
                )
                Spacer(modifier = Modifier.height(if (isMobilePortrait) 12.dp else 20.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Featured banner (Prime / Netflix style): big backdrop, title, a short
 * meta line and a "More info" button. Rotates through up to 5 titles every
 * 7 s. Uses Crossfade (not a Pager) — see the no-Lazy note on VodScreen.
 */
@Composable
private fun HeroBanner(
    items: List<TmdbMedia>,
    isCompact: Boolean,
    isTv: Boolean,
    screenHeightDp: Int,
    onFocusedScrollToTop: () -> Unit,
    onOpen: (TmdbMedia) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val d = AppTheme.dimens
    var index by remember(items) { mutableStateOf(0) }

    LaunchedEffect(items) {
        if (items.size > 1) {
            while (true) {
                delay(7000)
                index = (index + 1) % items.size
            }
        }
    }

    val current = items[index.coerceIn(0, items.lastIndex)]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // TV: height from the SCREEN height (a 21:9 banner on a 540dp-tall
                // TV is ~410dp and pushes every row off-screen).
                if (isTv) Modifier.height((screenHeightDp * 0.42f).dp)
                else Modifier.aspectRatio(if (isCompact) 16f / 11f else 21f / 9f)
            )
    ) {
        Crossfade(
            targetState = current,
            animationSpec = androidx.compose.animation.core.tween(600),
            label = "heroBackdrop"
        ) { media ->
            RemoteImage(
                url = media.backdropUrl,
                label = media.displayTitle,
                contentScale = ContentScale.Crop,
                describe = false,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Fade into the page background (bottom) and keep the text readable (left).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            scheme.background.copy(alpha = 0.55f),
                            scheme.background
                        ),
                        startY = 0f
                    )
                )
        )
        if (!isCompact) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.6f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(scheme.background.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (isCompact) 1f else 0.6f)
                .padding(horizontal = d.screenPaddingH, vertical = 14.dp)
        ) {
            Text(
                text = current.displayTitle,
                color = scheme.onBackground,
                style = if (isCompact || isTv) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(4.dp))

            val meta = listOfNotNull(
                current.displayDate.take(4).takeIf { it.length == 4 },
                if (current.isMovie) "Movie" else "TV Series",
                current.ratingText()?.let { "★ $it" }
            ).joinToString("  •  ")
            Text(
                text = meta,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )

            if (!isCompact && !current.overview.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = current.overview,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(12.dp))
            AppButton(
                text = "More info",
                onClick = { onOpen(current) },
                icon = Icons.Default.Info,
                modifier = Modifier.onFocusChanged { if (it.isFocused) onFocusedScrollToTop() }
            )
        }
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
        SearchHeader(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "Search movies, TV shows…",
            onClose = onClose,
            modifier = Modifier.focusGroup(),
            onSearch = {
                keyboard?.hide()
                if (results.isNotEmpty()) {
                    pendingJumpToResults = true
                }
            }
        )

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
    cardWidth: Dp,
    onClick: (TmdbMedia) -> Unit
) {
    val d = AppTheme.dimens
    val spacing = if (isMobilePortrait) 10.dp else 16.dp
    val verticalPadding = if (isMobilePortrait) 6.dp else 12.dp

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
private fun VodSkeleton(isMobilePortrait: Boolean, cardWidth: Dp) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (isMobilePortrait) 60.dp else 120.dp)
    ) {
        repeat(3) {
            SkeletonRow(isMobilePortrait = isMobilePortrait, cardWidth = cardWidth)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SkeletonRow(isMobilePortrait: Boolean, cardWidth: Dp) {
    val d = AppTheme.dimens
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
