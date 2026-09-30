package com.streambd.iptv.ui.screens.vod

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.tv.material3.Glow
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.streambd.iptv.data.models.TmdbMedia
import com.streambd.iptv.ui.components.tvFocusBorder
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.*
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────
// TODO: Move these to Theme.kt (consolidate hardcoded colors)
// ─────────────────────────────────────────────────────────────
private val SearchHintText = Color(0xFF999999)          // was Color(0xFF999999)
private val SearchContainerFocused = Color(0xFF333333)  // was Color(0xFF333333)
private val SearchContainerIdle = Color(0xFF1E1E1E)     // was Color(0xFF1E1E1E)
private val StarGold = Color(0xFFFFD700)                // was Color(0xFFFFD700)
private val SkeletonBase = Color.White                   // shimmer uses this with alpha

// ─────────────────────────────────────────────────────────────
// Data class to eliminate copy-pasted row blocks
// ─────────────────────────────────────────────────────────────
private data class MediaRow(
    val key: String,
    val title: String,
    val list: List<TmdbMedia>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
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

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Fix #3: FocusRequester for the search field so D-pad users land there automatically
    val searchFieldFocusRequester = remember { FocusRequester() }

    // Option A: focus requesters for two-step back navigation
    val resultsFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }
    // "results" or "home" — where focus should go next
    var pendingFocus by remember { mutableStateOf<String?>(null) }

    // ─────────────────────────────────────────────────────────────
    // BackHandler (Option A): two-step back, but each step lands
    // the focus somewhere meaningful.
    //   1st back (typing)  → first search-result card
    //   2nd back (search)  → first home-row card
    // ─────────────────────────────────────────────────────────────
    BackHandler(enabled = isSearchExpanded || searchQuery.isNotEmpty()) {
        if (isSearchFocused) {
            // 1st back: end typing, move focus to first result card
            focusManager.clearFocus()
            pendingFocus = "results"
        } else {
            // 2nd back: close search, restore focus to home
            isSearchExpanded = false
            searchQuery = ""
            viewModel.search("")
            pendingFocus = "home"
        }
    }

    // Fix #3: request focus on search field when it opens
    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            try {
                searchFieldFocusRequester.requestFocus()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    // Option A: apply pending focus when list state changes.
    // Note: after search closes, the home rows need to compose before we can
    // request focus on them — hence the small delay.
    LaunchedEffect(pendingFocus, searchResults) {
        when (pendingFocus) {
            "results" -> {
                if (searchResults.isNotEmpty()) {
                    delay(50) // let the card compose
                    try {
                        resultsFocusRequester.requestFocus()
                    } catch (_: Exception) {
                        // Ignore
                    }
                }
                pendingFocus = null
            }
            "home" -> {
                delay(100) // home rows re-compose after search closes
                try {
                    firstRowFocusRequester.requestFocus()
                } catch (_: Exception) {
                    // Ignore
                }
                pendingFocus = null
            }
            else -> Unit
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }

    // Improvement: single source of truth for rows (avoids 8 copy-pasted blocks)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
    ) {
        val showInitialSkeleton = isLoading && trendingMovies.isEmpty()
        val showEmptyNoResults = searchQuery.isNotEmpty() &&
                searchResults.isEmpty() && !isLoading

        if (showInitialSkeleton) {
            // Skeleton rows instead of plain spinner (premium feel)
            VodSkeleton(isTV = isTV)
        } else {
            val onMediaFocus: (TmdbMedia) -> Unit = {}

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Top spacer for the fixed nav bar
                item(key = "top_spacer") {
                    val isMobilePortrait =
                        !isTV && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    Spacer(
                        modifier = Modifier.height(if (isMobilePortrait) 16.dp else 100.dp)
                    )
                }

                // Gradient transition into rows
                item(key = "gradient_transition") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, PrimaryDark)
                                )
                            )
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    if (searchResults.isNotEmpty()) {
                        item(key = "search_results") {
                            MediaSection(
                                title = "Search Results",
                                mediaList = searchResults,
                                // Option A: first result gets focus when 1st back pressed
                                firstItemModifier = Modifier.focusRequester(resultsFocusRequester),
                                onFocus = onMediaFocus
                            ) { media ->
                                viewModel.selectMedia(media)
                                navController.navigate(Routes.TMDB_DETAILS)
                            }
                        }
                    } else if (showEmptyNoResults) {
                        item(key = "search_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No results found for \"$searchQuery\"",
                                    // Contrast: was Color.Gray
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                } else {
                    // Each row is its own LazyColumn item → true lazy loading
                    mediaRows.forEachIndexed { rowIndex, row ->
                        item(key = "row_${row.key}") {
                            MediaSection(
                                title = row.title,
                                mediaList = row.list,
                                // Option A: only first row gets the "home" focus requester
                                firstItemModifier = if (rowIndex == 0)
                                    Modifier.focusRequester(firstRowFocusRequester)
                                else Modifier,
                                onFocus = onMediaFocus
                            ) { media ->
                                viewModel.selectMedia(media)
                                navController.navigate(Routes.TMDB_DETAILS)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    item(key = "bottom_padding") {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        // ── Overlay Top Navigation Bar (Fixed on Top) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (isSearchExpanded || searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.search(it)
                    },
                    placeholder = {
                        Text(
                            "Search movies, TV shows...",
                            color = SearchHintText,
                            fontSize = 16.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isSearchFocused) Color.White else SearchHintText,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingIcon = {
                        val closeSearch = {
                            isSearchExpanded = false
                            searchQuery = ""
                            viewModel.search("")
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .tvFocusBorder(CircleShape)
                                .tvTouchClick(closeSearch)
                                .clickable { closeSearch() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedContainerColor = SearchContainerFocused.copy(alpha = 0.9f),
                        unfocusedContainerColor = SearchContainerIdle.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Fix #3: attach the focus requester
                        .focusRequester(searchFieldFocusRequester)
                        .onFocusChanged { isSearchFocused = it.isFocused }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onMenuClick != null) {
                        androidx.compose.material3.IconButton(onClick = { onMenuClick() }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White,
                                modifier = Modifier
                                    .tvFocusBorder(CircleShape)
                                    .background(Color.Transparent, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    Text(
                        text = "Media",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.IconButton(onClick = { isSearchExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier
                                .tvFocusBorder(CircleShape)
                                .background(Color.Transparent, CircleShape)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Skeleton / Shimmer (premium loading experience)
// ─────────────────────────────────────────────────────────────
@Composable
private fun VodSkeleton(isTV: Boolean) {
    val isMobilePortrait = !isTV && LocalConfiguration.current.orientation ==
            Configuration.ORIENTATION_PORTRAIT

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
        // Section header placeholder
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaSection(
    title: String,
    mediaList: List<TmdbMedia>,
    // Option A: applied to the first card only, so callers can attach a FocusRequester
    firstItemModifier: Modifier = Modifier,
    onFocus: (TmdbMedia) -> Unit = {},
    onClick: (TmdbMedia) -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }
    val isMobilePortrait = !isTV && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val spacing = if (isMobilePortrait) 12.dp else 24.dp
    val verticalPadding = if (isMobilePortrait) 8.dp else 20.dp

    Column {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TealAccent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = WhiteText,
                fontSize = if (isMobilePortrait) 17.sp else 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = verticalPadding)
        ) {
            // Option A: itemsIndexed so the first card can receive firstItemModifier
            itemsIndexed(
                items = mediaList,
                key = { _, media -> media.id }
            ) { index, media ->
                TmdbMediaCard(
                    media = media,
                    modifier = if (index == 0) firstItemModifier else Modifier,
                    onFocus = { onFocus(media) }
                ) { onClick(media) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbMediaCard(
    media: TmdbMedia,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }
    val isMobilePortrait = !isTV && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val cardWidth = if (isMobilePortrait) 115.dp else 180.dp
    val cardHeight = if (isMobilePortrait) 172.dp else 270.dp

    Card(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .onFocusChanged { if (it.isFocused) onFocus() }
            // tvTouchClick ensures phone touch works too; Card.onClick handles TV OK.
            .tvTouchClick(onClick),
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = SurfaceDark
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.Black.copy(alpha = 0.6f),
                elevation = 24.dp
            )
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                inset = 0.dp,
                shape = RoundedCornerShape(12.dp)
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    .height(100.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Rating Badge
            if (media.vote_average != null && media.vote_average > 0) {
                Row(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = String.format("%.1f", media.vote_average),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Title
            Text(
                text = media.displayTitle,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillMaxWidth()
            )
        }
    }
}
