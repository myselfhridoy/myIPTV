package com.streambd.iptv.ui.screens.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.TmdbGenre
import com.streambd.iptv.data.models.TmdbMedia
import com.streambd.iptv.data.models.TmdbSeason
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.components.AppButton
import com.streambd.iptv.ui.components.AppIconButton
import com.streambd.iptv.ui.components.CategoryChip
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.PremiumOptionRow
import com.streambd.iptv.ui.components.RemoteImage
import com.streambd.iptv.ui.components.SelectField
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.theme.PrimeGold
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Movie / series details.
 *
 * NOTE: this screen uses no Lazy containers (same Compose 1.6.x TV focus
 * crash as VodScreen — see the explanation there). Season / episode lists
 * are small, so plain Column + verticalScroll is fine.
 *
 * ALL logic is unchanged: details fetching, restoring & saving the last
 * season/episode per title, Play-button focus retries, season / episode
 * dialogs. Only the layout is new:
 *   • phone / portrait: wide backdrop on top, left-aligned title, genres,
 *     full-width Play button, Season / Episode selectors, overview
 *   • landscape / TV / tablet: poster + info side by side over the backdrop
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TmdbDetailsScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController
) {
    val media by tmdbViewModel.selectedMedia.collectAsState()
    val tvDetails by tmdbViewModel.selectedTvDetails.collectAsState()
    val movieDetails by tmdbViewModel.selectedMovieDetails.collectAsState()

    val device = LocalDeviceProfile.current
    val isTv = device.isTv
    val isPortrait = !device.isLandscape
    val isCompact = device.screenWidthDp < 600 && !isTv

    val backButtonFocusRequester = remember { FocusRequester() }
    val watchButtonFocusRequester = remember { FocusRequester() }

    val mediaId = media?.id

    // ── Persistence: restore last S/E for THIS media id ──
    val savedSelection = remember(mediaId) {
        mediaId?.let { mainViewModel.getVodSelectionFor(it) }
    }
    var localSeason by remember(mediaId) { mutableStateOf<Int?>(savedSelection?.first) }
    var localEpisode by remember(mediaId) { mutableStateOf<Int?>(savedSelection?.second) }
    var expandedSeason by remember(mediaId) { mutableStateOf(false) }
    var expandedEpisode by remember(mediaId) { mutableStateOf(false) }

    // ── Fetch details for the current title ──
    LaunchedEffect(mediaId) {
        val current = media ?: return@LaunchedEffect
        if (current.isMovie) {
            tmdbViewModel.fetchMovieDetails(current.id)
        } else {
            tmdbViewModel.fetchTvDetails(current.id)
        }
    }

    val m = media ?: return

    // ── Focus the Play button, and keep trying until it REALLY has focus ──
    // (requestFocus() can succeed on a node that is attached but not yet
    // laid out, or be overridden while the screen transition is still running;
    // so we watch the button's own focus state instead of trusting the call.)
    var playHasFocus by remember(mediaId) { mutableStateOf(false) }
    LaunchedEffect(mediaId, m) {
        repeat(10) { attempt ->
            delay(150L + attempt * 100L)
            if (playHasFocus) return@LaunchedEffect
            runCatching { watchButtonFocusRequester.requestFocus() }
        }
    }
    val playButtonModifier = Modifier.onFocusChanged { if (it.isFocused) playHasFocus = true }

    // ── Only trust details that match the current media ──
    val currentTvDetails = tvDetails?.takeIf { it.id == m.id && !m.isMovie }
    val currentMovieDetails = movieDetails?.takeIf { it.id == m.id && m.isMovie }

    val genres = if (m.isMovie) currentMovieDetails?.genres else currentTvDetails?.genres
    val tagline = if (m.isMovie) currentMovieDetails?.tagline else currentTvDetails?.tagline
    val runtime = currentMovieDetails?.runtime
    val totalSeasons = currentTvDetails?.number_of_seasons

    val validSeasons = remember(currentTvDetails, m.isMovie) {
        if (!m.isMovie && currentTvDetails != null) {
            currentTvDetails.seasons.filter { it.season_number > 0 }
        } else emptyList()
    }
    val effectiveSeason: Int? = localSeason ?: validSeasons.firstOrNull()?.season_number
    val selectedSeasonObj = validSeasons.find { it.season_number == effectiveSeason }
        ?: validSeasons.firstOrNull()
    val effectiveEpisode: Int = localEpisode
        ?.takeIf { it in 1..(selectedSeasonObj?.episode_count ?: 0) }
        ?: 1

    // ── Persist any user-visible change into the VM ──
    LaunchedEffect(m.id, effectiveSeason, effectiveEpisode, validSeasons.isNotEmpty()) {
        if (!m.isMovie && validSeasons.isNotEmpty() && effectiveSeason != null) {
            mainViewModel.setVodSelection(m.id, effectiveSeason, effectiveEpisode)
        }
    }

    // ── Season dialog focus ──
    val seasonItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(expandedSeason) {
        if (expandedSeason && validSeasons.isNotEmpty()) {
            delay(80)
            runCatching { seasonItemFocusRequester.requestFocus() }
        }
    }

    // ── Episode dialog focus ──
    val episodeItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(expandedEpisode, selectedSeasonObj?.season_number) {
        if (expandedEpisode && selectedSeasonObj != null) {
            delay(80)
            runCatching { episodeItemFocusRequester.requestFocus() }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val bg = scheme.background
    val backdropAspect = 16f / 10f

    val onPlay: () -> Unit = {
        if (!m.isMovie && effectiveSeason != null) {
            mainViewModel.setVodSelection(m.id, effectiveSeason, effectiveEpisode)
        }
        navController.navigate(Routes.SOURCE_SELECTION)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        val backdropModel = m.backdropUrl.ifEmpty { m.posterUrl }

        if (isPortrait) {
            // ── Phone / portrait: backdrop on top ──
            RemoteImage(
                url = backdropModel,
                label = m.displayTitle,
                contentScale = ContentScale.Crop,
                describe = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(backdropAspect)
                    .align(Alignment.TopCenter)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(backdropAspect)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                bg.copy(alpha = 0.55f),
                                bg
                            )
                        )
                    )
            )
        } else {
            // ── Landscape / TV: big backdrop with side fade ──
            RemoteImage(
                url = backdropModel,
                label = m.displayTitle,
                contentScale = ContentScale.Crop,
                describe = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .align(Alignment.TopCenter)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                bg.copy(alpha = 0.15f),
                                bg.copy(alpha = 0.6f),
                                bg.copy(alpha = 0.95f),
                                bg
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to bg.copy(alpha = 0.9f),
                                0.5f to bg.copy(alpha = 0.4f),
                                1f to Color.Transparent
                            )
                        )
                    )
            )
        }

        // ── Scrolling content (Column + verticalScroll, no Lazy) ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            if (isPortrait) {
                // Leaves ~45dp of the backdrop's fade behind the title.
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(backdropAspect * 1.25f)
                )
                DetailsInfo(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    media = m,
                    genres = genres,
                    tagline = tagline,
                    runtime = runtime,
                    totalSeasons = totalSeasons,
                    wide = false,
                    isCompact = isCompact,
                    validSeasons = validSeasons,
                    effectiveSeason = effectiveSeason,
                    effectiveEpisode = effectiveEpisode,
                    watchButtonFocusRequester = watchButtonFocusRequester,
                    playButtonModifier = playButtonModifier,
                    backButtonFocusRequester = backButtonFocusRequester,
                    onPlay = onPlay,
                    onOpenSeason = { expandedSeason = true },
                    onOpenEpisode = { expandedEpisode = true }
                )
            } else {
                Spacer(modifier = Modifier.height(if (isTv) 96.dp else 84.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isTv) 48.dp else 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(270.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
                    ) {
                        RemoteImage(
                            url = m.posterUrl,
                            label = m.displayTitle,
                            contentScale = ContentScale.Crop,
                            describe = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(32.dp))
                    DetailsInfo(
                        modifier = Modifier.weight(1f),
                        media = m,
                        genres = genres,
                        tagline = tagline,
                        runtime = runtime,
                        totalSeasons = totalSeasons,
                        wide = true,
                        isCompact = false,
                        validSeasons = validSeasons,
                        effectiveSeason = effectiveSeason,
                        effectiveEpisode = effectiveEpisode,
                        watchButtonFocusRequester = watchButtonFocusRequester,
                        playButtonModifier = playButtonModifier,
                        backButtonFocusRequester = backButtonFocusRequester,
                        onPlay = onPlay,
                        onOpenSeason = { expandedSeason = true },
                        onOpenEpisode = { expandedEpisode = true }
                    )
                }
            }
        }

        // ── Back button (floats over the backdrop) ──
        AppIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = { navController.popBackStack() },
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(if (isPortrait) 12.dp else 24.dp)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .focusProperties {
                    down = watchButtonFocusRequester
                    right = watchButtonFocusRequester
                },
            focusRequester = backButtonFocusRequester
        )
    }

    // ── Season dialog (non-lazy) ──
    if (expandedSeason && validSeasons.isNotEmpty()) {
        PremiumDialogScaffold(
            title = "Select Season",
            subtitle = "${validSeasons.size} season${if (validSeasons.size > 1) "s" else ""}",
            onDismiss = { expandedSeason = false },
            scrollContent = false
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                validSeasons.forEach { season ->
                    val isSelected = effectiveSeason == season.season_number
                    PremiumOptionRow(
                        title = "Season ${season.season_number}",
                        subtitle = if (season.episode_count > 0)
                            "${season.episode_count} episode${if (season.episode_count > 1) "s" else ""}"
                        else null,
                        selected = isSelected,
                        focusRequester = if (isSelected) seasonItemFocusRequester else null,
                        onClick = {
                            localSeason = season.season_number
                            localEpisode = 1
                            mainViewModel.setVodSelection(m.id, season.season_number, 1)
                            expandedSeason = false
                        }
                    )
                }
            }
        }
    }

    // ── Episode dialog (non-lazy, PAGED) ──
    // Long series have hundreds of episodes. Composing a row for each one
    // made the dialog very slow, and Lazy lists are off-limits on TV (see the
    // note at the top). So episodes are shown 50 at a time, with page chips
    // ("1–50", "51–100" …) above — at most 50 rows exist at once.
    if (expandedEpisode && selectedSeasonObj != null) {
        val totalEpisodes = selectedSeasonObj.episode_count
        val pageSize = 50
        val pageCount = (totalEpisodes + pageSize - 1) / pageSize
        // Open on the page that contains the current episode.
        var page by remember(selectedSeasonObj.season_number) {
            mutableStateOf(((effectiveEpisode - 1) / pageSize).coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
        }
        val firstEp = page * pageSize + 1
        val lastEp = minOf(totalEpisodes, firstEp + pageSize - 1)
        // Fresh scroll position whenever the page changes.
        val listScroll = remember(page) { ScrollState(0) }

        PremiumDialogScaffold(
            title = "Select Episode",
            subtitle = "Season ${selectedSeasonObj.season_number} • $totalEpisodes episodes",
            onDismiss = { expandedEpisode = false },
            scrollContent = false
        ) {
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (p in 0 until pageCount) {
                        val from = p * pageSize + 1
                        val to = minOf(totalEpisodes, from + pageSize - 1)
                        CategoryChip(
                            label = "$from–$to",
                            selected = p == page,
                            onClick = { page = p }
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(listScroll)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (ep in firstEp..lastEp) {
                    val isSelected = effectiveEpisode == ep
                    PremiumOptionRow(
                        title = "Episode $ep",
                        selected = isSelected,
                        focusRequester = if (isSelected) episodeItemFocusRequester else null,
                        onClick = {
                            localEpisode = ep
                            mainViewModel.setVodSelection(
                                m.id,
                                selectedSeasonObj.season_number,
                                ep
                            )
                            expandedEpisode = false
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Info block (title · meta · genres · actions · overview)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun DetailsInfo(
    modifier: Modifier,
    media: TmdbMedia,
    genres: List<TmdbGenre>?,
    tagline: String?,
    runtime: Int?,
    totalSeasons: Int?,
    wide: Boolean,
    isCompact: Boolean,
    validSeasons: List<TmdbSeason>,
    effectiveSeason: Int?,
    effectiveEpisode: Int,
    watchButtonFocusRequester: FocusRequester,
    playButtonModifier: Modifier,
    backButtonFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onOpenSeason: () -> Unit,
    onOpenEpisode: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val showSelectors = !media.isMovie && validSeasons.isNotEmpty()

    Column(modifier = modifier) {
        Text(
            text = media.displayTitle,
            color = scheme.onBackground,
            style = when {
                isCompact -> MaterialTheme.typography.headlineSmall
                wide -> MaterialTheme.typography.headlineLarge
                else -> MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )

        if (!tagline.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "“$tagline”",
                color = scheme.primary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic
            )
        }

        Spacer(Modifier.height(12.dp))

        MetaRow(media = media, runtime = runtime, totalSeasons = totalSeasons)

        if (!genres.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { genre ->
                    Text(
                        text = genre.name,
                        color = scheme.onSurface.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .border(1.dp, scheme.outline, MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ── Actions ──
        if (wide) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButton(
                    text = "Play",
                    onClick = onPlay,
                    icon = Icons.Default.PlayArrow,
                    focusRequester = watchButtonFocusRequester,
                    modifier = playButtonModifier.focusProperties { up = backButtonFocusRequester }
                )
                if (showSelectors) {
                    SelectField(
                        label = "Season",
                        value = "Season ${effectiveSeason ?: 1}",
                        onClick = onOpenSeason,
                        modifier = Modifier.width(170.dp)
                    )
                    SelectField(
                        label = "Episode",
                        value = "Episode $effectiveEpisode",
                        onClick = onOpenEpisode,
                        modifier = Modifier.width(170.dp)
                    )
                }
            }
        } else {
            AppButton(
                text = "Play",
                onClick = onPlay,
                icon = Icons.Default.PlayArrow,
                focusRequester = watchButtonFocusRequester,
                modifier = playButtonModifier
                    .fillMaxWidth()
                    .focusProperties { up = backButtonFocusRequester }
            )
            if (showSelectors) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelectField(
                        label = "Season",
                        value = "Season ${effectiveSeason ?: 1}",
                        onClick = onOpenSeason,
                        modifier = Modifier.weight(1f)
                    )
                    SelectField(
                        label = "Episode",
                        value = "Episode $effectiveEpisode",
                        onClick = onOpenEpisode,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── Overview ──
        if (!media.overview.isNullOrEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Overview",
                color = scheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = media.overview.orEmpty(),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = if (wide) Modifier.padding(end = 32.dp) else Modifier
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

/** ★ 7.8  ·  2024  ·  2h 5m / 3 Seasons  ·  [Movie] */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(media: TmdbMedia, runtime: Int?, totalSeasons: Int?) {
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val rating = media.vote_average?.takeIf { it > 0 }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (rating != null) {
            Text(
                text = "★ " + String.format(Locale.US, "%.1f", rating),
                color = PrimeGold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        if (media.displayDate.length >= 4) {
            Text(
                text = media.displayDate.take(4),
                color = muted,
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (media.isMovie && runtime != null && runtime > 0) {
            Text(
                text = "${runtime / 60}h ${runtime % 60}m",
                color = muted,
                style = MaterialTheme.typography.labelLarge
            )
        } else if (!media.isMovie && totalSeasons != null && totalSeasons > 0) {
            Text(
                text = "$totalSeasons Season${if (totalSeasons > 1) "s" else ""}",
                color = muted,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Text(
            text = if (media.isMovie) "Movie" else "TV Show",
            color = scheme.onSurface.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
