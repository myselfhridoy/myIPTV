package com.streambd.iptv.ui.screens.vod

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    BackHandler(enabled = isSearchExpanded || searchQuery.isNotEmpty()) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            isSearchExpanded = false
            searchQuery = ""
            viewModel.search("")
        }
    }
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTV = remember { context.packageManager.hasSystemFeature("android.software.leanback") }

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
            val onMediaFocus: (TmdbMedia) -> Unit = {}

            Box(modifier = Modifier.fillMaxSize()) {
                // 2. Scrollable Rows Overlay
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        val isMobilePortrait = !isTV && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                        Spacer(modifier = Modifier.height(if (isMobilePortrait) 16.dp else 100.dp))
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

        } // closes Hero Box
    } // closes else
    } // closes Background Box
        
} // closes Root Box
} // closes VodScreen

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaSection(title: String, mediaList: List<TmdbMedia>, onFocus: (TmdbMedia) -> Unit = {}, onClick: (TmdbMedia) -> Unit) {
    val configuration = LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTV = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
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
            items(items = mediaList, key = { it.id }) { media ->
                TmdbMediaCard(media, onFocus = { onFocus(media) }) { onClick(media) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TmdbMediaCard(media: TmdbMedia, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTV = remember { context.packageManager.hasSystemFeature("android.software.leanback") }
    val isMobilePortrait = !isTV && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val cardWidth = if (isMobilePortrait) 115.dp else 180.dp
    val cardHeight = if (isMobilePortrait) 172.dp else 270.dp

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
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
