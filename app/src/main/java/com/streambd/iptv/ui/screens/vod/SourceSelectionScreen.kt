package com.streambd.iptv.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.AppIconButton
import com.streambd.iptv.ui.components.AppTopBar
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.FocusSurface
import com.streambd.iptv.ui.components.ShimmerBox
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.theme.PrimeGold
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay

// Semantic quality colours (not theme colours on purpose).
private val Quality4k = PrimeGold
private val QualityFhd = Color(0xFF4CAF50)
private val QualityCam = Color(0xFFFF5722)

/** Quality rank — used for sorting streams (higher = better). */
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
 * Choose which addon source to play.
 *
 * NOTE: No Lazy containers — same Compose 1.6.x TV focus crash as VodScreen
 * and TmdbDetailsScreen (see the explanation in VodScreen). The list is
 * short (2–10 items), so a Column + verticalScroll is cheap and safe.
 *
 * ALL logic is unchanged: safe pop-back when media disappears, fetching
 * streams for the title / season / episode, de-duplicating and sorting by
 * quality, force refresh, focusing the first source once, hand-off to the
 * VOD player. Only the UI is new.
 */
@Composable
fun SourceSelectionScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
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

    val refresh: () -> Unit = {
        val type = if (m.isMovie) "movie" else "tv"
        mainViewModel.fetchVodStreams(
            context = context,
            type = type,
            tmdbId = m.id,
            season = selectedSeason,
            episode = selectedEpisode,
            forceRefresh = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        AppTopBar(
            title = "Select Source",
            subtitle = m.displayTitle,
            onBack = { navController.popBackStack() },
            actions = {
                AppIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refresh sources",
                    onClick = refresh
                )
            }
        )
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))

        when {
            isResolving && streams.isEmpty() -> SourcesSkeleton()

            streams.isEmpty() && !isResolving -> EmptyState(
                title = "No streams found",
                message = "Try a different title or check addons",
                icon = Icons.Default.Source,
                actionLabel = "Refresh",
                onAction = refresh
            )

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
// Loading — shimmer rows shaped like the final list
// ─────────────────────────────────────────────────────────────
@Composable
private fun SourcesSkeleton() {
    val d = AppTheme.dimens
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(horizontal = d.screenPaddingH + 4.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Searching addons for streams…",
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "This may take a moment",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(2.dp))
            repeat(5) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = MaterialTheme.shapes.medium
                )
            }
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
    val d = AppTheme.dimens
    val scheme = MaterialTheme.colorScheme
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.screenPaddingH + 4.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isResolving)
                    "Found ${streams.size} source(s) so far…"
                else
                    "${streams.size} source${if (streams.size > 1) "s" else ""} available",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            streams.forEachIndexed { index, stream ->
                StreamSourceCard(
                    stream = stream,
                    focusRequester = if (index == 0) firstFocusRequester else null,
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
                        color = scheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// One source row — touch, D-pad and TalkBack via FocusSurface
// ─────────────────────────────────────────────────────────────
@Composable
private fun StreamSourceCard(
    stream: StreamSource,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isTv = LocalDeviceProfile.current.isTv

    val qualityColor = remember(stream.quality, scheme.primary) {
        when {
            stream.quality.contains("4K", ignoreCase = true) ||
                stream.quality.contains("2160", ignoreCase = true) -> Quality4k
            stream.quality.contains("1080", ignoreCase = true) ||
                stream.quality.contains("FHD", ignoreCase = true) -> QualityFhd
            stream.quality.contains("720", ignoreCase = true) ||
                stream.quality.contains("HD", ignoreCase = true) -> scheme.primary
            stream.quality.contains("CAM", ignoreCase = true) -> QualityCam
            else -> scheme.primary
        }
    }

    FocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = scheme.surface.copy(alpha = 0.55f),
        focusedContainerColor = scheme.surfaceVariant,
        restingBorderColor = scheme.outline.copy(alpha = 0.55f),
        focusScale = if (isTv) 1.02f else 1f,
        focusRequester = focusRequester,
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true
    ) { _ ->
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Provider icon ──
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Provider info ──
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.provider.ifBlank { "Unknown Provider" },
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
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
                            .border(1.dp, qualityColor.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            tint = qualityColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stream.quality,
                            color = qualityColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Type badge
                    if (!stream.type.isNullOrEmpty()) {
                        Text(
                            text = stream.type.uppercase(),
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = scheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
