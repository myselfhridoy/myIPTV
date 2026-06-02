package com.myselfhridoy.streambdplayer.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

                // Watch Now Button
                var watchBtnFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .onFocusChanged { watchBtnFocused = it.isFocused }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            navController.navigate(Routes.SOURCE_SELECTION)
                        }
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(TealAccent, Color(0xFF008D94))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = if (watchBtnFocused) 3.dp else 0.dp,
                            color = if (watchBtnFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Watch Now", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
