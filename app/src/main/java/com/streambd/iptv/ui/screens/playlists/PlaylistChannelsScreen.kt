package com.streambd.iptv.ui.screens.playlists

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.streambd.iptv.data.models.Channel
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.DarkNavySurface
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.PlaylistChannelsViewModel
import androidx.activity.compose.BackHandler
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.components.tvFocusBorder
import java.net.URLEncoder

import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
import com.streambd.iptv.ui.theme.PrimaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistChannelsScreen(
    playlistTitle: String,
    playlistUrl: String,
    viewModel: PlaylistChannelsViewModel,
    mainViewModel: MainViewModel,
    navController: NavController
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val allGroups by viewModel.groups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val channels by viewModel.displayedChannels.collectAsState()

    var isGroupsModalExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    val currentPlaylist by rememberUpdatedState(channels)

    LaunchedEffect(playlistUrl) {
        viewModel.loadPlaylist(playlistUrl)
    }

    val filteredChannels = remember(channels, searchQuery) {
        if (searchQuery.isBlank()) {
            channels
        } else {
            channels.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    BackHandler(enabled = isSearchActive) {
        searchQuery = ""
        isSearchActive = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaryDark)
    ) {
        // Custom Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { 
                if (isSearchActive) {
                    isSearchActive = false
                    searchQuery = ""
                } else {
                    navController.popBackStack() 
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = "Back", 
                    tint = WhiteText,
                    modifier = Modifier.tvFocusBorder(CircleShape)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search channels...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = WhiteText,
                        unfocusedTextColor = WhiteText,
                        cursorColor = TealAccent
                    ),
                    modifier = Modifier.weight(1f).height(50.dp)
                )
            } else {
                Text(
                    text = playlistTitle,
                    color = WhiteText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = WhiteText,
                        modifier = Modifier
                            .tvFocusBorder(CircleShape)
                            .background(com.streambd.iptv.ui.theme.SurfaceDark, CircleShape)
                            .padding(8.dp)
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else {
            // Chips Row
            if (allGroups.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChipItem(
                            text = "Groups",
                            icon = Icons.AutoMirrored.Filled.List,
                            isSelected = false,
                            onClick = { isGroupsModalExpanded = true }
                        )
                    }
                    
                    items(allGroups.take(10)) { group ->
                        FilterChipItem(
                            text = group,
                            icon = if (selectedGroup == group) Icons.Default.Check else null,
                            isSelected = selectedGroup == group,
                            onClick = { viewModel.selectGroup(group) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredChannels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No channels available.", color = WhiteText)
                }
            } else {
                val groupedChannels = remember(filteredChannels, selectedGroup) {
                    if (selectedGroup != "All") {
                        mapOf(selectedGroup to filteredChannels)
                    } else {
                        filteredChannels.groupBy { it.group }
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    groupedChannels.forEach { (group, groupChannels) ->
                        item(key = group ?: "Unknown") {
                            Text(
                                text = (group ?: "Unknown").uppercase(),
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(groupChannels) { channel ->
                                    Box(modifier = Modifier.width(180.dp)) {
                                        PlaylistChannelCard(channel) {
                                            viewModel.selectChannel(channel)
                                            mainViewModel.setCurrentPlaylist(currentPlaylist)
                                            mainViewModel.selectChannel(channel)
                                            navController.navigate("live_player")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isGroupsModalExpanded) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { isGroupsModalExpanded = false }) {
            androidx.compose.material3.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color(0xFF1C2230)),
                shape = RoundedCornerShape(16.dp)
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Select Group", 
                            color = WhiteText, 
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.Bold, 
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    items(allGroups) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusBorder(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.selectGroup(group)
                                    isGroupsModalExpanded = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedGroup == group) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = TealAccent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                            } else {
                                Spacer(modifier = Modifier.width(32.dp))
                            }
                            Text(group, color = if (selectedGroup == group) TealAccent else WhiteText, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .height(36.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) TealAccent else if (isSelected) WhiteText else Color.Gray,
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                color = if (isSelected) Color(0xFF353C4B) else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WhiteText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = WhiteText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistChannelCard(channel: Channel, onClick: () -> Unit) {
    androidx.tv.material3.Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.77f) // 16:9 aspect ratio
            .tvTouchClick(onClick),
        colors = androidx.tv.material3.CardDefaults.colors(
            containerColor = Color.White,
            focusedContainerColor = Color.White
        ),
        shape = androidx.tv.material3.CardDefaults.shape(RoundedCornerShape(4.dp)), // Slight rounded corners
        scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1.05f),
        glow = androidx.tv.material3.CardDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(elevationColor = Color.Black.copy(alpha = 0.5f), elevation = 16.dp)
        ),
        border = androidx.tv.material3.CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.Red),
                inset = 0.dp,
                shape = RoundedCornerShape(4.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logo.isNotEmpty()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            } else {
                Text(
                    text = channel.name,
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
