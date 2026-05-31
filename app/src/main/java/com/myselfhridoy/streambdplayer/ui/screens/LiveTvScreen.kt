package com.myselfhridoy.streambdplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import com.myselfhridoy.streambdplayer.data.models.LiveCategory
import com.myselfhridoy.streambdplayer.ui.navigation.Routes
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavyBackground
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavySurface
import com.myselfhridoy.streambdplayer.ui.theme.TealAccent
import com.myselfhridoy.streambdplayer.ui.theme.WhiteText
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel

@Composable
fun LiveTvScreen(viewModel: MainViewModel, navController: NavController) {
    val categories by viewModel.liveCategories.collectAsState()
    val isLoading by viewModel.isLoadingLive.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF151A25)) // Darker Navy to match mockup
    ) {
        // Custom Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = WhiteText,
                modifier = Modifier.size(28.dp).clickable { /* TODO */ }
            )
            Spacer(modifier = Modifier.width(24.dp))
            Text(
                text = "Categories",
                color = WhiteText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
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

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { category ->
                    CategoryCard(category = category) {
                        viewModel.selectCategory(category)
                        navController.navigate(Routes.LIVE_CATEGORY_CHANNELS)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCard(category: LiveCategory, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) TealAccent else Color(0xFF2B3648), // Mockup has a subtle border
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF242C3D)),
                contentAlignment = Alignment.Center
            ) {
                if (category.logo.isNotEmpty()) {
                    AsyncImage(
                        model = category.logo,
                        contentDescription = category.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = category.name,
                color = WhiteText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
