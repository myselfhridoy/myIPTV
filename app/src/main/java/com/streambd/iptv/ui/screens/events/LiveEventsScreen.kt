package com.streambd.iptv.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.DarkNavySurface
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

val SportzfyDarkBg = Color(0xFF0B0F19)
val SportzfyCardBg = Color(0xFF161D2D).copy(alpha = 0.85f)
val SportzfyStroke = Color(0xFF2A344A)

@Composable
fun LiveEventsScreen(
    eventsViewModel: LiveEventsViewModel,
    mainViewModel: MainViewModel,
    navController: NavController,
    onMenuClick: () -> Unit
) {
    val events by eventsViewModel.events.collectAsState()
    val isLoading by eventsViewModel.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf("Live") } // "Recent", "Live", "Upcoming"
    
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
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
        var list = if (selectedCategory == "All") events else events.filter { it.sport == selectedCategory }
        list = when (selectedStatus) {
            "Live" -> list.filter { it.is_live == 1 }
            "Upcoming" -> list.filter { it.is_live == 0 } // simplified logic
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F1423), Color(0xFF0A0D14))
                )
            )
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
            }
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(50.dp),
                    placeholder = { Text("Search matches...", color = Color.Gray) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedIndicatorColor = TealAccent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(25.dp),
                    singleLine = true
                )
                IconButton(onClick = { 
                    isSearchActive = false 
                    searchQuery = ""
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close Search", tint = Color.White)
                }
            } else {
                Text(
                    "Live Events", 
                    color = TealAccent, 
                    fontSize = 26.sp, 
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
            }
        }

        // Circular Categories Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(categories) { cat ->
                CategoryCircle(
                    name = cat,
                    count = sportsCount[cat] ?: 0,
                    isSelected = selectedCategory == cat,
                    onClick = { selectedCategory = cat }
                )
            }
        }

        // Status Chips (Recent, Live, Upcoming)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusChip("Recent", 0, selectedStatus == "Recent") { selectedStatus = "Recent" }
            val liveCount = events.count { it.is_live == 1 }
            StatusChip("Live", liveCount, selectedStatus == "Live") { selectedStatus = "Live" }
            val upcomingCount = events.count { it.is_live == 0 }
            StatusChip("Upcoming", upcomingCount, selectedStatus == "Upcoming") { selectedStatus = "Upcoming" }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else if (filteredEvents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No ${selectedStatus} events for ${selectedCategory}", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            // Match Cards List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredEvents) { event ->
                    SportzfyMatchCard(event) {
                        coroutineScope.launch {
                            val streams = eventsViewModel.getStreamsForEvent(event.id ?: "")
                            if (streams.isNotEmpty()) {
                                // Just play the first stream for now
                                val stream = streams.first()
                                val ch = Channel(
                                    name = event.league ?: "Live Event",
                                    url = stream.stream_url ?: "",
                                    group = event.sport ?: "Live",
                                    isLiveEvent = true,
                                    drm = if (stream.drm_key != null) DrmConfig(
                                        type = "clearkey",
                                        rawKeyPair = "${stream.drm_kid}:${stream.drm_key}"
                                    ) else null
                                )
                                mainViewModel.selectChannel(ch)
                                navController.navigate(Routes.LIVE_PLAYER)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCircle(name: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) TealAccent else Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .background(
                    if (isSelected) TealAccent.copy(alpha = 0.15f) else Color(0xFF1E2638).copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Icon Placeholder
            Text(name.take(2).uppercase(Locale.ROOT), color = if (isSelected) TealAccent else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            
            // Notification Badge
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFF4B2B), Color(0xFFFF416C))))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(name, color = if (isSelected) Color.White else Color.LightGray, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun StatusChip(name: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (isSelected) TealAccent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) TealAccent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            "$name ($count)", 
            color = if (isSelected) TealAccent else Color.LightGray, 
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SportzfyMatchCard(event: SportzfyEvent, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp, 
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                ), 
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = SportzfyCardBg),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // League Info
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Time & Live Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val timeFormatted = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        val dateObj = sdf.parse(event.starts_at ?: "")
                        val outSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        if (dateObj != null) outSdf.format(dateObj) else event.starts_at
                    } catch (e: Exception) {
                        event.starts_at ?: ""
                    }

                    Text(
                        text = timeFormatted ?: "",
                        color = TealAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (event.is_live == 1) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Teams Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Team A
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (!event.team_a_logo.isNullOrEmpty()) {
                        AsyncImage(
                            model = event.team_a_logo,
                            contentDescription = "Team A Logo",
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = event.team_a_name ?: "Team A",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // VS
                Text(
                    text = "VS",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // Team B
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = event.team_b_name ?: "Team B",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!event.team_b_logo.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AsyncImage(
                            model = event.team_b_logo,
                            contentDescription = "Team B Logo",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}
