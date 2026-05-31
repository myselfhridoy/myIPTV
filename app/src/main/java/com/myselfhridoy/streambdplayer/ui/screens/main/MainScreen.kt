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
            NavigationRail(
                containerColor = BottomNavBackground,
                modifier = Modifier
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationRailItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(if (index == 0) "📺" else "🎬") },
                        label = { Text(title) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = SelectedItemColor,
                            selectedTextColor = SelectedItemColor,
                            unselectedIconColor = UnselectedItemColor,
                            unselectedTextColor = UnselectedItemColor,
                            indicatorColor = BottomNavBackground
                        )
                    )
                }
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
                NavigationBar(
                    containerColor = BottomNavBackground
                ) {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(if (index == 0) "📺" else "🎬") },
                            label = { Text(title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SelectedItemColor,
                                selectedTextColor = SelectedItemColor,
                                unselectedIconColor = UnselectedItemColor,
                                unselectedTextColor = UnselectedItemColor,
                                indicatorColor = BottomNavBackground
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
