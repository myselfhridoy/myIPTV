package com.streambd.iptv.ui.screens.events

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// Local palette — restrained, single accent, minimal neutrality
// ─────────────────────────────────────────────────────────────
private val CardSurface = Color(0xFF161D2D)
private val ChipIdle = Color(0xFF1B2233)
private val ChipIdleBorder = Color(0xFF252D3E)
private val OnAccent = Color(0xFF0A0E17)     // text on BioAccent fill

// ─────────────────────────────────────────────────────────────
// Date parsing
// ─────────────────────────────────────────────────────────────
private fun parseEventDate(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val cleanStr = dateStr.replace(Regex("([+-]\\d\\d):(\\d\\d)$"), "$1$2")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        sdf.parse(cleanStr)
    } catch (_: Exception) {
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
            .background(Color(0xFF0A0D14))
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .onFocusChanged { isSearchFocused = it.hasFocus },
                        placeholder = {
                            Text(
                                "Search teams or leagues…",
                                color = GrayText,
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ChipIdle,
                            unfocusedContainerColor = ChipIdle,
                            focusedIndicatorColor = BioAccent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = WhiteText,
                            unfocusedTextColor = WhiteText,
                            cursorColor = BioAccent
                        ),
                        shape = RoundedCornerShape(12.dp),
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
                            contentDescription = "Close",
                            tint = WhiteText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Text(
                        "Live Events",
                        color = WhiteText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
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

            // ── Category pills ──
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            // ── Status segmented control ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                StatusSegment("Recent", recentCount, selectedStatus == "Recent") {
                    selectedStatus = "Recent"
                }
                StatusSegment("Live", liveCount, selectedStatus == "Live") {
                    selectedStatus = "Live"
                }
                StatusSegment("Upcoming", upcomingCount, selectedStatus == "Upcoming") {
                    selectedStatus = "Upcoming"
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Content ──
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BioAccent, strokeWidth = 3.dp)
                    }
                }
                filteredEvents.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No $selectedStatus events",
                                color = WhiteText.copy(alpha = 0.6f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try a different filter",
                                color = GrayText.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 4.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = filteredEvents,
                            key = { it.id ?: it.hashCode() }
                        ) { event ->
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
            containerColor = CardSurface,
            contentColor = BioAccent
        )
    }

    // ── Multi-source picker ──
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
                    val streamName = stream.label?.takeIf { !it.isNullOrBlank() } ?: "Source"
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
// Category pill — solid BioAccent fill when selected
// ─────────────────────────────────────────────────────────────
@Composable
private fun CategoryPill(
    name: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent
            isFocused -> ChipIdleBorder
            else -> ChipIdle
        },
        animationSpec = tween(180),
        label = "catBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) OnAccent else WhiteText,
        animationSpec = tween(180),
        label = "catText"
    )
    val countColor by animateColorAsState(
        targetValue = if (isSelected) OnAccent.copy(alpha = 0.55f)
        else WhiteText.copy(alpha = 0.4f),
        animationSpec = tween(180),
        label = "catCount"
    )

    Row(
        modifier = Modifier
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .tvTouchClick(onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                color = countColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Status segment — segmented control feel (not a chip)
// ─────────────────────────────────────────────────────────────
@Composable
private fun StatusSegment(
    name: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent
            isFocused -> WhiteText
            else -> WhiteText.copy(alpha = 0.55f)
        },
        animationSpec = tween(180),
        label = "segText"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (isSelected) BioAccent else Color.Transparent,
        animationSpec = tween(180),
        label = "segIndicator"
    )

    Column(
        modifier = Modifier
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(10.dp))
            .tvTouchClick(onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = count.toString(),
                color = textColor.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(if (isSelected) 20.dp else 0.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(indicatorColor)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Match card — clean, minimal, single accent
// ─────────────────────────────────────────────────────────────
@Composable
private fun SportzfyMatchCard(event: SportzfyEvent, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(180),
        label = "cardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) BioAccent.copy(alpha = 0.55f) else Color.Transparent,
        animationSpec = tween(180),
        label = "cardBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .tvTouchClick(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            // ── Top row: league · time / live ──
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
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = listOfNotNull(event.sport, event.league)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = WhiteText.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dateObj = parseEventDate(event.starts_at)
                    Column(horizontalAlignment = Alignment.End) {
                        if (dateObj != null && event.is_live == 0) {
                            Text(
                                SimpleDateFormat("dd MMM", Locale.getDefault()).format(dateObj),
                                color = GrayText.copy(alpha = 0.65f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        val timeFormatted = if (dateObj != null) {
                            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(dateObj)
                        } else event.starts_at ?: ""
                        Text(
                            timeFormatted,
                            color = BioAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    if (event.is_live == 1) {
                        LiveBadge()
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Teams — symmetric 3-column ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: name (right-aligned) + logo
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = event.team_a_name ?: "Team A",
                        color = WhiteText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!event.team_a_logo.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        TeamLogo(url = event.team_a_logo)
                    }
                }

                // Center: VS anchor
                Box(
                    modifier = Modifier.width(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "VS",
                        color = WhiteText.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Right: logo + name (left-aligned)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (!event.team_b_logo.isNullOrEmpty()) {
                        TeamLogo(url = event.team_b_logo)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = event.team_b_name ?: "Team B",
                        color = WhiteText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Live badge — subtle pulse, no screaming gradient
// ─────────────────────────────────────────────────────────────
@Composable
private fun LiveBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(BioAccent.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(BioAccent.copy(alpha = dotAlpha))
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            "LIVE",
            color = BioAccent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Team logo — fixed size for perfect alignment
// ─────────────────────────────────────────────────────────────
@Composable
private fun TeamLogo(url: String) {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 30.dp)
            .clip(RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
