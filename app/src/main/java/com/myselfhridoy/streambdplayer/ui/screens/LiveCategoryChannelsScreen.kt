package com.myselfhridoy.streambdplayer.ui.screens

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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.myselfhridoy.streambdplayer.data.models.Channel
import com.myselfhridoy.streambdplayer.ui.navigation.Routes
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavyBackground
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavySurface
import com.myselfhridoy.streambdplayer.ui.theme.TealAccent
import com.myselfhridoy.streambdplayer.ui.theme.WhiteText
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCategoryChannelsScreen(viewModel: MainViewModel, navController: NavController) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val allChannelsMap by viewModel.categoryChannels.collectAsState()

    val categoryName = selectedCategory?.name ?: ""
    val allChannels = allChannelsMap[categoryName]

    var selectedGroup by remember { mutableStateOf("All") }
    var isGroupsModalExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

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
    
    val focusManager = LocalFocusManager.current

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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = WhiteText,
                modifier = Modifier
                    .size(28.dp)
                    .tvFocusBorder(CircleShape)
                    .clickable { 
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            navController.popBackStack() 
                        }
                    }
            )
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
                        .tvFocusBorder(CircleShape)
                        .background(Color(0xFF1E2638), CircleShape)
                        .clickable { isSearchActive = true }
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorites",
                    tint = WhiteText,
                    modifier = Modifier
                        .size(40.dp)
                        .tvFocusBorder(CircleShape)
                        .background(Color(0xFF1E2638), CircleShape)
                        .clickable { /* TODO */ }
                        .padding(8.dp)
                )
            }
        }

        // Chips Row
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
            
            items(groups.take(10)) { group -> // Limit to first 10 for horizontal scroll performance
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
                columns = GridCells.Adaptive(minSize = 80.dp), // Automatically adapts to TV and Mobile width
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

    if (isGroupsModalExpanded) {
        ModalBottomSheet(
            onDismissRequest = { isGroupsModalExpanded = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1C2230)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                item { Spacer(modifier = Modifier.height(32.dp)) }
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
