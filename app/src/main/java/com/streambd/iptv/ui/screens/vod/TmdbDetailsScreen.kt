package com.streambd.iptv.ui.screens.vod

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.streambd.iptv.ui.components.tvFocusBorder
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.*
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel

@Composable
fun TmdbDetailsScreen(
    @Suppress("UNUSED_PARAMETER") mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController
) {
    val media by tmdbViewModel.selectedMedia.collectAsState()

    val backButtonFocusRequester = remember { FocusRequester() }
    val watchButtonFocusRequester = remember { FocusRequester() }
    val tvDetails by tmdbViewModel.selectedTvDetails.collectAsState()
    val movieDetails by tmdbViewModel.selectedMovieDetails.collectAsState()

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LaunchedEffect(media) {
        if (media != null) {
            try {
                watchButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore
            }
            if (media?.isMovie == true) {
                tmdbViewModel.fetchMovieDetails(media!!.id)
            } else {
                tmdbViewModel.fetchTvDetails(media!!.id)
            }
        }
    }

    if (media == null) return

    val m = media!!
    val genres = if (m.isMovie) movieDetails?.genres else tvDetails?.genres
    val tagline = if (m.isMovie) movieDetails?.tagline else tvDetails?.tagline
    val runtime = movieDetails?.runtime
    val totalSeasons = tvDetails?.number_of_seasons

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D14)) // Deep Navy Background
    ) {
        // ── Immersive Full-Screen Backdrop ──
        AsyncImage(
            model = m.backdropUrl.ifEmpty { m.posterUrl },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f) // Cover top 80%
                .align(Alignment.TopCenter)
        )
        // Strong Gradient to blend backdrop into background and darken for text
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF090D14).copy(alpha = 0.2f),
                            Color(0xFF090D14).copy(alpha = 0.6f),
                            Color(0xFF090D14).copy(alpha = 0.95f),
                            Color(0xFF090D14)
                        ),
                        startY = 0f
                    )
                )
        )
        // Left gradient for text readability (only prominent in landscape)
        if (!isPortrait) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF090D14).copy(alpha = 0.9f),
                                Color(0xFF090D14).copy(alpha = 0.4f),
                                Color.Transparent
                            ),
                            endX = 1000f
                        )
                    )
            )
        }

        // ── Scrollable Content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(if (isPortrait) 100.dp else 180.dp)) // Push content down over the backdrop

            val posterContent: @Composable () -> Unit = {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
                    modifier = Modifier
                        .width(if (isPortrait) 160.dp else 180.dp)
                        .height(if (isPortrait) 240.dp else 270.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = m.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            val infoContent: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier = modifier.padding(top = if (isPortrait) 0.dp else 16.dp),
                    horizontalAlignment = if (isPortrait) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    // Title
                    Text(
                        text = m.displayTitle,
                        color = Color.White,
                        fontSize = if (isPortrait) 30.sp else 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = if (isPortrait) TextAlign.Center else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Tagline
                    if (!tagline.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\"$tagline\"",
                            color = TealAccent.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            textAlign = if (isPortrait) TextAlign.Center else TextAlign.Start
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metadata Row
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = if (isPortrait) Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Rating
                        if (m.vote_average != null && m.vote_average > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", m.vote_average),
                                    color = Color(0xFFFFD700),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Year
                        if (m.displayDate.isNotEmpty()) {
                            Text(
                                text = m.displayDate.take(4),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Runtime or Seasons
                        if (m.isMovie && runtime != null && runtime > 0) {
                            Text(
                                text = "${runtime / 60}h ${runtime % 60}m",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (!m.isMovie && totalSeasons != null && totalSeasons > 0) {
                            Text(
                                text = "$totalSeasons Seasons",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Type Badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (m.isMovie) Icons.Default.Movie else Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (m.isMovie) "Movie" else "TV Show",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Genres Row
                    if (!genres.isNullOrEmpty()) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = if (isPortrait) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            genres.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(Color(0xFF1E2633).copy(alpha = 0.8f))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = genre.name,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Action Row Section ──
                    var localSeason by remember { mutableStateOf<Int?>(null) }
                    var localEpisode by remember { mutableStateOf<Int?>(null) }
                    var expandedSeason by remember { mutableStateOf(false) }
                    var expandedEpisode by remember { mutableStateOf(false) }

                    val validSeasons = if (!m.isMovie && tvDetails != null) {
                        tvDetails!!.seasons.filter { it.season_number > 0 }
                    } else emptyList()
                    val selectedSeasonObj = validSeasons.find { it.season_number == localSeason } ?: validSeasons.firstOrNull()

                    if (validSeasons.isNotEmpty() && localSeason == null) {
                        localSeason = validSeasons.first().season_number
                    }
                    if (localEpisode == null || (selectedSeasonObj != null && localEpisode!! > selectedSeasonObj.episode_count)) {
                        localEpisode = 1
                    }

                    var watchBtnFocused by remember { mutableStateOf(false) }
                    val buttonScale by animateFloatAsState(
                        targetValue = if (watchBtnFocused) 1.05f else 1f,
                        animationSpec = tween(durationMillis = 180),
                        label = "watchBtnScale"
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = if (isPortrait) Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Watch Now Button
                        Row(
                            modifier = Modifier
                                .focusRequester(watchButtonFocusRequester)
                                .focusProperties {
                                    up = backButtonFocusRequester
                                }
                                .onFocusChanged { watchBtnFocused = it.isFocused }
                                .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }
                                .shadow(
                                    elevation = if (watchBtnFocused) 20.dp else 8.dp,
                                    shape = RoundedCornerShape(50.dp),
                                    spotColor = TealAccent,
                                    ambientColor = TealAccent
                                )
                                .clip(RoundedCornerShape(50.dp))
                                .clickable {
                                    mainViewModel.selectedSeason = localSeason
                                    mainViewModel.selectedEpisode = localEpisode
                                    navController.navigate(Routes.SOURCE_SELECTION)
                                }
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = if (watchBtnFocused)
                                            listOf(TealAccent, Color(0xFF00C6FF))
                                        else
                                            listOf(Color(0xFF0D5D63), Color(0xFF008D94))
                                    ),
                                    shape = RoundedCornerShape(50.dp)
                                )
                                .border(
                                    width = if (watchBtnFocused) 2.dp else 0.dp,
                                    color = if (watchBtnFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(50.dp)
                                )
                                .padding(horizontal = 28.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }

                        // Glassmorphic Season Pill
                        if (!m.isMovie && validSeasons.isNotEmpty()) {
                            var seasonPillFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { seasonPillFocused = it.isFocused }
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Color.White.copy(alpha = if (seasonPillFocused) 0.25f else 0.1f))
                                    .border(
                                        1.dp,
                                        if (seasonPillFocused) Color.White else Color.White.copy(alpha = 0.15f),
                                        RoundedCornerShape(50.dp)
                                    )
                                    .clickable { expandedSeason = true }
                                    .tvFocusBorder(RoundedCornerShape(50.dp))
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Season $localSeason", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (expandedSeason) {
                                Dialog(onDismissRequest = { expandedSeason = false }) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151A25)),
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                            Text("Select Season", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                                items(validSeasons) { season ->
                                                    val isSelected = localSeason == season.season_number
                                                    Text(
                                                        text = "Season ${season.season_number}",
                                                        color = if (isSelected) TealAccent else Color.White,
                                                        fontSize = 16.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { localSeason = season.season_number; localEpisode = 1; expandedSeason = false }
                                                            .background(if (isSelected) TealAccent.copy(alpha = 0.12f) else Color.Transparent)
                                                            .padding(16.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Glassmorphic Episode pill
                            var episodePillFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { episodePillFocused = it.isFocused }
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Color.White.copy(alpha = if (episodePillFocused) 0.25f else 0.1f))
                                    .border(
                                        1.dp,
                                        if (episodePillFocused) Color.White else Color.White.copy(alpha = 0.15f),
                                        RoundedCornerShape(50.dp)
                                    )
                                    .clickable { expandedEpisode = true }
                                    .tvFocusBorder(RoundedCornerShape(50.dp))
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Ep $localEpisode", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (expandedEpisode && selectedSeasonObj != null) {
                                Dialog(onDismissRequest = { expandedEpisode = false }) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151A25)),
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                            Text("Select Episode", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                                items((1..selectedSeasonObj.episode_count).toList()) { ep ->
                                                    val isSelected = localEpisode == ep
                                                    Text(
                                                        text = "Episode $ep",
                                                        color = if (isSelected) TealAccent else Color.White,
                                                        fontSize = 16.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { localEpisode = ep; expandedEpisode = false }
                                                            .background(if (isSelected) TealAccent.copy(alpha = 0.12f) else Color.Transparent)
                                                            .padding(16.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // ── Overview Section ──
                    if (!m.overview.isNullOrEmpty()) {
                        Text(
                            text = "Overview",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = if (isPortrait) TextAlign.Center else TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = m.overview.orEmpty(),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            textAlign = if (isPortrait) TextAlign.Center else TextAlign.Start,
                            modifier = if (isPortrait) Modifier.fillMaxWidth() else Modifier.padding(end = 32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(60.dp)) // Bottom padding
                }
            }

            // Decide layout based on orientation
            if (isPortrait) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    posterContent()
                    Spacer(modifier = Modifier.height(24.dp))
                    infoContent(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    posterContent()
                    Spacer(modifier = Modifier.width(32.dp))
                    infoContent(Modifier.weight(1f))
                }
            }
        }

        // Fixed Back Button on Top Left
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier
                .padding(if (isPortrait) 16.dp else 32.dp)
                .size(if (isPortrait) 40.dp else 48.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .focusRequester(backButtonFocusRequester)
                .focusProperties {
                    down = watchButtonFocusRequester
                    right = watchButtonFocusRequester
                }
                .tvFocusBorder(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .clickable { navController.popBackStack() }
                .padding(12.dp)
        )
    }
}
