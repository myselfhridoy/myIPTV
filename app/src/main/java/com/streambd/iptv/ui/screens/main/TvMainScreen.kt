package com.streambd.iptv.ui.screens.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.ui.components.TvFocusableItem
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
    // ── M3 drawer state — stable API ──
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Back: drawer open → close. Else double-back to exit dialog.
    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
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

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            delay(280)
            try { drawerFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(240.dp)
                    .background(DarkNavyBackground)
                    .padding(vertical = 28.dp, horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    tvTabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index && index < 4
                        DrawerNavItem(
                            tab = tab,
                            isSelected = isSelected,
                            focusRequester = if (index == 0) drawerFocusRequester else null,
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
                            }
                        )
                    }
                }
            }
        },
        scrimColor = DarkNavyBackground.copy(alpha = 0.8f),
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

// ─────────────────────────────────────────────────────────────
// Custom drawer item — uses our predictable TvFocusableItem
// ─────────────────────────────────────────────────────────────
@Composable
private fun DrawerNavItem(
    tab: TabItem,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    TvFocusableItem(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        focusRequester = focusRequester,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        restBackground = if (isSelected) BioAccent.copy(alpha = 0.15f) else Color.Transparent,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = tab.title,
            tint = if (isSelected) BioAccent else WhiteText.copy(alpha = 0.75f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = tab.title,
            color = if (isSelected) BioAccent else WhiteText,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
