package com.streambd.iptv.ui.screens.vod

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.ui.components.tvFocusBorder
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.*
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel

@Composable
fun SourceSelectionScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val media by tmdbViewModel.selectedMedia.collectAsState()
    val isResolving by mainViewModel.isResolvingStreams.collectAsState()
    val streams by mainViewModel.availableStreams.collectAsState()

    // Collect season/episode as state so LaunchedEffect key reacts to changes
    val selectedSeason by mainViewModel.selectedSeason.collectAsState()
    val selectedEpisode by mainViewModel.selectedEpisode.collectAsState()

    if (media == null) {
        navController.popBackStack()
        return
    }

    val m = media!!

    // Fetch streams on entry or when media/season/episode changes
    LaunchedEffect(m.id, selectedSeason, selectedEpisode) {
        val type = if (m.isMovie) "movie" else "tv"
        mainViewModel.fetchVodStreams(
            context = context,
            type = type,
            tmdbId = m.id,
            season = selectedSeason,
            episode = selectedEpisode
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .tvFocusBorder(CircleShape)
                    .background(Color(0xFF1E2638), CircleShape)
                    .clickable { navController.popBackStack() }
                    .padding(6.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Select Source",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = m.displayTitle,
                    color = Color.Gray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    val type = if (m.isMovie) "movie" else "tv"
                    mainViewModel.fetchVodStreams(
                        context = context,
                        type = type,
                        tmdbId = m.id,
                        season = selectedSeason,
                        episode = selectedEpisode,
                        forceRefresh = true
                    )
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .tvFocusBorder(CircleShape)
                    .background(Color(0xFF1E2638), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Sources",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, TealAccent.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
        )

        // ── Content ──
        if (isResolving && streams.isEmpty()) {
            // Loading State (Full Screen)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val infiniteTransition = rememberInfiniteTransition(label = "searchAnim")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )

                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = TealAccent,
                        modifier = Modifier
                            .size(56.dp)
                            .scale(pulseScale)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    CircularProgressIndicator(
                        color = TealAccent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Searching addons for streams...",
                        color = WhiteText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This may take a moment",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (streams.isEmpty() && !isResolving) {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Source,
                        contentDescription = null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No streams found",
                        color = WhiteText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try a different title or check addons",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Stream List
            val firstFocusRequester = remember { FocusRequester() }

            LaunchedEffect(streams) {
                try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (isResolving) "Found ${streams.size} source(s) so far... (Searching)" else "${streams.size} source${if (streams.size > 1) "s" else ""} available",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                itemsIndexed(streams) { index, stream ->
                    StreamSourceCard(
                        stream = stream,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                        onClick = {
                            mainViewModel.selectStream(stream)
                            navController.navigate(Routes.VOD_PLAYER)
                        }
                    )
                }

                if (isResolving) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = TealAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamSourceCard(
    stream: StreamSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(150),
        label = "streamScale"
    )

    val qualityColor = when {
        stream.quality.contains("4K", ignoreCase = true) || stream.quality.contains("2160", ignoreCase = true) -> Color(0xFFFFD700)
        stream.quality.contains("1080", ignoreCase = true) || stream.quality.contains("FHD", ignoreCase = true) -> Color(0xFF4CAF50)
        stream.quality.contains("720", ignoreCase = true) || stream.quality.contains("HD", ignoreCase = true) -> TealAccent
        stream.quality.contains("CAM", ignoreCase = true) -> Color(0xFFFF5722)
        else -> TealAccent
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) TealAccent else Color(0xFF2B3648),
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF1E2638) else Color(0xFF151A25)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(TealAccent.copy(alpha = 0.2f), Color(0xFF008D94).copy(alpha = 0.1f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Source,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Provider Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.provider,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quality Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(qualityColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = qualityColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stream.quality,
                            color = qualityColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Type Badge
                    if (!stream.type.isNullOrEmpty()) {
                        Text(
                            text = stream.type.uppercase(),
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Play Button
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Play",
                tint = if (isFocused) TealAccent else Color(0xFF555555),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
