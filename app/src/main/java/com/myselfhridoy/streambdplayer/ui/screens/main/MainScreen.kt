package com.myselfhridoy.streambdplayer.ui.screens.main

import com.myselfhridoy.streambdplayer.ui.screens.live.LiveTvScreen
import com.myselfhridoy.streambdplayer.ui.screens.vod.VodScreen
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.myselfhridoy.streambdplayer.ui.theme.*
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel

data class TabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(mainViewModel: MainViewModel, tmdbViewModel: TmdbViewModel, navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        TabItem("Live TV", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
        TabItem("Media", Icons.Filled.MovieFilter, Icons.Outlined.MovieFilter),
        TabItem("Playlists", Icons.Filled.Folder, Icons.Outlined.Folder)
    )

    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isTv = remember(configuration) {
        val uiMode = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
            true
        } else {
            val pm = context.packageManager
            !pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN) || 
            pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        }
    }

    if (isTv) {
        // ── TV Layout: Premium Sidebar ──
        Row(modifier = Modifier.fillMaxSize().background(DarkNavyBackground)) {
            // Premium TV NavigationRail — Floating Glass Panel
            Box(
                modifier = Modifier
                    .padding(vertical = 20.dp, horizontal = 10.dp)
                    .width(80.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1F2E).copy(alpha = 0.95f),
                                Color(0xFF141920).copy(alpha = 0.98f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        var isFocused by remember { mutableStateOf(false) }

                        val bgColor by animateColorAsState(
                            targetValue = when {
                                isSelected -> TealAccent.copy(alpha = 0.18f)
                                isFocused -> TealAccent.copy(alpha = 0.08f)
                                else -> Color.Transparent
                            },
                            animationSpec = tween(200),
                            label = "tabBg"
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isFocused) TealAccent else Color.Transparent,
                            animationSpec = tween(200),
                            label = "tabBorder"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) TealAccent else if (isFocused) Color.White else UnselectedItemColor,
                            animationSpec = tween(200),
                            label = "iconColor"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) TealAccent else if (isFocused) Color.White else UnselectedItemColor,
                            animationSpec = tween(200),
                            label = "textColor"
                        )

                        Column(
                            modifier = Modifier
                                .padding(vertical = 6.dp, horizontal = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable {
                                    if (index == 2) {
                                        navController.navigate("playlists")
                                    } else {
                                        selectedTab = index
                                    }
                                }
                                .background(bgColor, RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isFocused) 2.dp else 0.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                tint = iconColor,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tab.title,
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                            // Active Glow Indicator
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(TealAccent)
                                )
                            }
                        }
                    }
                }
            }

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> LiveTvScreen(mainViewModel, navController)
                    1 -> VodScreen(tmdbViewModel, navController)
                    2 -> {
                        LaunchedEffect(Unit) {
                            navController.navigate("playlists")
                            selectedTab = 0
                        }
                    }
                }
            }
        }
    } else {
        // ── Mobile Layout: Premium Bottom Navigation ──
        Scaffold(
            containerColor = DarkNavyBackground,
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {
                    // Gradient fade above the bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        DarkNavyBackground.copy(alpha = 0.8f)
                                    )
                                )
                            )
                    )

                    // Navigation Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF1A1F2E).copy(alpha = 0.97f),
                                        Color(0xFF141920).copy(alpha = 0.97f)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.1f),
                                        Color.White.copy(alpha = 0.03f)
                                    )
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index

                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) TealAccent.copy(alpha = 0.15f) else Color.Transparent,
                                animationSpec = tween(250),
                                label = "mobileBg"
                            )
                            val iconColor by animateColorAsState(
                                targetValue = if (isSelected) TealAccent else UnselectedItemColor,
                                animationSpec = tween(250),
                                label = "mobileIcon"
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) TealAccent else UnselectedItemColor,
                                animationSpec = tween(250),
                                label = "mobileText"
                            )
                            val pillWidth by animateDpAsState(
                                targetValue = if (isSelected) 20.dp else 0.dp,
                                animationSpec = tween(300),
                                label = "pillWidth"
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (index == 2) {
                                            navController.navigate("playlists")
                                        } else {
                                            selectedTab = index
                                        }
                                    }
                                    .background(bgColor, RoundedCornerShape(14.dp))
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.title,
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                // Animated pill indicator
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(pillWidth)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelected) TealAccent else Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> LiveTvScreen(mainViewModel, navController)
                    1 -> VodScreen(tmdbViewModel, navController)
                }
            }
        }
    }
}
