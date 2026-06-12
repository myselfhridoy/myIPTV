package com.streambd.iptv.ui.screens.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.data.models.StreamedMatch
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.DarkNavySurface
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveEventsScreen(
    eventsViewModel: LiveEventsViewModel,
    mainViewModel: MainViewModel,
    navController: NavController,
    onMenuClick: () -> Unit
) {
    val events by eventsViewModel.events.collectAsState()
    val isLoading by eventsViewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .padding(top = 16.dp)
    ) {
        // Simple Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Menu", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Live Events", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Live Events Available", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(events) { event ->
                    EventCard(
                        event = event,
                        onClick = {
                            val sourcesMap = mutableMapOf<String, String>()
                            event.sources.forEachIndexed { idx, s ->
                                val sName = s.source ?: "Source"
                                val sId = s.id ?: ""
                                if (sId.isNotEmpty()) {
                                    val keyName = if (event.sources.size > 1) "$sName ${idx + 1}" else sName
                                    sourcesMap[keyName] = "https://embed.st/embed/$sName/$sId/1"
                                }
                            }

                            if (sourcesMap.isNotEmpty()) {
                                val defaultUrl = sourcesMap.values.first()
                                val channelObj = Channel(
                                    name = event.title ?: "Live Event",
                                    url = defaultUrl,
                                    group = event.category ?: "Live Events",
                                    tokenUrl = "StreamBD",
                                    isLiveEvent = true,
                                    sources = sourcesMap
                                )
                                mainViewModel.selectChannel(channelObj)
                                navController.navigate(Routes.LIVE_PLAYER)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EventCard(event: StreamedMatch, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkNavySurface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title ?: "Unknown Match",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.category?.replace("-", " ")?.capitalize() ?: "Event",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dateFormatted = event.date?.let {
                        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(it))
                    } ?: "--:--"
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TealAccent.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dateFormatted,
                            color = TealAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(TealAccent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = TealAccent, modifier = Modifier.size(28.dp))
            }
        }
    }
}
