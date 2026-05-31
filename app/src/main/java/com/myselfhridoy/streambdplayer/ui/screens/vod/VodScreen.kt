package com.myselfhridoy.streambdplayer.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.myselfhridoy.streambdplayer.data.models.TmdbMedia
import com.myselfhridoy.streambdplayer.ui.navigation.Routes
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavyBackground
import com.myselfhridoy.streambdplayer.ui.theme.TealAccent
import com.myselfhridoy.streambdplayer.ui.theme.WhiteText
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodScreen(viewModel: TmdbViewModel, navController: NavController) {
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val trendingTv by viewModel.trendingTv.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .padding(16.dp)
    ) {
        // Search Bar
        var isSearchFocused by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                viewModel.search(it)
            },
            placeholder = { Text("Search movies or TV shows...", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = WhiteText,
                unfocusedTextColor = WhiteText,
                cursorColor = TealAccent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isSearchFocused = it.isFocused }
                .border(
                    width = if (isSearchFocused) 2.dp else 0.dp,
                    color = if (isSearchFocused) TealAccent else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (searchQuery.isNotEmpty() && searchResults.isNotEmpty()) {
                    Text(
                        text = "Search Results",
                        color = WhiteText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    TmdbMediaRow(searchResults) { media ->
                        viewModel.selectMedia(media)
                        navController.navigate(Routes.TMDB_DETAILS)
                    }
                } else if (searchQuery.isEmpty()) {
                    if (trendingMovies.isNotEmpty()) {
                        Text(
                            text = "Trending Movies",
                            color = WhiteText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        TmdbMediaRow(trendingMovies) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (trendingTv.isNotEmpty()) {
                        Text(
                            text = "Trending TV Shows",
                            color = WhiteText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        TmdbMediaRow(trendingTv) { media ->
                            viewModel.selectMedia(media)
                            navController.navigate(Routes.TMDB_DETAILS)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TmdbMediaRow(mediaList: List<TmdbMedia>, onClick: (TmdbMedia) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    ) {
        items(mediaList) { media ->
            TmdbMediaCard(media) { onClick(media) }
        }
    }
}

@Composable
fun TmdbMediaCard(media: TmdbMedia, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(240.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) TealAccent else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = media.posterUrl,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 100f
                        )
                    )
            )

            // Title
            Text(
                text = media.displayTitle,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .fillMaxWidth()
            )
        }
    }
}
