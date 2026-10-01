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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsBaseball
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsHockey
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.ui.graphics.vector.ImageVector
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
// Palette
// ─────────────────────────────────────────────────────────────
private val CardSurface = Color(0xFF161D2D)
private val CircleIdle = Color(0xFF1B2233)
private val CircleIdleBorder = Color(0xFF252D3E)
private val CountBadgeRed = Color(0xFFE53935)
private val LiveRed = Color(0xFFFF3B3B)
private val OnAccent = Color(0xFF0A0E17)

// ─────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────
private fun parseEventDate(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val cleanStr = dateStr.replace(Regex("([+-]\\d\\d):(\\d\\d)$"), "$1$2")
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(cleanStr)
    } catch (_: Exception) {
        null
    }
}

/** Map sport name → Material icon. */
private fun sportIcon(sport: String?): ImageVector {
    val s = sport?.lowercase() ?: ""
    return when {
        s.contains("cricket") -> Icons.Default.SportsCricket
        s.contains("football") || s.contains("soccer") -> Icons.Default.SportsSoccer
        s.contains("motorsport") || s.contains("motor") || s.contains("racing") -> Icons.Default.SportsMotorsports
        s.contains("wrestling") || s.contains("wwe") || s.contains("mma") || s.contains("ufc") -> Icons.Default.SportsMma
        s.contains("basketball") -> Icons.Default.SportsBasketball
        s.contains("tennis") -> Icons.Default.SportsTennis
        s.contains("volleyball") -> Icons.Default.SportsVolleyball
        s.contains("hockey") -> Icons.Default.SportsHockey
        s.contains("baseball") -> Icons.Default.SportsBaseball
        s.contains("kabaddi") -> Icons.Default.SportsKabaddi
        s.contains("esport") || s.contains("gaming") -> Icons.Default.SportsEsports
        else -> Icons.Default.SportsSoccer
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
    var selectedStatus by remember { mutableStateOf("All") }
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
            "Today" -> list.filter {
                val d = parseEventDate(it.starts_at)
                d != null && SimpleDateFormat("yyyyMMdd", Locale.US).format(d) ==
                    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            }
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

    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) eventsViewModel.fetchEvents()
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) pullToRefreshState.endRefresh()
    }

    BackHandler(enabled = isSearchActive) {
        if (isSearchFocused) focusManager.clearFocus()
        else { searchQuery = ""; isSearchActive = false }
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
                            Icons.Default.Menu, "Menu",
                            tint = WhiteText, modifier = Modifier.size(22.dp)
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
                            .height(46.dp)
                            .onFocusChanged { isSearchFocused = it.hasFocus },
                        placeholder = {
                            Text("Search teams or leagues…", color = GrayText, fontSize = 13.sp)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CircleIdle,
                            unfocusedContainerColor = CircleIdle,
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
                        onClick = { isSearchActive = false; searchQuery = "" },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = WhiteText, modifier = Modifier.size(22.dp))
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
                        Icon(Icons.Default.Search, "Search", tint = WhiteText, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // ── Category circles ──
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items = categories, key = { it }) { cat ->
                    CategoryCircle(
                        name = cat,
                        count = sportsCount[cat] ?: 0,
                        icon = if (cat == "All") Icons.Default.SportsSoccer else sportIcon(cat),
                        isSelected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Filter chips ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val liveCount = events.count { it.is_live == 1 }
                val todayCount = events.count {
                    val d = parseEventDate(it.starts_at)
                    d != null && SimpleDateFormat("yyyyMMdd", Locale.US).format(d) ==
                        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                }
                val upcomingCount = events.count {
                    it.is_live == 0 && (parseEventDate(it.starts_at)?.time ?: Long.MAX_VALUE) >
                        System.currentTimeMillis()
                }

                FilterChipWithIcon(
                    label = "All",
                    count = events.size,
                    icon = Icons.Default.Check,
                    isSelected = selectedStatus == "All",
                    onClick = { selectedStatus = "All" }
                )
                FilterChipWithIcon(
                    label = "Live",
                    count = liveCount,
                    icon = Icons.Default.Podcasts,
                    isSelected = selectedStatus == "Live",
                    onClick = { selectedStatus = "Live" }
                )
                FilterChipWithIcon(
                    label = "Today",
                    count = todayCount,
                    icon = Icons.Default.Schedule,
                    isSelected = selectedStatus == "Today",
                    onClick = { selectedStatus = "Today" }
                )
                FilterChipWithIcon(
                    label = "Upcoming",
                    count = upcomingCount,
                    icon = Icons.Default.Schedule,
                    isSelected = selectedStatus == "Upcoming",
                    onClick = { selectedStatus = "Upcoming" }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Content ──
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BioAccent, strokeWidth = 3.dp)
                    }
                }
                filteredEvents.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No $selectedStatus events",
                                color = WhiteText.copy(alpha = 0.6f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Try a different filter",
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
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = filteredEvents,
                            key = { it.id ?: it.hashCode() }
                        ) { event ->
                            PlayZStyleMatchCard(event) {
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = streams,
                    key = { "${it.label ?: ""}|${it.stream_url ?: ""}" }
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
                            navController.navigate(Routes.LIVE_PLAYER) { launchSingleTop = true }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Category circle — icon + count badge + label below
// ─────────────────────────────────────────────────────────────
@Composable
private fun CategoryCircle(
    name: String,
    count: Int,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val circleBg by animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent.copy(alpha = 0.14f)
            isFocused -> CircleIdleBorder
            else -> CircleIdle
        },
        animationSpec = tween(180),
        label = "circleBg"
    )
    val circleBorder by animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent
            isFocused -> BioAccent.copy(alpha = 0.7f)
            else -> CircleIdleBorder
        },
        animationSpec = tween(180),
        label = "circleBorder"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) BioAccent else WhiteText.copy(alpha = 0.8f),
        animationSpec = tween(180),
        label = "iconColor"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) BioAccent else WhiteText.copy(alpha = 0.65f),
        animationSpec = tween(180),
        label = "labelColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.hasFocus }
            .tvTouchClick(onClick)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(circleBg)
                    .border(
                        width = if (isSelected || isFocused) 2.dp else 1.dp,
                        color = circleBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(CountBadgeRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Filter chip with icon (All / Live / Today / Upcoming)
// ─────────────────────────────────────────────────────────────
@Composable
private fun FilterChipWithIcon(
    label: String,
    count: Int,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bg by animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent.copy(alpha = 0.14f)
            isFocused -> CircleIdleBorder
            else -> CircleIdle
        },
        animationSpec = tween(180),
        label = "chipBg"
    )
    val border by animateColorAsState(
        targetValue = when {
            isSelected -> BioAccent
            isFocused -> BioAccent.copy(alpha = 0.6f)
            else -> CircleIdleBorder
        },
        animationSpec = tween(180),
        label = "chipBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) BioAccent else WhiteText.copy(alpha = 0.7f),
        animationSpec = tween(180),
        label = "chipContent"
    )

    Row(
        modifier = Modifier
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(
                width = 1.dp,
                color = border,
                shape = RoundedCornerShape(50)
            )
            .tvTouchClick(onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "($count)",
            color = contentColor.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Match card — PlayZ TV style
//   • Center title
//   • Big circular team logos on each side
//   • Broadcast icon + Live label + time in the middle
//   • HOT ribbon when live
// ─────────────────────────────────────────────────────────────
@Composable
private fun PlayZStyleMatchCard(
    event: SportzfyEvent,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(180),
        label = "cardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) BioAccent.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = tween(180),
        label = "cardBorder"
    )

    val dateObj = parseEventDate(event.starts_at)
    val timeText = if (dateObj != null) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(dateObj)
    } else event.starts_at ?: "--:--"

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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            // ── Center title ──
            Text(
                text = listOfNotNull(event.sport, event.league)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = WhiteText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // ── Team row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left team
                MatchTeamColumn(
                    name = event.team_a_name ?: "Team A",
                    logoUrl = event.team_a_logo,
                    modifier = Modifier.weight(1f)
                )

                // Center live section
                Column(
                    modifier = Modifier.width(96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (event.is_live == 1) {
                        LiveIndicator()
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = timeText,
                        color = WhiteText.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Right team
                MatchTeamColumn(
                    name = event.team_b_name ?: "Team B",
                    logoUrl = event.team_b_logo,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── HOT ribbon (only when live) ──
        if (event.is_live == 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(LiveRed)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "HOT",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// One side of a match (logo + name stacked)
// ─────────────────────────────────────────────────────────────
@Composable
private fun MatchTeamColumn(
    name: String,
    logoUrl: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F2739))
                .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!logoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            } else {
                Text(
                    text = name.take(2).uppercase(),
                    color = BioAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            color = WhiteText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Broadcast indicator — pulsing red icon + "Live" label
// ─────────────────────────────────────────────────────────────
@Composable
private fun LiveIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Podcasts,
            contentDescription = "Live",
            tint = LiveRed.copy(alpha = alpha),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Live",
            color = LiveRed.copy(alpha = alpha),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
