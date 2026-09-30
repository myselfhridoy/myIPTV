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

// ─────────────────────────────────────────────────────────────
// TODO: Move these to Theme.kt (consolidate hardcoded colors)
// ─────────────────────────────────────────────────────────────
private val BackgroundDeep = Color(0xFF0D1117)      // was Color(0xFF0D1117)
private val SurfaceElevated = Color(0xFF151A25)     // was Color(0xFF151A25)
private val SurfaceFocused = Color(0xFF1E2638)      // was Color(0xFF1E2638)
private val SurfaceBorder = Color(0xFF2B3648)       // was Color(0xFF2B3648)
private val IconMuted = Color(0xFF555555)           // was Color(0xFF555555)
private val TealAccentDim = Color(0xFF008D94)       // was Color(0xFF008D94)

// ─────────────────────────────────────────────────────────────
// Quality rank — used for sorting streams (higher = better)
// ─────────────────────────────────────────────────────────────
private fun qualityRank(q: String?): Int {
    val s = q?.lowercase() ?: return 0
    return when {
        s.contains("4k") || s.contains("2160") -> 100
        s.contains("1080") || s.contains("fhd") -> 80
        s.contains("720") || (s.contains("hd") && !s.contains("fhd")) -> 60
        s.contains("480") || s.contains("sd") -> 40
        s.contains("cam") -> 10
        else -> 5
    }
}

@Composable
fun SourceSelectionScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val media by tmdbViewModel.selectedMedia.collectAsState()
    val isResolving by mainViewModel.isResolvingStreams.collectAsState()
    val streamsRaw by mainViewModel.availableStreams.collectAsState()

    // Collect season/episode as state so LaunchedEffect key reacts to changes
    val selectedSeason by mainViewModel.selectedSeason.collectAsState()
    val selectedEpisode by mainViewModel.selectedEpisode.collectAsState()

    // ── Fix #2: pop back inside LaunchedEffect, not during composition ──
    LaunchedEffect(media) {
        if (media == null) {
            navController.popBackStack()
        }
    }

    if (media == null) return

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

    // ─────────────────────────────────────────────────────────────
    // FIX (duplicate key crash):
    //   1) distinctBy — same provider + quality + type + url ⇒ dedupe
    //   2) sortedWith — best quality first, tie-break by provider name
    // ─────────────────────────────────────────────────────────────
    val streams = remember(streamsRaw) {
        streamsRaw
            .distinctBy { "${it.provider}|${it.quality}|${it.type.orEmpty()}|${it.url}" }
            .sortedWith(
                compareByDescending<StreamSource> { qualityRank(it.quality) }
                    .thenBy { it.provider.lowercase() }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fix #5: 48dp touch target (was 36dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceFocused, CircleShape)
                    .tvFocusBorder(CircleShape)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

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
                    // Fix: Color.Gray → higher-contrast white alpha
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Fix #5: 48dp touch target (was 36dp). Manual Box instead of IconButton
            // so we keep the same visual pill + border styling.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceFocused, CircleShape)
                    .tvFocusBorder(CircleShape)
                    .clickable {
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
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Sources",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
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
                        colors = listOf(
                            Color.Transparent,
                            TealAccent.copy(alpha = 0.3f),
                            Color.Transparent
                        )
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
                        color = Color.White.copy(alpha = 0.6f),
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
                        tint = IconMuted,
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
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // Stream List
            val firstFocusRequester = remember { FocusRequester() }

            LaunchedEffect(streams) {
                try {
                    firstFocusRequester.requestFocus()
                } catch (_: Exception) {
                    // Ignore
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (isResolving)
                            "Found ${streams.size} source(s) so far... (Searching)"
                        else
                            "${streams.size} source${if (streams.size > 1) "s" else ""} available",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // ─────────────────────────────────────────────────────────────
                // FIX: was `items(key = provider|quality|type)` → crash when the
                // addon returned two streams with the same provider/quality/type.
                // Now `itemsIndexed` — index is guaranteed unique, so duplicate
                // keys can never happen even if upstream data repeats.
                // ─────────────────────────────────────────────────────────────
                itemsIndexed(
                    items = streams,
                    key = { index, stream ->
                        "$index|${stream.provider}|${stream.quality}|${stream.type.orEmpty()}"
                    }
                ) { index, stream ->
                    StreamSourceCard(
                        stream = stream,
                        modifier = if (index == 0)
                            Modifier.focusRequester(firstFocusRequester)
                        else Modifier,
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
        stream.quality.contains("4K", ignoreCase = true) ||
                stream.quality.contains("2160", ignoreCase = true) -> Color(0xFFFFD700)
        stream.quality.contains("1080", ignoreCase = true) ||
                stream.quality.contains("FHD", ignoreCase = true) -> Color(0xFF4CAF50)
        stream.quality.contains("720", ignoreCase = true) ||
                stream.quality.contains("HD", ignoreCase = true) -> TealAccent
        stream.quality.contains("CAM", ignoreCase = true) -> Color(0xFFFF5722)
        else -> TealAccent
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) TealAccent else SurfaceBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) SurfaceFocused else SurfaceElevated
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
                            colors = listOf(
                                TealAccent.copy(alpha = 0.2f),
                                TealAccentDim.copy(alpha = 0.1f)
                            )
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
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }

                    // ─────────────────────────────────────────────
                    // TODO: File-size / language badges
                    // Uncomment once StreamSource has these fields
                    // (e.g. `size: String?`, `languages: List<String>?`).
                    //
                    // stream.size?.takeIf { it.isNotBlank() }?.let { size ->
                    //     Text(
                    //         text = size,
                    //         color = Color.White.copy(alpha = 0.6f),
                    //         fontSize = 11.sp
                    //     )
                    // }
                    // stream.languages?.takeIf { it.isNotEmpty() }?.let { langs ->
                    //     Text(
                    //         text = langs.take(3).joinToString(", "),
                    //         color = Color.White.copy(alpha = 0.6f),
                    //         fontSize = 11.sp,
                    //         maxLines = 1,
                    //         overflow = TextOverflow.Ellipsis
                    //     )
                    // }
                    // ─────────────────────────────────────────────
                }
            }

            // Play Button
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Play",
                tint = if (isFocused) TealAccent else IconMuted,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
