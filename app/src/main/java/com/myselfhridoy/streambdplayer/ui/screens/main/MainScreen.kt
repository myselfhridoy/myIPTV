package com.myselfhridoy.streambdplayer.ui.screens.main

import com.myselfhridoy.streambdplayer.ui.screens.live.LiveTvScreen
import com.myselfhridoy.streambdplayer.ui.screens.vod.VodScreen
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.myselfhridoy.streambdplayer.ui.theme.BottomNavBackground
import com.myselfhridoy.streambdplayer.ui.theme.DarkNavyBackground
import com.myselfhridoy.streambdplayer.ui.theme.SelectedItemColor
import com.myselfhridoy.streambdplayer.ui.theme.WhiteText
import com.myselfhridoy.streambdplayer.ui.theme.UnselectedItemColor
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel

@Composable
fun MainScreen(mainViewModel: MainViewModel, tmdbViewModel: TmdbViewModel, navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Live TV", "VOD")

    val configuration = LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
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
        Row(modifier = Modifier.fillMaxSize().background(DarkNavyBackground)) {
            // Premium TV Sidebar (Floating Dock Style)
            NavigationRail(
                containerColor = BottomNavBackground.copy(alpha = 0.95f),
                modifier = Modifier
                    .padding(vertical = 24.dp, horizontal = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            ) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                tabs.forEachIndexed { index, title ->
                    NavigationRailItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(if (index == 0) "📺" else "🎬", fontSize = 24.sp) },
                        label = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = SelectedItemColor,
                            selectedTextColor = SelectedItemColor,
                            unselectedIconColor = UnselectedItemColor,
                            unselectedTextColor = UnselectedItemColor,
                            indicatorColor = SelectedItemColor.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            }
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> LiveTvScreen(mainViewModel, navController)
                    1 -> VodScreen(tmdbViewModel, navController)
                }
            }
        }
    } else {
        Scaffold(
            containerColor = DarkNavyBackground,
            bottomBar = {
                // Premium Mobile Bottom Bar (Rounded Top Corners & Floating Shadow Effect)
                NavigationBar(
                    containerColor = BottomNavBackground.copy(alpha = 0.98f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(if (index == 0) "📺" else "🎬", fontSize = 24.sp) },
                            label = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SelectedItemColor,
                                selectedTextColor = SelectedItemColor,
                                unselectedIconColor = UnselectedItemColor,
                                unselectedTextColor = UnselectedItemColor,
                                indicatorColor = SelectedItemColor.copy(alpha = 0.15f)
                            )
                        )
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
