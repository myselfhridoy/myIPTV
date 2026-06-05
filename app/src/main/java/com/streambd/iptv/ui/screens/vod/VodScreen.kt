package com.streambd.iptv.ui.screens.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
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
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.*
import com.streambd.iptv.ui.viewmodels.TmdbViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
        ) {
            if (isLoading && trendingMovies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // ── Hero Banner ──
                if (searchQuery.isEmpty() && trendingMovies.isNotEmpty()) {
                    val featured = trendingMovies.first()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        // Backdrop Image
                        AsyncImage(
                            model = featured.backdropUrl.ifEmpty { featured.posterUrl },
                            contentDescription = featured.displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Gradient Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFF0D1117).copy(alpha = 0.6f),
                                            Color(0xFF0D1117)
                                        ),
                                        startY = 80f
                                    )
                                )
                        )
                        // Left Gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF0D1117).copy(alpha = 0.7f),
                                            Color.Transparent
                                        ),
                                        endX = 400f
                                    )
                                )
                        )
                        // Hero Content
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = featured.displayTitle,
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (featured.vote_average != null && featured.vote_average > 0) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format("%.1f", featured.vote_average),
                                        color = Color(0xFFFFD700),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(
                                    text = if (featured.isMovie) "Movie" else "TV Show",
                                    color = TealAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (featured.displayDate.isNotEmpty()) {
                                    Text(
                                        text = " • ${featured.displayDate.take(4)}",
                                        color = Color.LightGray,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            // Watch Now CTA
                            var ctaFocused by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .onFocusChanged { ctaFocused = it.isFocused }
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable {
                                        viewModel.selectMedia(featured)
                                        navController.navigate(Routes.TMDB_DETAILS)
                                    }
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(TealAccent, Color(0xFF008D94))
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .border(
                                        width = if (ctaFocused) 2.dp else 0.dp,
                                        color = if (ctaFocused) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watch Now", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ── Search Bar ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    var isSearchFocused by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.search(it)
                        },
                        placeholder = { Text("Search movies, TV shows...", color = Color(0xFF555555)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = if (isSearchFocused) TealAccent else Color(0xFF555555))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealAccent,
                            unfocusedBorderColor = Color(0xFF2B3648),
                            focusedTextColor = WhiteText,
                            unfocusedTextColor = WhiteText,
                            cursorColor = TealAccent,
                            focusedContainerColor = Color(0xFF151A25),
                            unfocusedContainerColor = Color(0xFF151A25)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isSearchFocused = it.isFocused }
                    )
                }

                // ── Content Sections ──
                if (searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
                    MediaSection(title = "Search Results", mediaList = searchResults) { media ->
                        viewModel.selectMedia(media)
                        navController.navigate(Routes.TMDB_DETAILS)
                    }
                } else if (searchQuery.isEmpty()) {
                    if (trendingMovies.isNotEmpty()) {
                        MediaSection(
                            title = "🔥 Trending Movies",
                            mediaList = trendingMovies.drop(1) // Skip the hero item
                        ) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (trendingTv.isNotEmpty()) {
                        MediaSection(title = "📺 Trending TV Shows", mediaList = trendingTv) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (hindiMovies.isNotEmpty()) {
                        MediaSection(title = "🎬 Bollywood Hits", mediaList = hindiMovies) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (hindiTv.isNotEmpty()) {
                        MediaSection(title = "📺 Hindi TV Shows", mediaList = hindiTv) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (southMovies.isNotEmpty()) {
                        MediaSection(title = "🍿 South Indian Blockbusters", mediaList = southMovies) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (southTv.isNotEmpty()) {
                        MediaSection(title = "📺 South Indian TV Shows", mediaList = southTv) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (banglaMovies.isNotEmpty()) {
                        MediaSection(title = "🎥 Bengali Movies", mediaList = banglaMovies) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (banglaTv.isNotEmpty()) {
                        MediaSection(title = "📺 Bengali TV Shows", mediaList = banglaTv) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
        
    // Menu Overlay
        if (onMenuClick != null && searchQuery.isEmpty()) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
            }
        }
    }
}

@Composable
fun MediaSection(title: String, mediaList: List<TmdbMedia>, onClick: (TmdbMedia) -> Unit) {
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
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(mediaList) { media ->
                TmdbMediaCard(media) { onClick(media) }
            }
        }
    }
}

@Composable
fun TmdbMediaCard(media: TmdbMedia, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(200),
        label = "cardScale"
    )

    Card(
        modifier = Modifier
            .width(135.dp)
            .height(220.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) TealAccent else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 12.dp else 4.dp
        ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230))
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
                fontSize = 12.sp,
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
