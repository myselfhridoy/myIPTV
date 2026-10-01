@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.streambd.iptv.ui.screens.live

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.streambd.iptv.data.models.LiveCategory
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.SurfaceDark
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun LiveTvScreen(
    viewModel: MainViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    val categories by viewModel.liveCategories.collectAsState()
    val isLoading by viewModel.isLoadingLive.collectAsState()
    val allChannelsMap by viewModel.categoryChannels.collectAsState()
    val loadProgress by viewModel.searchLoadProgress.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val isTV = remember { com.streambd.iptv.utils.DeviceType.isTv(context) }

    val columns = when {
        isTV -> 5
        isLandscape -> 4
        else -> 2
    }

    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    // ── NEW: Focus on first card when the screen opens (TV) ──
    val firstCardFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Debounce only the filter input; blank clears immediately.
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            debouncedQuery = ""
        } else {
            delay(250)
            debouncedQuery = searchQuery
        }
    }

    val displayedCategories = remember(debouncedQuery, categories) {
        if (debouncedQuery.isBlank()) categories
        else categories.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
    }

    val matchedChannels = remember(debouncedQuery, allChannelsMap) {
        if (debouncedQuery.isBlank()) emptyList()
        else allChannelsMap.values.asSequence()
            .flatten()
            .filter { it.name.contains(debouncedQuery, ignoreCase = true) }
            .distinctBy { it.name + it.url }
            .take(100)
            .toList()
    }

    // ── NEW: Focus first card on entry (TV). Re-runs when loading finishes. ──
    LaunchedEffect(isLoading, displayedCategories.isNotEmpty()) {
        if (!isLoading && displayedCategories.isNotEmpty()) {
            delay(180)
            try { firstCardFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
            viewModel.loadAllCategoriesForSearch()
        }
    }

    BackHandler(enabled = isSearchActive) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            searchQuery = ""
            isSearchActive = false
        }
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
            if (!isSearchActive) {
                // ── FIX: hide hamburger on TV (Back opens drawer instead) ──
                if (onMenuClick != null && !isTV) {
                    TvIconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = WhiteText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = "Live TV",
                    color = WhiteText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!isTV) {
                    TvIconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = WhiteText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search channels & categories…",
                            color = GrayText,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = BioAccent
                        )
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .tvTouchClick {
                                    searchQuery = ""
                                    isSearchActive = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close search",
                                tint = GrayText
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BioAccent,
                        unfocusedBorderColor = Color(0xFF2B3648),
                        focusedTextColor = WhiteText,
                        unfocusedTextColor = WhiteText,
                        cursorColor = BioAccent,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .onFocusChanged { isSearchFocused = it.hasFocus }
                )
            }
        }

        // ── Search hints + progress ──
        AnimatedVisibility(visible = isSearchActive, enter = fadeIn(), exit = fadeOut()) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                if (debouncedQuery.isNotBlank()) {
                    Text(
                        text = "${displayedCategories.size} categories, " +
                            "${matchedChannels.size} channels for \"$debouncedQuery\"",
                        color = GrayText,
                        fontSize = 12.sp
                    )
                }
                loadProgress?.let { (done, total) ->
                    Text(
                        text = "Loading channels… $done/$total",
                        color = BioAccent,
                        fontSize = 12.sp
                    )
                }
            }
        }

        when {
            isLoading -> LoadingState()
            displayedCategories.isEmpty() &&
                matchedChannels.isEmpty() &&
                debouncedQuery.isNotBlank() -> NoResultsState(debouncedQuery)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // CATEGORIES section header (only while searching)
                if (debouncedQuery.isNotBlank() && displayedCategories.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_categories") {
                        SectionHeader("CATEGORIES")
                    }
                }

                // ── FIX: first item gets the initial FocusRequester ──
                itemsIndexed(
                    items = displayedCategories,
                    key = { index, category -> "cat_${index}_${category.name}" }
                ) { index, category ->
                    CategoryCard(
                        category = category,
                        modifier = if (index == 0 && debouncedQuery.isBlank())
                            Modifier.focusRequester(firstCardFocusRequester)
                        else Modifier
                    ) {
                        viewModel.selectCategory(category)
                        navController.navigate(Routes.LIVE_CATEGORY_CHANNELS) {
                            launchSingleTop = true
                        }
                    }
                }

                // CHANNELS section (only while searching)
                if (debouncedQuery.isNotBlank() && matchedChannels.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_channels") {
                        SectionHeader("CHANNELS")
                    }
                    itemsIndexed(
                        items = matchedChannels,
                        key = { index, channel -> "ch_${index}_${channel.name}|${channel.url}" }
                    ) { _, channel ->
                        ChannelCard(channel = channel) {
                            viewModel.setCurrentPlaylist(matchedChannels)
                            viewModel.selectChannel(channel)
                            navController.navigate(Routes.LIVE_PLAYER) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = GrayText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
}

// ─────────────────────────────────────────────────────────────
// Loading / No results
// ─────────────────────────────────────────────────────────────
@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = BioAccent)
    }
}

@Composable
private fun NoResultsState(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = GrayText.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "No results found for \"$query\"",
                color = GrayText,
                fontSize = 14.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Category card — modifier parameter added so callers can attach a FocusRequester
// ─────────────────────────────────────────────────────────────
@Composable
fun CategoryCard(
    category: LiveCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(180),
        label = "catScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) BioAccent else Color(0xFF2B3648),
        animationSpec = tween(180),
        label = "catBorder"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C2230), RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .tvTouchClick(onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF242C3D))
                .border(1.dp, Color.DarkGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (category.logo.isNotEmpty()) {
                AsyncImage(
                    model = category.logo,
                    contentDescription = category.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = BioAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = category.name,
            color = WhiteText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 30.dp
                )
        )
    }
}
