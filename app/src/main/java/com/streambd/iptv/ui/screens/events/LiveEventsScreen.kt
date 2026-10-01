package com.streambd.iptv.ui.screens.events

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.DrmConfig
import com.streambd.iptv.data.models.SportzfyEvent
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.PremiumOptionRow
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// Palette (local, will be consolidated later)
// ─────────────────────────────────────────────────────────────
private val SportzfyCardBg = Color(0xFF161D2D).copy(alpha = 0.85f)

// ─────────────────────────────────────────────────────────────
// Date parsing helper
// ─────────────────────────────────────────────────────────────
private fun parseEventDate(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val cleanStr = dateStr.replace(Regex("([+-]\\d\\d):(\\d\\d)$"), "$1$2")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        sdf.parse(cleanStr)
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveEventsScreen(
    eventsViewModel: LiveEventsViewModel,
    mainViewModel: MainViewModel,
    navController: NavController,
    onMenuClick: (() -> Unit)? = null
) {
    val events by eventsViewModel.events.collectAsState()
    val isLoading by eventsViewModel.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf("Live") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    var showStreamSelectionFor by remember {
        mutableStateOf<List<com.streambd.iptv.data.models.SportzfyStream>?>(null)
    }
    var selectedEventForStream by remember { mutableStateOf<SportzfyEvent?>(null) }

    val sportsCount = remember(events) {
        val counts = mutableMapOf<String, Int>()
        counts["All"] = events.size
        events.forEach { e ->
            val s = e.sport ?: "Other"
            counts[s] = (counts[s] ?: 0) + 1
        }
        counts
    }

    val categories = remember(events) {
        listOf("All", "Cricket", "Football", "Motorsports", "Wrestling", "WWE")
            .filter { it == "All" || sportsCount.containsKey(it) }
    }

    val filteredEvents = remember(events, selectedCategory, selectedStatus, searchQuery) {
        var list = if (selectedCategory == "All") events
        else events.filter { it.sport == selectedCategory }
        list = when (selectedStatus) {
            "Live" -> list.filter { it.is_live == 1 }
            "Upcoming" -> list.filter {
                it.is_live == 0 && (parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE) >
                    System.currentTimeMillis()
            }.sortedBy { parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE }
            "Recent" -> list.filter {
                it.is_live == 0 && (parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE) <=
                    System.currentTimeMillis()
            }.sortedByDescending { parseEventDate(it.starts_at)?.time ?: 0L }
            else -> list
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                (it.team_a_name?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.team_b_name?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.league?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
        list
    }

    // Pull-to-refresh
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            eventsViewModel.fetchEvents()
        }
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) pullToRefreshState.endRefresh()
    }

    BackHandler(enabled = isSearchActive) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            searchQuery = ""
            isSearchActive = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F1423), Color(0xFF0A0D14))
                    )
                )
        ) {
            // ── Top bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onMenuClick != null && !isSearchActive) {
                    TvIconButton(onClick = onMenuClick, modifier = Modifier.size(44.dp)) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = WhiteText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .onFocusChanged { isSearchFocused = it.hasFocus },
                        placeholder = { Text("Search matches…", color = GrayText) },
                        colors = TextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.1f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
    focusedIndicatorColor = BioAccent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = WhiteText,
    unfocusedTextColor = WhiteText,
    cursorColor = BioAccent
),
                        shape = RoundedCornerShape(25.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TvIconButton(
                        onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close search",
                            tint = WhiteText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Text(
                        "Live Events",
                        color = BioAccent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    TvIconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = WhiteText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ── Category circles ──
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = categories, key = { it }) { cat ->
                    CategoryPill(
                        name = cat,
                        count = sportsCount[cat] ?: 0,
                        isSelected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            // ── Status chips ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val recentCount = events.count {
                    it.is_live == 0 && (parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE) <=
                        System.currentTimeMillis()
                }
                val liveCount = events.count { it.is_live == 1 }
                val upcomingCount = events.count {
                    it.is_live == 0 && (parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE) >
                        System.currentTimeMillis()
                }
                StatusChip("Recent", recentCount, selectedStatus == "Recent") {
                    selectedStatus = "Recent"
                }
                StatusChip("Live", liveCount, selectedStatus == "Live") {
                    selectedStatus = "Live"
                }
                StatusChip("Upcoming", upcomingCount, selectedStatus == "Upcoming") {
                    selectedStatus = "Upcoming"
                }
            }

            // ── Content ──
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BioAccent)
                    }
                }
                filteredEvents.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No $selectedStatus events for $selectedCategory",
                            color = GrayText,
                            fontSize = 16.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = filteredEvents, key = { it.id ?: it.hashCode() }) { event ->
                            SportzfyMatchCard(event) {
                                coroutineScope.launch {
                                    val streams = eventsViewModel.getStreamsForEvent(event.id ?: "")
                                    if (streams.isNotEmpty()) {
                                        val sourcesMap = mutableMapOf<String, String>()
                                        streams.forEachIndexed { index, s ->
                                            var name = s.label.takeIf { !it.isNullOrBlank() }
                                                ?: "Source ${index + 1}"
                                            if (sourcesMap.containsKey(name))
                                                name = "$name (${index + 1})"
                                            var srcUrl = s.stream_url ?: ""
                                            if (s.drm_key != null && s.drm_kid != null) {
                                                srcUrl += "|drm-type=clearkey|drm-key=" +
                                                    "${s.drm_kid}:${s.drm_key}"
                                            }
                                            sourcesMap[name] = srcUrl
                                        }

                                        if (streams.size == 1) {
                                            val stream = streams.first()
                                            var initialUrl = stream.stream_url ?: ""
                                            if (stream.drm_key != null && stream.drm_kid != null) {
                                                initialUrl += "|drm-type=clearkey|drm-key=" +
                                                    "${stream.drm_kid}:${stream.drm_key}"
                                            }
                                            val ch = Channel(
                                                name = event.league ?: "Live Event",
                                                url = initialUrl,
                                                group = event.sport ?: "Live",
                                                isLiveEvent = true,
                                                drm = if (stream.drm_key != null) DrmConfig(
                                                    type = "clearkey",
                                                    rawKeyPair = "${stream.drm_kid}:${stream.drm_key}"
                                                ) else null,
                                                sources = sourcesMap
                                            )
                                            mainViewModel.selectChannel(ch)
                                            navController.navigate(Routes.LIVE_PLAYER) {
                                                launchSingleTop = true
                                            }
                                        } else {
                                            selectedEventForStream = event
                                            showStreamSelectionFor = streams
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = SportzfyCardBg,
            contentColor = BioAccent
        )
    }

    // ── Multi-source picker (was ModalBottomSheet) ──
    if (showStreamSelectionFor != null && selectedEventForStream != null) {
        val streams = showStreamSelectionFor!!
        val event = selectedEventForStream!!
        PremiumDialogScaffold(
            title = "Select Stream",
            subtitle = "${streams.size} source${if (streams.size > 1) "s" else ""}",
            onDismiss = {
                showStreamSelectionFor = null
                selectedEventForStream = null
            },
            scrollContent = false
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = streams,
                    key = { stream ->
                        "${stream.label ?: ""}|${stream.stream_url ?: ""}"
                    }
                ) { stream ->
                    val streamName = stream.label?.takeIf { !it.isNullOrBlank() }
                        ?: "Source"
                    PremiumOptionRow(
                        title = streamName,
                        selected = false,
                        onClick = {
                            val sourcesMap = mutableMapOf<String, String>()
                            streams.forEachIndexed { idx, s ->
                                var sName = s.label.takeIf { !it.isNullOrBlank() }
                                    ?: "Source ${idx + 1}"
                                if (sourcesMap.containsKey(sName)) sName = "$sName (${idx + 1})"
                                var srcUrl = s.stream_url ?: ""
                                if (s.drm_key != null && s.drm_kid != null) {
                                    srcUrl += "|drm-type=clearkey|drm-key=" +
                                        "${s.drm_kid}:${s.drm_key}"
                                }
                                sourcesMap[sName] = srcUrl
                            }

                            showStreamSelectionFor = null

                            var initialUrl = stream.stream_url ?: ""
                            if (stream.drm_key != null && stream.drm_kid != null) {
                                initialUrl += "|drm-type=clearkey|drm-key=" +
                                    "${stream.drm_kid}:${stream.drm_key}"
                            }
                            val ch = Channel(
                                name = event.league ?: "Live Event",
                                url = initialUrl,
                                group = event.sport ?: "Live",
                                isLiveEvent = true,
                                drm = if (stream.drm_key != null) DrmConfig(
                                    type = "clearkey",
                                    rawKeyPair = "${stream.drm_kid}:${stream.drm_key}"
                                ) else null,
                                sources = sourcesMap
                            )
                            mainViewModel.selectChannel(ch)
                            selectedEventForStream = null
                            navController.navigate(Routes.LIVE_PLAYER) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Category pill — solid-fill when selected (Netflix/TiviMate style)
// ─────────────────────────────────────────────────────────────
@Composable
private fun CategoryPill(
    name: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent
            isFocused -> Color.White.copy(alpha = 0.08f)
            else -> Color(0xFF161D2D)
        },
        animationSpec = tween(200),
        label = "catPillBg"
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent
            isFocused -> BioAccent.copy(alpha = 0.7f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        animationSpec = tween(200),
        label = "catPillBorder"
    )
    val mainTextColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0A0E17) else WhiteText,
        animationSpec = tween(200),
        label = "catPillText"
    )
    val countTextColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0A0E17).copy(alpha = 0.55f)
        else WhiteText.copy(alpha = 0.45f),
        animationSpec = tween(200),
        label = "catPillCount"
    )

    Row(
        modifier = Modifier
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .tvTouchClick(onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = mainTextColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = count.toString(),
                color = countTextColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Status chip — subtle segmented-control look
// ─────────────────────────────────────────────────────────────
@Composable
private fun StatusChip(
    name: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent.copy(alpha = 0.14f)
            isFocused -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "statusBg"
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent.copy(alpha = 0.55f)
            isFocused -> BioAccent.copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        animationSpec = tween(180),
        label = "statusBorder"
    )
    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) BioAccent else WhiteText.copy(alpha = 0.7f),
        animationSpec = tween(180),
        label = "statusText"
    )

    Row(
        modifier = Modifier
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .tvTouchClick(onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            color = textColor.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Match card — custom focus + tvTouchClick
// ─────────────────────────────────────────────────────────────
@Composable
private fun SportzfyMatchCard(event: SportzfyEvent, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(180),
        label = "cardScale"
    )
    val borderBrush = if (isFocused) {
        Brush.linearGradient(listOf(BioAccent, BioAccent))
    } else {
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(16.dp))
            .background(SportzfyCardBg)
            .border(1.dp, borderBrush, RoundedCornerShape(16.dp))
            .tvTouchClick(onClick)
            .padding(12.dp)
    ) {
        Column {
            // Top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!event.league_icon.isNullOrEmpty()) {
                        AsyncImage(
                            model = event.league_icon,
                            contentDescription = "League Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "${event.sport} || ${event.league}",
                        color = WhiteText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dateObj = parseEventDate(event.starts_at)
                    Column(horizontalAlignment = Alignment.End) {
                        if (dateObj != null && event.is_live == 0) {
                            val dateSdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                            Text(
                                dateSdf.format(dateObj),
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        val timeFormatted = if (dateObj != null) {
                            SimpleDateFormat("hh:mm a", Locale.getDefault())
                                .format(dateObj)
                        } else event.starts_at ?: ""
                        Text(
                            timeFormatted,
                            color = BioAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    if (event.is_live == 1) {
                        val infiniteTransition = rememberInfiniteTransition(label = "liveDot")
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))
                                    )
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = dotAlpha))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "LIVE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Teams
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!event.team_a_logo.isNullOrEmpty()) {
                        AsyncImage(
                            model = event.team_a_logo,
                            contentDescription = "Team A Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(44.dp)
                                .widthIn(max = 64.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        event.team_a_name ?: "Team A",
                        color = WhiteText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    "VS",
                    color = WhiteText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        event.team_b_name ?: "Team B",
                        color = WhiteText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!event.team_b_logo.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        AsyncImage(
                            model = event.team_b_logo,
                            contentDescription = "Team B Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(44.dp)
                                .widthIn(max = 64.dp)
                        )
                    }
                }
            }
        }
    }
}
