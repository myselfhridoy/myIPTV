package com.streambd.iptv.ui.screens.vod

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────
// Local palette
// ─────────────────────────────────────────────────────────────
private val SurfaceChip = Color(0xFF1E2633)
private val BioAccentDim = Color(0xFF008D94)
private val StarGold = Color(0xFFFFC43D)

@OptIn(ExperimentalLayoutApi::class)
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
    val isTv = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isCompact = configuration.screenWidthDp < 600 && !isTv

    // ── Focus targets ──
    val backButtonFocusRequester = remember { FocusRequester() }
    val watchButtonFocusRequester = remember { FocusRequester() }

    // ── Hoisted selection state, keyed by media id ──
    val mediaId = media?.id
    var localSeason by remember(mediaId) { mutableStateOf<Int?>(null) }
    var localEpisode by remember(mediaId) { mutableStateOf<Int?>(null) }
    var expandedSeason by remember(mediaId) { mutableStateOf(false) }
    var expandedEpisode by remember(mediaId) { mutableStateOf(false) }

    // ── Dialog list states ──
    val seasonListState = rememberLazyListState()
    val seasonItemFocusRequester = remember { FocusRequester() }
    val episodeListState = rememberLazyListState()
    val episodeItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(media) {
        if (media != null) {
            try { watchButtonFocusRequester.requestFocus() } catch (_: Exception) {}
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

    // ── Derived selection ──
    val validSeasons = remember(tvDetails, m.isMovie) {
        if (!m.isMovie && tvDetails != null) {
            tvDetails!!.seasons.filter { it.season_number > 0 }
        } else emptyList()
    }
    val effectiveSeason: Int? = localSeason ?: validSeasons.firstOrNull()?.season_number
    val selectedSeasonObj = validSeasons.find { it.season_number == effectiveSeason }
        ?: validSeasons.firstOrNull()
    val effectiveEpisode: Int = localEpisode
        ?.takeIf { it in 1..(selectedSeasonObj?.episode_count ?: 0) }
        ?: 1

    // Focus + scroll to selected item when the season dialog opens.
    LaunchedEffect(expandedSeason) {
        if (expandedSeason && validSeasons.isNotEmpty()) {
            val idx = validSeasons
                .indexOfFirst { it.season_number == effectiveSeason }
                .coerceAtLeast(0)
            try { seasonListState.scrollToItem(idx) } catch (_: Exception) {}
            delay(80)
            try { seasonItemFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Focus + scroll to selected item when the episode dialog opens.
    LaunchedEffect(expandedEpisode, selectedSeasonObj?.season_number) {
        if (expandedEpisode && selectedSeasonObj != null) {
            val idx = (effectiveEpisode - 1).coerceAtLeast(0)
            try { episodeListState.scrollToItem(idx) } catch (_: Exception) {}
            delay(80)
            try { episodeItemFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        // ── Full-screen backdrop ──
        AsyncImage(
            model = m.backdropUrl.ifEmpty { m.posterUrl },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .align(Alignment.TopCenter)
        )

        // Vertical gradient into the background.
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

        // Left gradient for text readability in landscape.
        if (!isPortrait) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                DarkNavyBackground.copy(alpha = 0.9f),
                                DarkNavyBackground.copy(alpha = 0.4f),
                                Color.Transparent
                            ),
                            endX = 1000f
                        )
                    )
            )
        }

        // ── Scrollable content ──
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
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp)
                        )
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
                    // ── Title ──
                    Text(
                        text = m.displayTitle,
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

                    // ── Tagline ──
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

                    // ── Metadata row ──
                    FlowRow(
                        horizontalArrangement = if (isPortrait)
                            Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                        else Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (m.vote_average != null && m.vote_average > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = StarGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", m.vote_average),
                                    color = StarGold,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (m.displayDate.isNotEmpty()) {
                            Text(
                                text = m.displayDate.take(4),
                                color = WhiteText.copy(alpha = 0.75f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (m.isMovie && runtime != null && runtime > 0) {
                            Text(
                                text = "${runtime / 60}h ${runtime % 60}m",
                                color = WhiteText.copy(alpha = 0.75f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (!m.isMovie && totalSeasons != null && totalSeasons > 0) {
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
                                imageVector = if (m.isMovie) Icons.Default.Movie else Icons.Default.Tv,
                                contentDescription = null,
                                tint = WhiteText.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (m.isMovie) "Movie" else "TV Show",
                                color = WhiteText.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Genres ──
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
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(50.dp)
                                        )
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

                    // ── Action row ──
                    FlowRow(
                        horizontalArrangement = if (isPortrait)
                            Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                        else Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // ── Play button (own component → works on TV + mobile) ──
                        PlayButton(
                            isCompact = isCompact,
                            onClick = {
                                mainViewModel.setSelectedSeason(effectiveSeason)
                                mainViewModel.setSelectedEpisode(effectiveEpisode)
                                navController.navigate(Routes.SOURCE_SELECTION)
                            },
                            focusRequester = watchButtonFocusRequester,
                            modifier = Modifier.focusProperties {
                                up = backButtonFocusRequester
                            }
                        )

                        // ── Season + Episode pills (TV shows only) ──
                        if (!m.isMovie && validSeasons.isNotEmpty()) {
                            Pill(
                                label = "Season ${effectiveSeason ?: 1}",
                                isCompact = isCompact,
                                onClick = { expandedSeason = true }
                            )
                            Pill(
                                label = "Ep $effectiveEpisode",
                                isCompact = isCompact,
                                onClick = { expandedEpisode = true }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // ── Overview ──
                    if (!m.overview.isNullOrEmpty()) {
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
                            text = m.overview.orEmpty(),
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

            // ── Layout by orientation ──
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

        // ── Back button (top-left) ──
        TvIconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .padding(if (isPortrait) 16.dp else 28.dp)
                .size(if (isPortrait) 44.dp else 48.dp)
                .align(Alignment.TopStart)
                .background(
                    Color.Black.copy(alpha = 0.35f),
                    androidx.compose.foundation.shape.CircleShape
                )
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

    // ── Season selection dialog ──
    if (expandedSeason && validSeasons.isNotEmpty()) {
        PremiumDialogScaffold(
            title = "Select Season",
            subtitle = "${validSeasons.size} season${if (validSeasons.size > 1) "s" else ""}",
            onDismiss = { expandedSeason = false },
            scrollContent = false
        ) {
            LazyColumn(
                state = seasonListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = validSeasons,
                    key = { it.season_number }
                ) { season ->
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
                            expandedSeason = false
                        }
                    )
                }
            }
        }
    }

    // ── Episode selection dialog ──
    if (expandedEpisode && selectedSeasonObj != null) {
        PremiumDialogScaffold(
            title = "Select Episode",
            subtitle = "Season ${selectedSeasonObj.season_number}",
            onDismiss = { expandedEpisode = false },
            scrollContent = false
        ) {
            LazyColumn(
                state = episodeListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = (1..selectedSeasonObj.episode_count).toList(),
                    key = { it }
                ) { ep ->
                    val isSelected = effectiveEpisode == ep
                    PremiumOptionRow(
                        title = "Episode $ep",
                        selected = isSelected,
                        focusRequester = if (isSelected) episodeItemFocusRequester else null,
                        onClick = {
                            localEpisode = ep
                            expandedEpisode = false
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Play button — own component so TV + mobile behave identically
// ─────────────────────────────────────────────────────────────
@Composable
private fun PlayButton(
    isCompact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        targetValue = if (isFocused) BioAccentDim else BioAccent,
        animationSpec = tween(180),
        label = "playBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(180),
        label = "playScale"
    )

    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .tvTouchClick(onClick)
            .padding(
                horizontal = if (isCompact) 22.dp else 28.dp,
                vertical = if (isCompact) 12.dp else 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Play",
            color = Color.Black,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Season / Episode pill
// ─────────────────────────────────────────────────────────────
@Composable
private fun Pill(
    label: String,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    TvFocusableItem(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        restBackground = Color.White.copy(alpha = 0.08f),
        contentPadding = PaddingValues(
            horizontal = if (isCompact) 16.dp else 20.dp,
            vertical = if (isCompact) 10.dp else 14.dp
        )
    ) {
        Text(
            text = label,
            color = WhiteText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = WhiteText.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
    }
}
