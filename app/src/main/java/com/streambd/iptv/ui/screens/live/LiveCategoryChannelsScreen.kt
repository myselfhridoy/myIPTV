package com.streambd.iptv.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Border
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
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
import com.streambd.iptv.ui.components.tvFocusBorder
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.DarkNavySurface
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.theme.PrimaryDark
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.MainViewModel
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCategoryChannelsScreen(viewModel: MainViewModel, navController: NavController) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val allChannelsMap by viewModel.categoryChannels.collectAsState()

    val categoryName = selectedCategory?.name ?: ""
    val allChannels = allChannelsMap[categoryName]

    var selectedGroup by rememberSaveable { mutableStateOf("All") }
    var isGroupsModalExpanded by rememberSaveable { mutableStateOf(false) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val groups = remember(allChannels) {
        val uniqueGroups = allChannels?.map { it.group }?.distinct()?.sorted() ?: emptyList()
        listOf("All") + uniqueGroups
    }

    val filteredChannels = remember(allChannels, selectedGroup, searchQuery) {
        val groupFiltered = if (selectedGroup == "All") allChannels else allChannels?.filter { it.group == selectedGroup }
        if (searchQuery.isBlank()) {
            groupFiltered
        } else {
            groupFiltered?.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Use rememberUpdatedState so the click lambda inside LazyVerticalGrid items
    // always captures the LATEST filteredChannels, even when Compose skips
    // recomposition of items whose Channel data hasn't changed.
    val currentPlaylist by rememberUpdatedState(filteredChannels ?: emptyList())

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
            .background(PrimaryDark) // Match mockup
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
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .onFocusChanged { isSearchFocused = it.isFocused }
                )
            } else {
                Text(
                    text = categoryName,
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

        // Chips Row - only show when there are multiple valid groups
        val validGroups = groups.filter { it.isNotBlank() }
        if (validGroups.size > 1) { // > 1 because "All" + at least 1 real group
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

                items(validGroups.take(10)) { group ->
                    FilterChipItem(
                        text = group,
                        icon = if (selectedGroup == group) Icons.Default.Check else null,
                        isSelected = selectedGroup == group,
                        onClick = { selectedGroup = group }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (allChannels == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = TealAccent,
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading channels...",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (filteredChannels.isNullOrEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels available.", color = WhiteText)
            }
        } else {
            val groupedChannels = remember(filteredChannels) {
                if (selectedGroup != "All") {
                    mapOf(selectedGroup to filteredChannels)
                } else {
                    filteredChannels.groupBy { it.group }
                }
            }

            // Use grid when a specific group is selected OR there's only one group
            val useGridLayout = selectedGroup != "All" || groupedChannels.size <= 1

            if (useGridLayout) {
                val channelsList = if (groupedChannels.size == 1) groupedChannels.values.first() else filteredChannels
                val groupName = if (groupedChannels.size == 1) groupedChannels.keys.first() else selectedGroup
                Column {
                    if (!groupName.isNullOrBlank() && selectedGroup == "All") {
                        Text(
                            text = groupName.uppercase(),
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(channelsList!!) { channel ->
                            ChannelCard(channel = channel) {
                                viewModel.setCurrentPlaylist(currentPlaylist)
                                viewModel.selectChannel(channel)
                                navController.navigate(Routes.LIVE_PLAYER)
                            }
                        }
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    groupedChannels.forEach { (group, channels) ->
                        item(key = group) {
                            if (group.isNotBlank()) {
                                Text(
                                    text = group.uppercase(),
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(channels) { channel ->
                                    Box(modifier = Modifier.width(140.dp)) {
                                        ChannelCard(channel = channel) {
                                            viewModel.setCurrentPlaylist(currentPlaylist)
                                            viewModel.selectChannel(channel)
                                            navController.navigate(Routes.LIVE_PLAYER)
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
                    items(groups) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusBorder(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedGroup = group
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.77f) // 16:9 aspect ratio
            .tvTouchClick(onClick),
        colors = CardDefaults.colors(
            containerColor = Color.White,
            focusedContainerColor = Color.White
        ),
        shape = CardDefaults.shape(RoundedCornerShape(4.dp)), // Slight rounded corners
        scale = CardDefaults.scale(focusedScale = 1.05f),
        glow = CardDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.5f), elevation = 16.dp)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
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
