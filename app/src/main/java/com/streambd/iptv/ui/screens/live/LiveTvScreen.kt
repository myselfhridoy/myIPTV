package com.streambd.iptv.ui.screens.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.activity.compose.BackHandler
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.tv.material3.Glow
import androidx.tv.material3.Border
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.streambd.iptv.ui.components.tvFocusBorder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.streambd.iptv.data.models.LiveCategory
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.theme.PrimaryDark
import com.streambd.iptv.ui.theme.SurfaceDark
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.MainViewModel

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTvScreen(viewModel: MainViewModel, navController: NavController, onMenuClick: (() -> Unit)? = null) {
    val tvModePlaylists by viewModel.tvModePlaylists.collectAsState()
    val categories by viewModel.liveCategories.collectAsState()
    val isLoading by viewModel.isLoadingLive.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val isTV = remember {
        context.packageManager.hasSystemFeature("android.software.leanback")
    }

    val columns = when {
        isTV -> 5
        isLandscape -> 4
        else -> 2
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    val displayedCategories = remember(searchQuery, categories) {
        if (searchQuery.isBlank()) categories
        else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            kotlinx.coroutines.delay(100)
            try { searchFocusRequester.requestFocus() } catch (_: Exception) {}
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
            if (!isSearchActive) {
                if (onMenuClick != null) {
                    androidx.compose.material3.IconButton(onClick = { onMenuClick() }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = WhiteText,
                            modifier = Modifier.tvFocusBorder(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = "Live TV",
                    color = WhiteText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!isTV) {
                    androidx.compose.material3.IconButton(onClick = { isSearchActive = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = WhiteText,
                            modifier = Modifier
                                .tvFocusBorder(CircleShape)
                                .background(SurfaceDark, CircleShape)
                                .padding(8.dp)
                        )
                    }
                }
            } else {
                // Search Mode
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search channels & categories...", color = Color.Gray, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TealAccent)
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close Search",
                            tint = Color.Gray,
                            modifier = Modifier.clickable {
                                searchQuery = ""
                                isSearchActive = false
                            }
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = Color(0xFF2B3648),
                        focusedTextColor = WhiteText,
                        unfocusedTextColor = WhiteText,
                        cursorColor = TealAccent,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                )
            }
        }



        // Search result count hint
        AnimatedVisibility(visible = isSearchActive && searchQuery.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = "${displayedCategories.size} result(s) for \"$searchQuery\"",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else if (displayedCategories.isEmpty() && searchQuery.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No results found for \"$searchQuery\"", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), // TV Gutters
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(displayedCategories) { category ->
                    CategoryCard(category = category) {
                        viewModel.selectCategory(context, category)
                        navController.navigate(Routes.LIVE_CATEGORY_CHANNELS) {
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CategoryCard(category: LiveCategory, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .tvFocusBorder(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C2230), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF2B3648), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF242C3D))
                .border(1.dp, Color.DarkGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (category.logo.isNotEmpty()) {
                AsyncImage(
                    model = category.logo,
                    contentDescription = category.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Text(
            text = category.name,
            color = WhiteText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = 30.dp
                )
        )
    }
}
