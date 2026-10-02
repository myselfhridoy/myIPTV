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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // ── Focus anchors ──
    val searchFieldFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    val menuRequester = remember { FocusRequester() }
    val searchBtnRequester = remember { FocusRequester() }
    val contentRequester = remember { FocusRequester() }

    var pendingFocus by remember { mutableStateOf<String?>(null) }

    val closeSearch: () -> Unit = {
        isSearchExpanded = false
        searchQuery = ""
        viewModel.search("")
    }

    BackHandler(enabled = isSearchExpanded || searchQuery.isNotEmpty()) {
        if (isSearchFocused) {
            focusManager.clearFocus()
            pendingFocus = "results"
        } else {
            closeSearch()
            pendingFocus = "home"
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            // Safe: requester's node may not be attached yet.
            runCatching { searchFieldFocusRequester.requestFocus() }
        }
    }

    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTV = remember(context) { com.streambd.iptv.utils.DeviceType.isTv(context) }
    val isMobilePortrait = !isTV && configuration.orientation == Configuration.ORIENTATION_PORTRAIT

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
    val showEmptyNoResults = searchQuery.isNotEmpty() &&
            searchResults.isEmpty() && !isLoading

    // Content is only composed when the skeleton is NOT shown.
    // Guard focus requests with this so we never target a detached node.
    val contentVisible = !showInitialSkeleton && mediaRows.isNotEmpty()

    // ── Deferred focus restoration ──
    LaunchedEffect(pendingFocus, searchResults) {
        when (pendingFocus) {
            "results" -> {
                if (searchResults.isNotEmpty()) {
                    delay(50)
                    runCatching { resultsFocusRequester.requestFocus() }
                }
                pendingFocus = null
            }
            "home" -> {
                if (contentVisible) {
                    delay(150)
                    runCatching { firstCardFocusRequester.requestFocus() }
                }
                pendingFocus = null
            }
            else -> Unit
        }
    }

    // Focus first card only when TV + content ready + not searching.
    LaunchedEffect(isTV, contentVisible, isSearchExpanded, searchQuery) {
        if (isTV && contentVisible && !isSearchExpanded && searchQuery.isEmpty()) {
            delay(180)
            runCatching { firstCardFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        if (showInitialSkeleton) {
            VodSkeleton(isMobilePortrait = isMobilePortrait)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(contentRequester)
                    // ═══════════════════════════════════════════════════════
                    // CRITICAL FIX: Was `.focusRestorer { firstCardFocusRequester }`
                    //
                    // That fallback requester could become detached (row removed,
                    // search opened, scroll recycling), and Compose's 2D focus
                    // search would then crash with:
                    //   IllegalStateException: LayoutCoordinate operations
                    //   are only valid when isAttached is true
                    //
                    // The default restorer (no explicit fallback) always restores
                    // to the last-focused *attached* child, avoiding the crash.
                    // ═══════════════════════════════════════════════════════
                    .focusRestorer(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item(key = "top_spacer") {
                    Spacer(
                        modifier = Modifier.height(if (isMobilePortrait) 16.dp else 100.dp)
                    )
                }

                item(key = "gradient_transition") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, DarkNavyBackground)
                                )
                            )
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    if (searchResults.isNotEmpty()) {
                        item(key = "search_results") {
                            MediaSection(
                                title = "Search Results",
                                count = searchResults.size,
                                mediaList = searchResults,
                                firstItemModifier = Modifier.focusRequester(resultsFocusRequester),
                                isMobilePortrait = isMobilePortrait,
                                upRequester = null
                            ) { media ->
                                viewModel.selectMedia(media)
                                closeSearch()
                                navController.navigate(Routes.TMDB_DETAILS) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    } else if (showEmptyNoResults) {
                        item(key = "search_empty") {
                            EmptySearchState(query = searchQuery)
                        }
                    }
                } else {
                    mediaRows.forEachIndexed { rowIndex, row ->
                        item(key = "row_${row.key}") {
                            MediaSection(
                                title = row.title,
                                count = row.list.size,
                                mediaList = row.list,
                                firstItemModifier = if (rowIndex == 0)
                                    Modifier.focusRequester(firstCardFocusRequester)
                                else Modifier,
                                isMobilePortrait = isMobilePortrait,
                                upRequester = if (rowIndex == 0) menuRequester else null
                            ) { media ->
                                viewModel.selectMedia(media)
                                if (isSearchExpanded || searchQuery.isNotEmpty()) {
                                    closeSearch()
                                }
                                navController.navigate(Routes.TMDB_DETAILS) {
                                    launchSingleTop = true
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    item(key = "bottom_padding") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkNavyBackground.copy(alpha = 0.98f),
                            DarkNavyBackground.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (isSearchExpanded || searchQuery.isNotEmpty()) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        viewModel.search(it)
                    },
                    isFocused = isSearchFocused,
                    onFocusChange = { isSearchFocused = it },
                    onClose = closeSearch,
                    onSubmit = {
                        if (searchResults.isNotEmpty()) pendingFocus = "results"
                    },
                    focusRequester = searchFieldFocusRequester,
                    downRequester = contentRequester
                )
            } else {
                VodTopBar(
                    onMenuClick = onMenuClick,
                    onSearchClick = { isSearchExpanded = true },
                    menuRequester = menuRequester,
                    searchRequester = searchBtnRequester,
                    downRequester = contentRequester
                )
            }
        }
    }
}

@Composable
private fun VodTopBar(
    onMenuClick: (() -> Unit)?,
    onSearchClick: () -> Unit,
    menuRequester: FocusRequester,
    searchRequester: FocusRequester,
    downRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // focusGroup scopes 2D focus search to this bar — prevents
            // beyond-bounds search from walking into the LazyColumn tree.
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onMenuClick != null) {
            TvIconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(menuRequester)
                    .focusProperties { down = downRequester }
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
            modifier = Modifier
                .size(44.dp)
                .focusRequester(searchRequester)
                .focusProperties { down = downRequester }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    downRequester: FocusRequester
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text("Search movies, TV shows…", color = SearchHintText, fontSize = 16.sp)
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) WhiteText else SearchHintText,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingIcon = {
            Box(
                modifier = Modifier.size(48.dp).tvTouchClick(onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = WhiteText)
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
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSubmit() }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties { down = downRequester }
            .onFocusChanged { onFocusChange(it.hasFocus) }
    )
}

@Composable
private fun EmptySearchState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
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
}

@Composable
private fun VodSkeleton(isMobilePortrait: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (isMobilePortrait) 16.dp else 100.dp)
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

@Composable
private fun MediaSection(
    title: String,
    count: Int,
    mediaList: List<TmdbMedia>,
    firstItemModifier: Modifier = Modifier,
    isMobilePortrait: Boolean,
    upRequester: FocusRequester? = null,
    onClick: (TmdbMedia) -> Unit
) {
    val spacing = if (isMobilePortrait) 12.dp else 24.dp
    val verticalPadding = if (isMobilePortrait) 8.dp else 16.dp

    Column {
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

        LazyRow(
            // focusGroup scopes horizontal focus navigation to this row —
            // keeps beyond-bounds search from crossing into sibling rows.
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = verticalPadding)
        ) {
            itemsIndexed(
                items = mediaList,
                key = { _, media -> media.stableKey() }
            ) { index, media ->
                TmdbMediaCard(
                    media = media,
                    isMobilePortrait = isMobilePortrait,
                    modifier = (if (index == 0) firstItemModifier else Modifier)
                        .then(
                            if (upRequester != null) {
                                Modifier.focusProperties { up = upRequester }
                            } else Modifier
                        )
                ) { onClick(media) }
            }
        }
    }
}

@Composable
private fun TmdbMediaCard(
    media: TmdbMedia,
    isMobilePortrait: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cardWidth = if (isMobilePortrait) 115.dp else 180.dp
    val cardHeight = if (isMobilePortrait) 172.dp else 270.dp

    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
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
            .width(cardWidth)
            .height(cardHeight)
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
                    Icons.Default.Star,
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
