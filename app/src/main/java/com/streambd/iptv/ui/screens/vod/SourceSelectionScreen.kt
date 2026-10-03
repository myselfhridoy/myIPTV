package com.streambd.iptv.ui.screens.vod

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.ui.components.TvFocusableItem
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────
// Local palette
// ─────────────────────────────────────────────────────────────
private val SurfaceElevated = Color(0xFF151A25)
private val SurfaceFocused = Color(0xFF1E2638)
private val SurfaceBorder = Color(0xFF2B3648)
private val IconMuted = Color(0xFF555555)
private val BioAccentDim = Color(0xFF008D94)
private val Quality4k = Color(0xFFFFC43D)
private val QualityFhd = Color(0xFF4CAF50)
private val QualityCam = Color(0xFFFF5722)

/**
 * Quality rank — used for sorting streams (higher = better).
 */
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

/**
 * SourceSelectionScreen
 *
 * NOTE: No Lazy containers — same reasoning as VodScreen / TmdbDetailsScreen.
 *
 * Compose 1.6.x has a bug in LazyLayoutBeyondBoundsModifierLocal that
 * crashes the focus system on Android TV when D-pad navigation triggers
 * a beyond-bounds focus search:
 *   IllegalStateException: LayoutCoordinate operations are only valid
 *   when isAttached is true
 *
 * The list here is typically short (2–10 items), so eager composition in
 * a Column + verticalScroll is both cheap and crash-free.
 */
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
    val selectedSeason by mainViewModel.selectedSeason.collectAsState()
    val selectedEpisode by mainViewModel.selectedEpisode.collectAsState()

    // ── Safe pop-back on null media ──
    // Only pop if media was EVER set and then became null, so an early
    // composition frame (before the VM has resolved the selected media)
    // does not accidentally pop this screen off the back stack.
    var everHadMedia by remember { mutableStateOf(false) }
    LaunchedEffect(media) {
        if (media != null) {
            everHadMedia = true
        } else if (everHadMedia) {
            navController.popBackStack()
        }
    }

    if (media == null) return

    val m = media!!

    // Fetch streams on entry, or when media/season/episode changes.
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

    // Dedupe + sort by quality.
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
            .background(DarkNavyBackground)
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .size(44.dp)
                    .background(SurfaceFocused, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = WhiteText,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Select Source",
                    color = WhiteText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = m.displayTitle,
                    color = WhiteText.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TvIconButton(
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
                    .size(44.dp)
                    .background(SurfaceFocused, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Sources",
                    tint = WhiteText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ── Divider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BioAccent.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ── Content ──
        when {
            isResolving && streams.isEmpty() -> LoadingState()
            streams.isEmpty() && !isResolving -> EmptyState()
            else -> StreamList(
                streams = streams,
                isResolving = isResolving,
                onClick = { stream ->
                    mainViewModel.selectStream(stream)
                    navController.navigate(Routes.VOD_PLAYER)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Loading / Empty
// ─────────────────────────────────────────────────────────────
@Composable
private fun LoadingState() {
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
                tint = BioAccent,
                modifier = Modifier
                    .size(56.dp)
                    .scale(pulseScale)
            )
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator(
                color = BioAccent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Searching addons for streams…",
                color = WhiteText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This may take a moment",
                color = WhiteText.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun EmptyState() {
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
                color = WhiteText.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Stream list — non-lazy
// ─────────────────────────────────────────────────────────────
@Composable
private fun StreamList(
    streams: List<StreamSource>,
    isResolving: Boolean,
    onClick: (StreamSource) -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }

    // ── Focus first card, with retry ──
    // Retry because the first item may not be attached on the first frame
    // (source list arrives progressively from the addons). Fires on the
    // empty→non-empty transition only, so subsequent sources arriving
    // do NOT steal focus back from the user.
    LaunchedEffect(streams.isNotEmpty()) {
        if (streams.isEmpty()) return@LaunchedEffect
        repeat(4) { attempt ->
            delay(120L + attempt * 80L)
            if (runCatching { firstFocusRequester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (isResolving)
                "Found ${streams.size} source(s) so far…"
            else
                "${streams.size} source${if (streams.size > 1) "s" else ""} available",
            color = WhiteText.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        streams.forEachIndexed { index, stream ->
            StreamSourceCard(
                stream = stream,
                modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester)
                else Modifier,
                onClick = { onClick(stream) }
            )
        }

        if (isResolving) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BioAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────
// Stream card — uses TvFocusableItem so focus + touch + D-pad work
// ─────────────────────────────────────────────────────────────
@Composable
private fun StreamSourceCard(
    stream: StreamSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val qualityColor = remember(stream.quality) {
        when {
            stream.quality.contains("4K", ignoreCase = true) ||
                stream.quality.contains("2160", ignoreCase = true) -> Quality4k
            stream.quality.contains("1080", ignoreCase = true) ||
                stream.quality.contains("FHD", ignoreCase = true) -> QualityFhd
            stream.quality.contains("720", ignoreCase = true) ||
                stream.quality.contains("HD", ignoreCase = true) -> BioAccent
            stream.quality.contains("CAM", ignoreCase = true) -> QualityCam
            else -> BioAccent
        }
    }

    TvFocusableItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        restBackground = SurfaceElevated,
        contentPadding = PaddingValues(16.dp)
    ) {
        // ── Provider icon ──
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            BioAccent.copy(alpha = 0.2f),
                            BioAccentDim.copy(alpha = 0.1f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Source,
                contentDescription = null,
                tint = BioAccent,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // ── Provider info ──
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stream.provider.ifBlank { "Unknown Provider" },
                color = WhiteText,
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
                // Quality badge
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

                // Type badge
                if (!stream.type.isNullOrEmpty()) {
                    Text(
                        text = stream.type.uppercase(),
                        color = WhiteText.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Play icon (visual cue) ──
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = BioAccent,
            modifier = Modifier.size(32.dp)
        )
    }
}
