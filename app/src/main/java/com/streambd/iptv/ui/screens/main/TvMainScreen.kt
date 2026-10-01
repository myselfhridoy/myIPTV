package com.streambd.iptv.ui.screens.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import com.streambd.iptv.ui.screens.events.LiveEventsScreen
import com.streambd.iptv.ui.screens.live.LiveTvScreen
import com.streambd.iptv.ui.screens.stream.StreamScreen
import com.streambd.iptv.ui.screens.vod.VodScreen
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onShowExitDialog: () -> Unit,
    onRestartApp: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Back: drawer open → close. Else double-back to exit dialog.
    BackHandler(enabled = true) {
        if (drawerState.currentValue == DrawerValue.Open) {
            scope.launch { drawerState.close() }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000) {
                lastBackPressTime = 0L
                onShowExitDialog()
            } else {
                lastBackPressTime = now
                android.widget.Toast.makeText(
                    context, "Press BACK again to exit", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val drawerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            delay(280)
            try { drawerFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val isDrawerExpanded = drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isDrawerExpanded) 240.dp else 1.dp)
                    .background(if (isDrawerExpanded) DarkNavyBackground else Color.Transparent)
                    .padding(
                        vertical = 28.dp,
                        horizontal = if (isDrawerExpanded) 12.dp else 0.dp
                    )
            ) {
                if (isDrawerExpanded) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center
                    ) {
                        tvTabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTab == index && index < 4
                            NavigationDrawerItem(
                                selected = isSelected,
                                onClick = {
                                    when (index) {
                                        4 -> navController.navigate("playlists")
                                        5 -> onCheckUpdate()
                                        6 -> onShowMore()
                                        7 -> onShowExitDialog()
                                        else -> {
                                            onTabSelected(index)
                                            scope.launch { drawerState.close() }
                                        }
                                    }
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                },
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .then(
                                        if (index == 0) Modifier.focusRequester(drawerFocusRequester)
                                        else Modifier
                                    ),
                                colors = NavigationDrawerItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = WhiteText.copy(alpha = 0.7f),
                                    selectedContainerColor = BioAccent.copy(alpha = 0.15f),
                                    selectedContentColor = BioAccent,
                                    focusedContainerColor = BioAccent,
                                    focusedContentColor = Color.Black,
                                    focusedSelectedContainerColor = BioAccent,
                                    focusedSelectedContentColor = Color.Black
                                )
                            ) {
                                Text(text = tab.title, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        },
        scrimBrush = Brush.horizontalGradient(
            colors = listOf(DarkNavyBackground.copy(alpha = 0.8f), Color.Transparent)
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    val eventsViewModel: LiveEventsViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel()
                    LiveEventsScreen(eventsViewModel, mainViewModel, navController, onMenuClick = null)
                }
                1 -> LiveTvScreen(mainViewModel, navController, onMenuClick = null)
                2 -> VodScreen(tmdbViewModel, navController, onMenuClick = null)
                3 -> StreamScreen(mainViewModel, tmdbViewModel, navController, onMenuClick = null)
            }
        }
    }
}
