package com.myselfhridoy.streambdplayer.ui.screens.vod

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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.myselfhridoy.streambdplayer.ui.components.tvFocusBorder
import com.myselfhridoy.streambdplayer.ui.navigation.Routes
import com.myselfhridoy.streambdplayer.ui.theme.*
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel

@Composable
fun TmdbDetailsScreen(
    @Suppress("UNUSED_PARAMETER") mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController
) {
    val media by tmdbViewModel.selectedMedia.collectAsState()

    val watchButtonFocusRequester = remember { FocusRequester() }
    val tvDetails by tmdbViewModel.selectedTvDetails.collectAsState()

    LaunchedEffect(media) {
        if (media != null) {
            try {
                watchButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request error during initial composition
            }
            if (media?.isMovie == false) {
                tmdbViewModel.fetchTvDetails(media!!.id)
            }
        }
    }

    if (media == null) return

    val m = media!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .verticalScroll(rememberScrollState())
    ) {
        // ── Backdrop Header ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            AsyncImage(
                model = m.backdropUrl.ifEmpty { m.posterUrl },
                contentDescription = m.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Bottom gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D1117).copy(alpha = 0.3f),
                                Color(0xFF0D1117).copy(alpha = 0.7f),
                                Color(0xFF0D1117)
                            ),
                            startY = 100f
                        )
                    )
            )

            // Back Button
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .tvFocusBorder(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .clickable { navController.popBackStack() }
                    .padding(8.dp)
            )
        }

        // ── Content Section ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset(y = (-40).dp)
        ) {
            // Poster
            Card(
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .width(140.dp)
                    .height(210.dp)
            ) {
                AsyncImage(
                    model = m.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Info Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 44.dp)
            ) {
                Text(
                    text = m.displayTitle,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Metadata Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Type Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TealAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (m.isMovie) Icons.Default.Movie else Icons.Default.Tv,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (m.isMovie) "Movie" else "TV Show",
                            color = TealAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Rating
                    if (m.vote_average != null && m.vote_average > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format("%.1f", m.vote_average),
                                color = Color(0xFFFFD700),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (m.vote_count != null && m.vote_count > 0) {
                                Text(
                                    text = " (${m.vote_count})",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Year
                    if (m.displayDate.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = m.displayDate.take(4),
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                var localSeason by remember { mutableStateOf<Int?>(null) }
                var localEpisode by remember { mutableStateOf<Int?>(null) }
                
                var expandedSeason by remember { mutableStateOf(false) }
                var expandedEpisode by remember { mutableStateOf(false) }

                // Watch Now Button
                var watchBtnFocused by remember { mutableStateOf(false) }
                val buttonScale by animateFloatAsState(
                    targetValue = if (watchBtnFocused) 1.05f else 1f,
                    animationSpec = tween(durationMillis = 200),
                    label = "watchBtnScale"
                )

                Row(
                    modifier = Modifier
                        .focusRequester(watchButtonFocusRequester)
                        .onFocusChanged { watchBtnFocused = it.isFocused }
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                        .shadow(
                            elevation = if (watchBtnFocused) 16.dp else 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = TealAccent,
                            ambientColor = TealAccent
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            mainViewModel.selectedSeason = localSeason
                            mainViewModel.selectedEpisode = localEpisode
                            navController.navigate(Routes.SOURCE_SELECTION)
                        }
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = if (watchBtnFocused) {
                                    listOf(TealAccent, Color(0xFF00C6FF))
                                } else {
                                    listOf(Color(0xFF0D5D63), Color(0xFF008D94))
                                }
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = if (watchBtnFocused) 2.dp else 0.dp,
                            color = if (watchBtnFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow, 
                        contentDescription = null, 
                        tint = Color.White, 
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Watch Now", 
                        color = Color.White, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
                
                // Season and Episode Pickers for TV Shows
                if (!m.isMovie && tvDetails != null && tvDetails!!.seasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    val validSeasons = tvDetails!!.seasons.filter { it.season_number > 0 }
                    
                    if (validSeasons.isNotEmpty()) {
                        if (localSeason == null) {
                            localSeason = validSeasons.first().season_number
                        }
                        
                        val selectedSeasonObj = validSeasons.find { it.season_number == localSeason } ?: validSeasons.first()
                        
                        if (localEpisode == null || localEpisode!! > selectedSeasonObj.episode_count) {
                            localEpisode = 1
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Season Picker
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "SEASON", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1A2232))
                                        .border(1.dp, Color(0xFF2B3648), RoundedCornerShape(12.dp))
                                        .clickable { expandedSeason = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .tvFocusBorder(RoundedCornerShape(12.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Season ${localSeason}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TealAccent)
                                    }
                                    if (expandedSeason) {
                                        Dialog(onDismissRequest = { expandedSeason = false }) {
                                            Card(
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151A25)),
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                                    Text(
                                                        text = "Select Season", 
                                                        color = Color.White, 
                                                        fontSize = 18.sp, 
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                                        items(validSeasons) { season ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        localSeason = season.season_number
                                                                        localEpisode = 1
                                                                        expandedSeason = false
                                                                    }
                                                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(if (localSeason == season.season_number) TealAccent.copy(alpha = 0.1f) else Color.Transparent)
                                                                    .tvFocusBorder()
                                                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Season ${season.season_number}",
                                                                    color = if (localSeason == season.season_number) TealAccent else Color.White,
                                                                    fontSize = 16.sp,
                                                                    fontWeight = if (localSeason == season.season_number) FontWeight.Bold else FontWeight.Normal,
                                                                    modifier = Modifier.fillMaxWidth(),
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
                            }

                            // Episode Picker
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "EPISODE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1A2232))
                                        .border(1.dp, Color(0xFF2B3648), RoundedCornerShape(12.dp))
                                        .clickable { expandedEpisode = true }
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                        .tvFocusBorder(RoundedCornerShape(12.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Episode ${localEpisode}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TealAccent)
                                    }
                                    if (expandedEpisode) {
                                        Dialog(onDismissRequest = { expandedEpisode = false }) {
                                            Card(
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151A25)),
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                                    Text(
                                                        text = "Select Episode", 
                                                        color = Color.White, 
                                                        fontSize = 18.sp, 
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                                        items((1..selectedSeasonObj.episode_count).toList()) { ep ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        localEpisode = ep
                                                                        expandedEpisode = false
                                                                    }
                                                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(if (localEpisode == ep) TealAccent.copy(alpha = 0.1f) else Color.Transparent)
                                                                    .tvFocusBorder()
                                                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Episode $ep",
                                                                    color = if (localEpisode == ep) TealAccent else Color.White,
                                                                    fontSize = 16.sp,
                                                                    fontWeight = if (localEpisode == ep) FontWeight.Bold else FontWeight.Normal,
                                                                    modifier = Modifier.fillMaxWidth(),
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
                            }
                        }
                    }
                }
            }
        }

        // ── Overview Section ──
        if (!m.overview.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-20).dp)
            ) {
                Text(
                    text = "Overview",
                    color = WhiteText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = m.overview.orEmpty(),
                    color = Color(0xFFAAB0BC),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
