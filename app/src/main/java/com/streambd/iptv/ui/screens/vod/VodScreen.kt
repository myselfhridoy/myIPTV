package com.streambd.iptv.ui.screens.vod

import androidx.compose.animation.Crossfade
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
            LaunchedEffect(trendingMovies) {
                if (focusedMedia == null && trendingMovies.isNotEmpty()) {
                    focusedMedia = trendingMovies.first()
                }
            }
            val onMediaFocus: (TmdbMedia) -> Unit = { focusedMedia = it }

            Box(modifier = Modifier.fillMaxSize()) {
                // ── Fixed Hero Banner ──
                if (searchQuery.isEmpty() && focusedMedia != null) {
                    Crossfade(targetState = focusedMedia, label = "heroCrossfade") { featured ->
                        featured?.let {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp)
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
                                            PrimaryDark.copy(alpha = 0.6f),
                                            PrimaryDark
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
                                            PrimaryDark.copy(alpha = 0.7f),
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
                            androidx.tv.material3.Button(
                                onClick = {
                                    viewModel.selectMedia(featured)
                                    navController.navigate(Routes.TMDB_DETAILS)
                                },
                                colors = androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = TealAccent,
                                    focusedContainerColor = Color(0xFF008D94)
                                ),
                                shape = androidx.tv.material3.ButtonDefaults.shape(RoundedCornerShape(24.dp)),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watch Now", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                            }
                        }
                    }
                }

                // ── Scrollable Content ──
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (searchQuery.isEmpty() && focusedMedia != null) {
                        Spacer(modifier = Modifier.height(350.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryDark)
                    ) {
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
                            unfocusedBorderColor = SurfaceDark,
                            focusedTextColor = WhiteText,
                            unfocusedTextColor = WhiteText,
                            cursorColor = TealAccent,
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark
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
                    MediaSection(title = "Search Results", mediaList = searchResults, onFocus = onMediaFocus) { media ->
                        viewModel.selectMedia(media)
                        navController.navigate(Routes.TMDB_DETAILS)
                    }
                } else if (searchQuery.isEmpty()) {
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
            } // closes Content Column
        } // closes Scrollable Column
        } // closes Hero Box
    } // closes else
    } // closes Background Box
        
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
    } // closes Menu Overlay block?
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
            horizontalArrangement = Arrangement.spacedBy(20.dp), // Official TV Card Gutter
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
            .width(124.dp) // Official 5-card layout size
            .height(186.dp) // 2:3 aspect ratio
            .onFocusChanged { if (it.isFocused) onFocus() }
            .tvTouchClick(onClick),
        colors = CardDefaults.colors(
            containerColor = SurfaceDark,
            focusedContainerColor = SurfaceDark // For images, background color change is less important
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
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
