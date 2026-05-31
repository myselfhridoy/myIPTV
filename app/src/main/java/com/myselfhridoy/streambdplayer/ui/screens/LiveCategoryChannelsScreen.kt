package com.myselfhridoy.streambdplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.myselfhridoy.streambdplayer.data.models.Channel
import com.myselfhridoy.streambdplayer.ui.navigation.Routes
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavyBackground
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavySurface
import com.myselfhridoy.streambdplayer.ui.theme.TealAccent
import com.myselfhridoy.streambdplayer.ui.theme.WhiteText
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel

@Composable
fun LiveCategoryChannelsScreen(viewModel: MainViewModel, navController: NavController) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val allChannelsMap by viewModel.categoryChannels.collectAsState()

    val categoryName = selectedCategory?.name ?: ""
    val allChannels = allChannelsMap[categoryName]

    var selectedGroup by remember { mutableStateOf("All") }
    var isGroupsDropdownExpanded by remember { mutableStateOf(false) }

    val groups = remember(allChannels) {
        val uniqueGroups = allChannels?.map { it.group }?.distinct()?.sorted() ?: emptyList()
        listOf("All") + uniqueGroups
    }

    val filteredChannels = remember(allChannels, selectedGroup) {
        if (selectedGroup == "All") allChannels else allChannels?.filter { it.group == selectedGroup }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151A25)) // Match mockup
    ) {
        // Custom Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = WhiteText,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { navController.popBackStack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = categoryName,
                color = WhiteText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = WhiteText,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1E2638), CircleShape)
                    .padding(8.dp)
                    .clickable { /* TODO */ }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorites",
                tint = WhiteText,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1E2638), CircleShape)
                    .padding(8.dp)
                    .clickable { /* TODO */ }
            )
        }

        // Chips Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Box {
                    FilterChipItem(
                        text = "Groups",
                        icon = Icons.Default.List,
                        isSelected = false,
                        onClick = { isGroupsDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = isGroupsDropdownExpanded,
                        onDismissRequest = { isGroupsDropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF242C3D))
                    ) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group, color = WhiteText) },
                                onClick = {
                                    selectedGroup = group
                                    isGroupsDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            items(groups.take(10)) { group -> // Limit to first 10 for horizontal scroll performance
                val icon = if (group == "All") Icons.Default.Check else null
                FilterChipItem(
                    text = group,
                    icon = if (selectedGroup == group) Icons.Default.Check else null,
                    isSelected = selectedGroup == group,
                    onClick = { selectedGroup = group }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (allChannels == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else if (filteredChannels.isNullOrEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels available.", color = WhiteText)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4), // Mockup shows 4 columns
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(filteredChannels) { channel ->
                    ChannelCard(channel = channel) {
                        viewModel.selectChannel(channel)
                        navController.navigate(Routes.LIVE_PLAYER)
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
                width = 1.dp,
                color = if (isFocused || isSelected) WhiteText else Color.Gray,
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

@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f) // Adjust height to fit text below without clipping
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) TealAccent else Color(0xFF2B3648),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(6.dp)
        ) {
            // Circular Logo background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF242C3D)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = channel.name,
                color = WhiteText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
