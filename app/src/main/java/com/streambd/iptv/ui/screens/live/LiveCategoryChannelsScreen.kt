package com.streambd.iptv.ui.screens.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.PremiumOptionRow
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.SurfaceDark
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.MainViewModel

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
    val focusManager = LocalFocusManager.current

    val groups = remember(allChannels) {
        val uniqueGroups = allChannels?.map { it.group }?.distinct()?.sorted() ?: emptyList()
        listOf("All") + uniqueGroups
    }

    val filteredChannels = remember(allChannels, selectedGroup, searchQuery) {
        val groupFiltered = if (selectedGroup == "All") allChannels
        else allChannels?.filter { it.group == selectedGroup }
        if (searchQuery.isBlank()) groupFiltered
        else groupFiltered?.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Keep the tap callback referencing the LATEST list even if Compose skips
    // recomposing items whose Channel data hasn't changed.
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
            .background(DarkNavyBackground)
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIconButton(
                onClick = {
                    if (isSearchActive) {
                        isSearchActive = false
                        searchQuery = ""
                    } else {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = WhiteText,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))

            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search channels…", color = GrayText) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BioAccent,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = WhiteText,
                        unfocusedTextColor = WhiteText,
                        cursorColor = BioAccent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .onFocusChanged { isSearchFocused = it.hasFocus }
                )
            } else {
                Text(
                    text = categoryName,
                    color = WhiteText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
        }

        // ── Chips row (only if more than one real group) ──
        val validGroups = groups.filter { it.isNotBlank() }
        if (validGroups.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "groups_button") {
                    FilterChipItem(
                        text = "Groups",
                        icon = Icons.AutoMirrored.Filled.List,
                        isSelected = false,
                        onClick = { isGroupsModalExpanded = true }
                    )
                }
                items(
                    items = validGroups.take(10),
                    key = { it }
                ) { group ->
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

        // ── Body ──
        when {
            allChannels == null -> LoadingState()
            filteredChannels.isNullOrEmpty() -> EmptyState()
            else -> {
                val groupedChannels = remember(filteredChannels) {
                    if (selectedGroup != "All") mapOf(selectedGroup to filteredChannels)
                    else filteredChannels.groupBy { it.group }
                }
                val useGridLayout = selectedGroup != "All" || groupedChannels.size <= 1

                if (useGridLayout) {
                    val channelsList = if (groupedChannels.size == 1)
                        groupedChannels.values.first()
                    else filteredChannels
                    val groupName = if (groupedChannels.size == 1)
                        groupedChannels.keys.first() else selectedGroup

                    Column {
                        if (!groupName.isNullOrBlank() && selectedGroup == "All") {
                            Text(
                                text = groupName.uppercase(),
                                color = GrayText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    start = 16.dp, end = 16.dp, bottom = 8.dp
                                )
                            )
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItems(
                                items = channelsList ?: emptyList(),
                                key = { "${it.name}|${it.url}" }
                            ) { channel ->
                                ChannelCard(channel = channel) {
                                    viewModel.setCurrentPlaylist(currentPlaylist)
                                    viewModel.selectChannel(channel)
                                    navController.navigate(Routes.LIVE_PLAYER) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        groupedChannels.forEach { (group, channels) ->
                            item(key = "grp_$group") {
                                if (group.isNotBlank()) {
                                    Text(
                                        text = group.uppercase(),
                                        color = GrayText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            start = 16.dp, end = 16.dp, bottom = 8.dp
                                        )
                                    )
                                }
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        items = channels,
                                        key = { "${it.name}|${it.url}" }
                                    ) { channel ->
                                        Box(modifier = Modifier.width(140.dp)) {
                                            ChannelCard(channel = channel) {
                                                viewModel.setCurrentPlaylist(currentPlaylist)
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
                }
            }
        }
    }

    // ── Groups modal ──
    if (isGroupsModalExpanded) {
        PremiumDialogScaffold(
            title = "Select Group",
            subtitle = "${groups.size} group${if (groups.size > 1) "s" else ""}",
            onDismiss = { isGroupsModalExpanded = false },
            scrollContent = false
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = groups, key = { it }) { group ->
                    PremiumOptionRow(
                        title = group,
                        selected = selectedGroup == group,
                        onClick = {
                            selectedGroup = group
                            isGroupsModalExpanded = false
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Body states
// ─────────────────────────────────────────────────────────────
@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = BioAccent,
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Loading channels…",
                color = GrayText,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("No channels available.", color = WhiteText)
    }
}

// ─────────────────────────────────────────────────────────────
// Filter chip
// ─────────────────────────────────────────────────────────────
@Composable
fun FilterChipItem(
    text: String,
    icon: ImageVector?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> BioAccent
            isSelected -> WhiteText
            else -> GrayText.copy(alpha = 0.5f)
        },
        animationSpec = tween(180),
        label = "chipBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF353C4B) else Color.Transparent,
        animationSpec = tween(180),
        label = "chipBg"
    )

    Row(
        modifier = Modifier
            .height(36.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .background(bgColor, RoundedCornerShape(18.dp))
            .tvTouchClick(onClick)
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

// ─────────────────────────────────────────────────────────────
// Channel card — custom, predictable on TV + mobile
// ─────────────────────────────────────────────────────────────
@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(200),
        label = "channelScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) BioAccent else Color.Transparent,
        animationSpec = tween(200),
        label = "channelBorder"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.77f) // 16:9
            .onFocusChanged { isFocused = it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(3.dp, borderColor, RoundedCornerShape(8.dp))
            .tvTouchClick(onClick),
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
