package com.streambd.iptv.ui.screens.vod

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.PremiumOptionRow
import com.streambd.iptv.ui.components.TvFocusableItem
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private val SurfaceChip = Color(0xFF1E2633)
private val BioAccentDim = Color(0xFF008D94)
private val StarGold = Color(0xFFFFC43D)

/**
 * TmdbDetailsScreen
 *
 * NOTE: This screen uses no Lazy containers.
 *
 * Compose 1.6.x has a bug in LazyLayoutBeyondBoundsModifierLocal that
 * crashes the focus system on Android TV when D-pad navigation triggers
 * a beyond-bounds focus search:
 *   IllegalStateException: LayoutCoordinate operations are only valid
 *   when isAttached is true
 *
 * The fix requires Compose Foundation 1.7.0+, which needs Kotlin 1.9.23+
 * and Compose Compiler 1.5.11+. To keep the current toolchain we replaced
 * every LazyColumn in this screen with a plain Column + verticalScroll.
 * Season/episode lists are small enough that eager composition is cheap.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TmdbDetailsScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController
) {
    val media by tmdbViewModel.selectedMedia.collectAsState()
    val tvDetails by tmdbViewModel.selectedTvDetails.collectAsState()
    val movieDetails by tmdbViewModel.selectedMovieDetails.collectAsState()

    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = remember(context) { com.streambd.iptv.utils.DeviceType.isTv(context) }
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isCompact = configuration.screenWidthDp < 600 && !isTv

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

    // ── Focus Play button (retry because it may not be attached on first frame) ──
    LaunchedEffect(mediaId, m) {
        repeat(6) { attempt ->
            delay(120L + attempt * 80L)
            val ok = runCatching { watchButtonFocusRequester.requestFocus() }.isSuccess
            if (ok) return@LaunchedEffect
        }
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        AsyncImage(
            model = m.backdropUrl.ifEmpty { m.posterUrl },
            contentDescription = null,
            contentScale = ContentScale.Crop,
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
                            DarkNavyBackground.copy(alpha = 0.15f),
                            DarkNavyBackground.copy(alpha = 0.6f),
                            DarkNavyBackground.copy(alpha = 0.95f),
                            DarkNavyBackground
                        ),
                        startY = 0f
                    )
                )
        )

        if (!isPortrait) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to DarkNavyBackground.copy(alpha = 0.9f),
                                0.5f to DarkNavyBackground.copy(alpha = 0.4f),
                                1f to Color.Transparent
                            )
                        )
                    )
            )
        }

        // ── Main content (Column + verticalScroll, no Lazy) ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(
                modifier = Modifier.height(
                    when {
                        isCompact -> 60.dp
                        isPortrait -> 100.dp
                        else -> 180.dp
                    }
                )
            )

            val posterContent: @Composable () -> Unit = {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
                    modifier = Modifier
                        .width(if (isPortrait || isCompact) 160.dp else 180.dp)
                        .height(if (isPortrait || isCompact) 240.dp else 270.dp)
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
                DetailsInfoContent(
                    modifier = modifier,
                    media = m,
                    genres = genres,
                    tagline = tagline,
                    runtime = runtime,
                    totalSeasons = totalSeasons,
                    isPortrait = isPortrait,
                    isCompact = isCompact,
                    validSeasons = validSeasons,
                    effectiveSeason = effectiveSeason,
                    effectiveEpisode = effectiveEpisode,
                    watchButtonFocusRequester = watchButtonFocusRequester,
                    backButtonFocusRequester = backButtonFocusRequester,
                    onPlay = {
                        if (!m.isMovie && effectiveSeason != null) {
                            mainViewModel.setVodSelection(m.id, effectiveSeason, effectiveEpisode)
                        }
                        navController.navigate(Routes.SOURCE_SELECTION)
                    },
                    onOpenSeason = { expandedSeason = true },
                    onOpenEpisode = { expandedEpisode = true }
                )
            }

            if (isPortrait) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    posterContent()
                    Spacer(modifier = Modifier.height(20.dp))
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

        TvIconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .padding(if (isPortrait) 16.dp else 28.dp)
                .size(if (isPortrait) 44.dp else 48.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                .focusRequester(backButtonFocusRequester)
                .focusProperties {
                    down = watchButtonFocusRequester
                    right = watchButtonFocusRequester
                }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = WhiteText,
                modifier = Modifier.size(22.dp)
            )
        }
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

    // ── Episode dialog (non-lazy) ──
    if (expandedEpisode && selectedSeasonObj != null) {
        PremiumDialogScaffold(
            title = "Select Episode",
            subtitle = "Season ${selectedSeasonObj.season_number}",
            onDismiss = { expandedEpisode = false },
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
                repeat(selectedSeasonObj.episode_count) { index ->
                    val ep = index + 1
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
// Info body
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DetailsInfoContent(
    modifier: Modifier,
    media: com.streambd.iptv.data.models.TmdbMedia,
    genres: List<com.streambd.iptv.data.models.TmdbGenre>?,
    tagline: String?,
    runtime: Int?,
    totalSeasons: Int?,
    isPortrait: Boolean,
    isCompact: Boolean,
    validSeasons: List<com.streambd.iptv.data.models.TmdbSeason>,
    effectiveSeason: Int?,
    effectiveEpisode: Int,
    watchButtonFocusRequester: FocusRequester,
    backButtonFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onOpenSeason: () -> Unit,
    onOpenEpisode: () -> Unit
) {
    Column(
        modifier = modifier.padding(top = if (isPortrait) 0.dp else 16.dp),
        horizontalAlignment = if (isPortrait) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            text = media.displayTitle,
            color = WhiteText,
            fontSize = when {
                isCompact -> 24.sp
                isPortrait -> 30.sp
                else -> 36.sp
            },
            fontWeight = FontWeight.ExtraBold,
            textAlign = if (isPortrait) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (!tagline.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\"$tagline\"",
                color = BioAccent.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                textAlign = if (isPortrait) TextAlign.Center else TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            horizontalArrangement = if (isPortrait)
                Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
            else Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (media.vote_average != null && media.vote_average > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = StarGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f", media.vote_average),
                        color = StarGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (media.displayDate.isNotEmpty()) {
                Text(
                    text = media.displayDate.take(4),
                    color = WhiteText.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (media.isMovie && runtime != null && runtime > 0) {
                Text(
                    text = "${runtime / 60}h ${runtime % 60}m",
                    color = WhiteText.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (!media.isMovie && totalSeasons != null && totalSeasons > 0) {
                Text(
                    text = "$totalSeasons Seasons",
                    color = WhiteText.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (media.isMovie) Icons.Default.Movie else Icons.Default.Tv,
                    contentDescription = null,
                    tint = WhiteText.copy(alpha = 0.9f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (media.isMovie) "Movie" else "TV Show",
                    color = WhiteText.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!genres.isNullOrEmpty()) {
            FlowRow(
                horizontalArrangement = if (isPortrait)
                    Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                else Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { genre ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(SurfaceChip.copy(alpha = 0.85f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = genre.name,
                            color = WhiteText.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        FlowRow(
            horizontalArrangement = if (isPortrait)
                Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
            else Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PlayButton(
                isCompact = isCompact,
                onClick = onPlay,
                focusRequester = watchButtonFocusRequester,
                modifier = Modifier.focusProperties {
                    up = backButtonFocusRequester
                }
            )

            if (!media.isMovie && validSeasons.isNotEmpty()) {
                Pill(label = "Season ${effectiveSeason ?: 1}", isCompact = isCompact, onClick = onOpenSeason)
                Pill(label = "Ep $effectiveEpisode", isCompact = isCompact, onClick = onOpenEpisode)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (!media.overview.isNullOrEmpty()) {
            Text(
                text = "Overview",
                color = WhiteText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = if (isPortrait) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = media.overview.orEmpty(),
                color = WhiteText.copy(alpha = 0.7f),
                fontSize = 15.sp,
                lineHeight = 23.sp,
                textAlign = if (isPortrait) TextAlign.Center else TextAlign.Start,
                modifier = if (isPortrait) Modifier.fillMaxWidth()
                else Modifier.padding(end = 32.dp)
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// REDESIGNED PLAY BUTTON
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PlayButton(
    isCompact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isFocused) BioAccentDim else BioAccent,
        animationSpec = tween(180),
        label = "playBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(180),
        label = "playScale"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(180),
        label = "playBorder"
    )

    val buttonHeight = if (isCompact) 46.dp else 52.dp
    val iconCircleSize = if (isCompact) 30.dp else 34.dp
    val iconSize = if (isCompact) 18.dp else 20.dp

    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(buttonHeight)
            .onFocusChanged { isFocused = it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = borderAlpha * 0.85f),
                shape = RoundedCornerShape(50)
            )
            .tvTouchClick(onClick)
            .padding(
                start = if (isCompact) 8.dp else 10.dp,
                end = if (isCompact) 22.dp else 26.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(iconCircleSize)
                .background(Color.Black.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(Modifier.width(if (isCompact) 8.dp else 10.dp))
        Text(
            text = "Play",
            color = Color.Black,
            fontSize = if (isCompact) 15.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun Pill(label: String, isCompact: Boolean, onClick: () -> Unit) {
    TvFocusableItem(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        restBackground = Color.White.copy(alpha = 0.08f),
        contentPadding = PaddingValues(
            horizontal = if (isCompact) 16.dp else 20.dp,
            vertical = if (isCompact) 10.dp else 14.dp
        )
    ) {
        Text(label, color = WhiteText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = WhiteText.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
    }
}
