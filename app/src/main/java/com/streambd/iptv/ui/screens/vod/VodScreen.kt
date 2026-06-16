package com.streambd.iptv.ui.screens.vod

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
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
import androidx.tv.material3.Text
import androidx.tv.material3.Glow
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun VodScreen(viewModel: TmdbViewModel, navController: NavController, onMenuClick: (() -> Unit)? = null) {
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
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PrimaryDark)
        ) {
            if (isLoading && trendingMovies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else {
            var focusedMedia by remember { mutableStateOf<TmdbMedia?>(null) }
            var initialFocusSet by remember(isLandscape) { mutableStateOf(false) }
            val playButtonFocusRequester = remember { FocusRequester() }

            LaunchedEffect(trendingMovies) {
                if (focusedMedia == null && trendingMovies.isNotEmpty()) {
                    focusedMedia = trendingMovies.first()
                }
            }

            LaunchedEffect(focusedMedia) {
                if (!initialFocusSet && focusedMedia != null && isLandscape) {
                    kotlinx.coroutines.delay(300)
                    try { playButtonFocusRequester.requestFocus() } catch (e: Exception) {}
                    initialFocusSet = true
                }
            }

            val onMediaFocus: (TmdbMedia) -> Unit = remember { { focusedMedia = it } }

            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Fixed Hero Banner in Background
                if (searchQuery.isEmpty() && focusedMedia != null) {
                    Crossfade(
                        targetState = focusedMedia,
                        modifier = Modifier.focusGroup().focusRequester(playButtonFocusRequester),
                        label = "heroCrossfade"
                    ) { featured ->
                        featured?.let {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isLandscape) 450.dp else 500.dp) // Netflix-style taller banner
                            ) {
                                // Backdrop Image
                                AsyncImage(
                                    model = featured.backdropUrl.ifEmpty { featured.posterUrl },
                                    contentDescription = featured.displayTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Gradient Overlay (Bottom)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    PrimaryDark.copy(alpha = 0.5f),
                                                    PrimaryDark
                                                ),
                                                startY = 100f
                                            )
                                        )
                                )
                                // Gradient Overlay (Left)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    PrimaryDark.copy(alpha = 0.9f),
                                                    PrimaryDark.copy(alpha = 0.6f),
                                                    Color.Transparent
                                                ),
                                                endX = 600f
                                            )
                                        )
                                )
                                // Hero Content
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(if (isLandscape) 0.5f else 1f) // Take up left half on TV, full on mobile
                                        .padding(
                                            start = if (isLandscape) 58.dp else 24.dp,
                                            end = if (isLandscape) 0.dp else 24.dp,
                                            bottom = if (isLandscape) 40.dp else 32.dp
                                        ), // align with general tv margins
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text(
                                        text = featured.displayTitle,
                                        color = Color.White,
                                        fontSize = if (isLandscape) 42.sp else 32.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = if (isLandscape) 2 else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = if (isLandscape) 48.sp else 36.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    // Metadata Row
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (featured.vote_average != null && featured.vote_average > 0) {
                                            Text(
                                                text = "${(featured.vote_average * 10).toInt()}% Match",
                                                color = Color(0xFF46D369), // Netflix Match Green
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        if (featured.displayDate.isNotEmpty()) {
                                            Text(
                                                text = featured.displayDate.take(4),
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Transparent, RoundedCornerShape(2.dp))
                                                .border(1.dp, Color.Gray, RoundedCornerShape(2.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (featured.isMovie) "MOVIE" else "TV",
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Transparent, RoundedCornerShape(2.dp))
                                                .border(1.dp, Color.Gray, RoundedCornerShape(2.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "HD",
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    // Overview
                                    if (!featured.overview.isNullOrEmpty()) {
                                        Text(
                                            text = featured.overview,
                                            color = Color(0xFFCCCCCC),
                                            fontSize = 14.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 20.sp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    
                                    // Netflix Style Action Buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        val playAction = {
                                            viewModel.selectMedia(featured)
                                            if (featured.isMovie) {
                                                navController.navigate(Routes.SOURCE_SELECTION)
                                            } else {
                                                navController.navigate(Routes.TMDB_DETAILS)
                                            }
                                        }
                                        androidx.tv.material3.Button(
                                            onClick = playAction,
                                            modifier = Modifier.tvTouchClick(playAction),
                                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                                containerColor = Color.White,
                                                contentColor = Color.Black,
                                                focusedContainerColor = Color.LightGray,
                                                focusedContentColor = Color.Black
                                            ),
                                            shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(4.dp)),
                                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Play", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                        
                                        val moreInfoAction = {
                                            viewModel.selectMedia(featured)
                                            navController.navigate(Routes.TMDB_DETAILS)
                                        }
                                        androidx.tv.material3.Button(
                                            onClick = moreInfoAction,
                                            modifier = Modifier.tvTouchClick(moreInfoAction),
                                            colors = androidx.tv.material3.ButtonDefaults.colors(
                                                containerColor = Color(0x66333333),
                                                contentColor = Color.White,
                                                focusedContainerColor = Color.White,
                                                focusedContentColor = Color.Black
                                            ),
                                            shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(4.dp)),
                                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                        ) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("More Info", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Scrollable Rows Overlay
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        if (searchQuery.isEmpty() && focusedMedia != null) {
                            // Transparent spacer to reveal the Hero banner behind
                            Spacer(modifier = Modifier.height(if (isLandscape) 450.dp else 500.dp))
                        } else if (searchQuery.isNotEmpty()) {
                            // Spacer to push search results below Top Nav Bar
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }

                    // Media Rows
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Gradient transition from transparent to background
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
                            // Solid background for rows
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PrimaryDark)
                            ) {
                        // ── Content Sections ──
                        if (searchQuery.isNotEmpty()) {
                            if (searchResults.isNotEmpty()) {
                                MediaSection(title = "Search Results", mediaList = searchResults, onFocus = onMediaFocus) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            } else if (!isLoading) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("No results found for \"$searchQuery\"", color = Color.Gray, fontSize = 18.sp)
                                }
                            }
                        } else {
                            if (trendingMovies.isNotEmpty()) {
                                MediaSection(
                                    title = "Trending Movies",
                                    mediaList = trendingMovies,
                                    onFocus = onMediaFocus
                                ) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (trendingTv.isNotEmpty()) {
                                MediaSection(title = "Trending TV Shows", mediaList = trendingTv, onFocus = onMediaFocus) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (hindiMovies.isNotEmpty()) {
                                MediaSection(title = "Bollywood Hits", mediaList = hindiMovies, onFocus = onMediaFocus) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (hindiTv.isNotEmpty()) {
                                MediaSection(title = "Hindi TV Shows", mediaList = hindiTv, onFocus = onMediaFocus) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (southMovies.isNotEmpty()) {
                                MediaSection(title = "South Indian Blockbusters", mediaList = southMovies, onFocus = onMediaFocus) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (southTv.isNotEmpty()) {
                                MediaSection(title = "South Indian TV Shows", mediaList = southTv, onFocus = onMediaFocus) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (banglaMovies.isNotEmpty()) {
                                MediaSection(title = "Bengali Movies", mediaList = banglaMovies, onFocus = onMediaFocus) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (banglaTv.isNotEmpty()) {
                                MediaSection(title = "Bengali TV Shows", mediaList = banglaTv, onFocus = onMediaFocus) { media ->
                                    viewModel.selectMedia(media)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        } // closes else-if
                    } // closes inner Content Column
                    } // closes outer Column
                    } // closes item
                } // closes Scrollable Column

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
                            placeholder = { Text("Search movies, TV shows...", color = Color(0xFF999999), fontSize = 16.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = if (isSearchFocused) Color.White else Color(0xFF999999), modifier = Modifier.size(24.dp))
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
                                focusedContainerColor = Color(0xFF333333).copy(alpha = 0.9f),
                                unfocusedContainerColor = Color(0xFF1E1E1E).copy(alpha = 0.9f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
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
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
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
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }

        } // closes Hero Box
    } // closes else
    } // closes Background Box
        
} // closes Root Box
} // closes VodScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaSection(title: String, mediaList: List<TmdbMedia>, onFocus: (TmdbMedia) -> Unit = {}, onClick: (TmdbMedia) -> Unit) {
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
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp), // Official TV Card Gutter
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
        ) {
            items(mediaList) { media ->
                TmdbMediaCard(media, onFocus = { onFocus(media) }) { onClick(media) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbMediaCard(media: TmdbMedia, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(180.dp) // Official 5-card layout size
            .height(270.dp) // 2:3 aspect ratio
            .onFocusChanged { if (it.isFocused) onFocus() }
            .tvTouchClick(onClick),
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = SurfaceDark // For images, background color change is less important
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        glow = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 24.dp)
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
                        tint = Color(0xFFFFD700),
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
