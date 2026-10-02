package com.streambd.iptv.ui.screens.vod

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.streambd.iptv.data.models.TmdbMedia
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.SurfaceDark
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private val SearchHintText = Color(0xFF999999)
private val SearchContainerFocused = Color(0xFF1E2638)
private val SearchContainerIdle = Color(0xFF141A24)
private val StarGold = Color(0xFFFFC43D)
private val SkeletonBase = Color.White

private data class MediaRow(
    val key: String,
    val title: String,
    val list: List<TmdbMedia>
)

private fun TmdbMedia.stableKey(): String = "${media_type ?: "unknown"}_$id"

/**
 * Top-level VOD screen.
 *
 * Structure:
 *   - Skeleton            → while initial content is loading
 *   - SearchMode          → user is searching (own focus tree)
 *   - BrowseMode          → normal browsing (own focus tree)
 *
 * The two modes are mutually exclusive. Because only one is composed
 * at a time, focus search never crosses between the top bar, the
 * search field, and the content — that's what caused the previous
 * `LayoutCoordinate … isAttached` and `CompositionLocal … attached`
 * crashes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun VodScreen(
    viewModel: TmdbViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    // ── Data ──
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

    // ── UI state ──
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Only requester left: seeds focus on first card after initial load
    // or after returning from a modal state like search.
    val firstCardRequester = remember { FocusRequester() }

    // ── Device ──
    val context = LocalContext.current
    val isTV = remember(context) { com.streambd.iptv.utils.DeviceType.isTv(context) }
    val configuration = LocalConfiguration.current
    val isMobilePortrait = !isTV && configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // ── Rows ──
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

    // ── Actions ──
    val handleItemClick: (TmdbMedia) -> Unit = { media ->
        viewModel.selectMedia(media)
        navController.navigate(Routes.TMDB_DETAILS) { launchSingleTop = true }
    }

    val closeSearch: () -> Unit = {
        isSearching = false
        searchQuery = ""
        viewModel.search("")
    }

    // ── Render ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        when {
            showInitialSkeleton -> VodSkeleton(isMobilePortrait = isMobilePortrait)

            isSearching -> SearchMode(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    viewModel.search(it)
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

    // Seed focus on the first card whenever browse mode is active and
    // content is ready. Fires on:
    //   • initial load completes
    //   • user exits search
    //   • user returns from TmdbDetailsScreen (composable recreated)
    LaunchedEffect(isSearching, mediaRows.isNotEmpty(), isTV) {
        if (isTV && !isSearching && mediaRows.isNotEmpty()) {
            delay(300)
            runCatching { firstCardRequester.requestFocus() }
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════
 * BROWSE MODE
 *
 * One LazyColumn that contains:
 *   1. Top bar (first item — natural focus flow to the rows below)
 *   2. Spacer
 *   3. One item per media row
 *   4. Bottom padding
 *
 * No overlays, no cross-composable focus properties.
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(key = "top_bar") {
            BrowseTopBar(
                onMenuClick = onMenuClick,
                onSearchClick = onSearchClick
            )
        }

        item(key = "top_spacer") {
            Spacer(
                modifier = Modifier.height(if (isMobilePortrait) 8.dp else 16.dp)
            )
        }

        mediaRows.forEachIndexed { index, row ->
            item(key = "row_${row.key}") {
                MediaSection(
                    title = row.title,
                    count = row.list.size,
                    mediaList = row.list,
                    firstItemModifier = if (index == 0)
                        Modifier.focusRequester(firstCardRequester)
                    else Modifier,
                    isMobilePortrait = isMobilePortrait,
                    onClick = onClickItem
                )
                Spacer(
                    modifier = Modifier.height(if (isMobilePortrait) 16.dp else 24.dp)
                )
            }
        }
    }
}

@Composable
private fun BrowseTopBar(
    onMenuClick: (() -> Unit)?,
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            // focusGroup scopes the 2D focus search to these two buttons.
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onMenuClick != null) {
            TvIconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = WhiteText,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Text(
            text = "Media",
            color = WhiteText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        TvIconButton(
            onClick = onSearchClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = WhiteText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════════════════
 * SEARCH MODE
 *
 * Fixed search field at the top + grid of results below.
 * Pressing Back exits search mode.
 * ═══════════════════════════════════════════════════════════════════ */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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
    val searchFieldRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Focus the search field as soon as SearchMode mounts.
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { searchFieldRequester.requestFocus() }
    }

    BackHandler { onClose() }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Search field (fixed at top) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .focusGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search movies, TV shows…",
                        color = SearchHintText,
                        fontSize = 16.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = SearchHintText,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .tvTouchClick(onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = WhiteText
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WhiteText,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = WhiteText,
                    unfocusedTextColor = WhiteText,
                    cursorColor = WhiteText,
                    focusedContainerColor = SearchContainerFocused.copy(alpha = 0.9f),
                    unfocusedContainerColor = SearchContainerIdle.copy(alpha = 0.9f)
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFieldRequester)
            )
        }

        // ── Body ──
        when {
            results.isNotEmpty() -> {
                val columns = if (isMobilePortrait) 3 else 5
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 32.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    gridItems(
                        items = results,
                        key = { it.stableKey() }
                    ) { media ->
                        TmdbMediaCard(
                            media = media,
                            isMobilePortrait = isMobilePortrait,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f),
                            focusScale = 1.04f,
                            onClick = { onClickItem(media) }
                        )
                    }
                }
            }

            query.isNotEmpty() && !isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptySearchState(query = query)
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    SearchIdleHint()
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = GrayText.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No results for \"$query\"",
            color = WhiteText.copy(alpha = 0.85f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Try a different title or check your spelling.",
            color = GrayText.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SearchIdleHint() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = GrayText.copy(alpha = 0.3f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Search movies & TV shows",
            color = GrayText.copy(alpha = 0.6f),
            fontSize = 16.sp
        )
    }
}

/* ═══════════════════════════════════════════════════════════════════
 * SHARED COMPONENTS
 * ═══════════════════════════════════════════════════════════════════ */

@Composable
private fun MediaSection(
    title: String,
    count: Int,
    mediaList: List<TmdbMedia>,
    firstItemModifier: Modifier = Modifier,
    isMobilePortrait: Boolean,
    onClick: (TmdbMedia) -> Unit
) {
    val spacing = if (isMobilePortrait) 12.dp else 24.dp
    val verticalPadding = if (isMobilePortrait) 8.dp else 16.dp
    val cardWidth = if (isMobilePortrait) 115.dp else 180.dp
    val cardHeight = if (isMobilePortrait) 172.dp else 270.dp

    Column {
        // ── Row header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BioAccent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = WhiteText,
                fontSize = if (isMobilePortrait) 17.sp else 19.sp,
                fontWeight = FontWeight.Bold
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    color = GrayText.copy(alpha = 0.55f),
                    fontSize = if (isMobilePortrait) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Cards ──
        LazyRow(
            // focusGroup keeps horizontal focus traversal scoped to this
            // row; without it Compose would run a beyond-bounds search
            // that occasionally touches detached nodes.
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = verticalPadding
            )
        ) {
            itemsIndexed(
                items = mediaList,
                key = { _, media -> media.stableKey() }
            ) { index, media ->
                TmdbMediaCard(
                    media = media,
                    isMobilePortrait = isMobilePortrait,
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .then(if (index == 0) firstItemModifier else Modifier),
                    focusScale = 1.08f,
                    onClick = { onClick(media) }
                )
            }
        }
    }
}

@Composable
private fun TmdbMediaCard(
    media: TmdbMedia,
    isMobilePortrait: Boolean,
    modifier: Modifier,
    focusScale: Float = 1.08f,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = tween(200),
        label = "cardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) BioAccent else Color.Transparent,
        animationSpec = tween(200),
        label = "cardBorder"
    )

    Box(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(3.dp, borderColor, RoundedCornerShape(12.dp))
            .tvTouchClick(onClick)
    ) {
        AsyncImage(
            model = media.posterUrl,
            contentDescription = media.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Rating chip
        if (media.vote_average != null && media.vote_average > 0) {
            Row(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = StarGold,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = String.format(Locale.US, "%.1f", media.vote_average),
                    color = WhiteText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Title
        Text(
            text = media.displayTitle,
            color = WhiteText,
            fontSize = if (isMobilePortrait) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth()
        )
    }
}

/* ═══════════════════════════════════════════════════════════════════
 * SKELETON
 * ═══════════════════════════════════════════════════════════════════ */

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
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    val cardWidth = if (isMobilePortrait) 115.dp else 180.dp
    val cardHeight = if (isMobilePortrait) 172.dp else 270.dp
    val spacing = if (isMobilePortrait) 12.dp else 24.dp

    Column {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .width(160.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SkeletonBase.copy(alpha = alpha * 0.5f))
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            userScrollEnabled = false
        ) {
            items(6) {
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SkeletonBase.copy(alpha = alpha))
                )
            }
        }
    }
}
