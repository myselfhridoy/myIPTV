package com.streambd.iptv.ui.screens.main

import android.app.Activity
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.*
import com.streambd.iptv.ui.screens.live.LiveTvScreen
import com.streambd.iptv.ui.screens.stream.StreamScreen
import com.streambd.iptv.ui.screens.vod.VodScreen
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.TealAccent
import kotlinx.coroutines.launch
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMainScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController,
    context: Context,
    tvTabs: List<TabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    onShowMore: () -> Unit,
    onShowExitDialog: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val isDrawerExpanded = drawerState.currentValue == DrawerValue.Open

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isDrawerExpanded) 240.dp else 0.dp)
                    .background(DarkNavyBackground)
                    .padding(vertical = 28.dp, horizontal = if (isDrawerExpanded) 12.dp else 0.dp)
            ) {
                if (isDrawerExpanded) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center
                    ) {
                        tvTabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index && index < 3
                            
                            NavigationDrawerItem(
                                selected = isSelected,
                                onClick = {
                                    when (index) {
                                        3 -> navController.navigate("playlists")
                                        4 -> onCheckUpdate()
                                        5 -> onShowMore()
                                        6 -> onShowExitDialog()
                                        else -> {
                                            onTabSelected(index)
                                            scope.launch { drawerState.setValue(DrawerValue.Closed) }
                                        }
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                },
                                modifier = Modifier.padding(vertical = 4.dp),
                                colors = NavigationDrawerItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Gray,
                                    selectedContainerColor = TealAccent.copy(alpha = 0.15f),
                                    selectedContentColor = TealAccent,
                                    focusedContainerColor = TealAccent,
                                    focusedContentColor = Color.Black,
                                    focusedSelectedContainerColor = TealAccent,
                                    focusedSelectedContentColor = Color.Black
                                )
                            ) {
                                Text(
                                    text = tab.title,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        scrimBrush = Brush.horizontalGradient(
            colors = listOf(DarkNavyBackground.copy(alpha = 0.8f), Color.Transparent)
        ),
        modifier = Modifier.fillMaxSize().background(DarkNavyBackground)
    ) {
        // Main Screen Area wrapped in overscan margins
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 58.dp, vertical = 28.dp)
        ) {
            when (selectedTab) {
                0 -> LiveTvScreen(mainViewModel, navController, onMenuClick = { scope.launch { drawerState.setValue(DrawerValue.Open) } })
                1 -> VodScreen(tmdbViewModel, navController, onMenuClick = { scope.launch { drawerState.setValue(DrawerValue.Open) } })
                2 -> StreamScreen(mainViewModel, tmdbViewModel, navController, onMenuClick = { scope.launch { drawerState.setValue(DrawerValue.Open) } })
            }
        }
    }
}
