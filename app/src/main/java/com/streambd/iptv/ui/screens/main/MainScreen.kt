package com.streambd.iptv.ui.screens.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.adaptive.NavigationStyle
import com.streambd.iptv.ui.navigation.AppMenuItem
import com.streambd.iptv.ui.navigation.MainTab
import com.streambd.iptv.ui.navigation.Routes
import com.streambd.iptv.ui.screens.events.LiveEventsScreen
import com.streambd.iptv.ui.screens.live.LiveTvScreen
import com.streambd.iptv.ui.screens.stream.StreamScreen
import com.streambd.iptv.ui.screens.vod.VodScreen
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import com.streambd.iptv.utils.AnalyticsLogger
import com.streambd.iptv.utils.PlayerPreferences
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App home. Owns the state (selected tab, dialogs) and the menu actions,
 * then hands the layout to the right shell for this device:
 *
 *   NavigationStyle.BottomBar → MobileShell (phone)
 *   NavigationStyle.Rail      → MobileShell (tablet, side rail)
 *   NavigationStyle.Drawer    → TvShell     (TV / D-pad)
 *
 * Public signature is unchanged, so AppNavigation needs no edit.
 */
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController,
    onCheckUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val device = LocalDeviceProfile.current
    val actions = rememberAppActions()
    val scope = rememberCoroutineScope()

    // Saved as an Int (same as before) so restored state stays compatible.
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    val selectedTab = MainTab.fromIndex(selectedIndex)

    var showExitDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }
    var showCopyrightDialog by remember { mutableStateOf(false) }
    var showTvModeConfigDialog by remember { mutableStateOf(false) }

    val isTvModeEnabled by mainViewModel.isTvModeEnabled.collectAsState()
    var portraitPlayer by remember { mutableStateOf(PlayerPreferences.isPortraitPlayerEnabled(context)) }

    // Safe TV mode toggle off — grace window for the async write.
    val turnOffTvMode: () -> Unit = {
        scope.launch {
            mainViewModel.toggleTvMode(false)
            delay(150)
            actions.restartApp()
        }
    }

    val logFeature: (String) -> Unit = { key ->
        AnalyticsLogger.logFeatureUsed(context = context, feature = key)
    }

    val labelOf: (AppMenuItem) -> String = { item ->
        when (item) {
            AppMenuItem.TvMode -> if (isTvModeEnabled) "TV Mode: ON" else "TV Mode: OFF"
            AppMenuItem.PortraitPlayer ->
                if (portraitPlayer) "Portrait Player: ON" else "Portrait Player: OFF"
            else -> item.label
        }
    }

    val onMenuItem: (AppMenuItem) -> Unit = { item ->
        when (item) {
            AppMenuItem.TvMode ->
                if (!isTvModeEnabled) showTvModeConfigDialog = true else turnOffTvMode()
            AppMenuItem.PortraitPlayer -> {
                portraitPlayer = !portraitPlayer
                PlayerPreferences.setPortraitPlayerEnabled(context, portraitPlayer)
                Toast.makeText(
                    context,
                    if (portraitPlayer) "Portrait player on: channels open with a channel list below the video"
                    else "Portrait player off: channels open in fullscreen landscape",
                    Toast.LENGTH_SHORT
                ).show()
            }
            AppMenuItem.Playlists -> navController.navigate(Routes.PLAYLISTS)
            AppMenuItem.Notice -> actions.showNotice()
            AppMenuItem.JoinUs -> actions.openTelegram()
            AppMenuItem.Copyright -> showCopyrightDialog = true
            AppMenuItem.Share -> actions.shareApp()
            AppMenuItem.CheckUpdate -> onCheckUpdate()
            AppMenuItem.More -> showMoreDialog = true
            AppMenuItem.Exit -> showExitDialog = true
        }
    }

    // ── Dialogs ──────────────────────────────────────────────
    if (showExitDialog) {
        ExitDialog(
            onDismiss = { showExitDialog = false },
            onConfirm = {
                showExitDialog = false
                actions.exitApp()
            }
        )
    }

    if (showCopyrightDialog) {
        CopyrightDialog(onDismiss = { showCopyrightDialog = false })
    }

    if (showMoreDialog) {
        MoreDialog(
            items = AppMenuItem.MoreDialog,
            labelOf = labelOf,
            onItemClick = { item ->
                showMoreDialog = false
                onMenuItem(item)
            },
            onDismiss = { showMoreDialog = false }
        )
    }

    if (showTvModeConfigDialog) {
        TvModeConfigDialog(
            mainViewModel = mainViewModel,
            onDismiss = { showTvModeConfigDialog = false },
            onConfirm = {
                showTvModeConfigDialog = false
                scope.launch {
                    delay(150)
                    actions.restartApp()
                }
            }
        )
    }

    // ── Layout for this device ───────────────────────────────
    val tabContent: @Composable (openMenu: () -> Unit) -> Unit = { openMenu ->
        MainTabContent(
            tab = selectedTab,
            mainViewModel = mainViewModel,
            tmdbViewModel = tmdbViewModel,
            navController = navController,
            onMenuClick = openMenu
        )
    }

    when (device.navigationStyle) {
        NavigationStyle.Drawer -> TvShell(
            selectedTab = selectedTab,
            // TV: one analytics event per drawer tap (as before).
            onTabSelected = { tab ->
                logFeature(tab.key)
                selectedIndex = tab.ordinal
            },
            onMenuItem = { item ->
                logFeature(item.key)
                onMenuItem(item)
            },
            onExitRequested = { showExitDialog = true },
            content = tabContent
        )

        NavigationStyle.BottomBar, NavigationStyle.Rail -> MobileShell(
            selectedTab = selectedTab,
            // Phone/tablet: log only when the tab actually changes (as before).
            onTabSelected = { tab ->
                if (tab != selectedTab) logFeature(tab.key)
                selectedIndex = tab.ordinal
            },
            onBackToStart = { selectedIndex = MainTab.Events.ordinal },
            drawerItems = AppMenuItem.PhoneDrawer,
            labelOf = labelOf,
            onMenuItem = onMenuItem,
            onOpenAdmin = { navController.navigate(Routes.ADMIN_CONVERTER) },
            onExitRequested = { showExitDialog = true },
            useRail = device.navigationStyle == NavigationStyle.Rail,
            content = tabContent
        )
    }
}

/** The four tab screens — one definition for every layout. */
@Composable
private fun MainTabContent(
    tab: MainTab,
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController,
    onMenuClick: () -> Unit
) {
    when (tab) {
        MainTab.Events -> {
            val eventsViewModel: LiveEventsViewModel = viewModel()
            LiveEventsScreen(
                eventsViewModel, mainViewModel, navController,
                onMenuClick = onMenuClick
            )
        }
        MainTab.LiveTv -> LiveTvScreen(
            mainViewModel, navController,
            onMenuClick = onMenuClick
        )
        MainTab.Media -> VodScreen(
            tmdbViewModel, navController,
            onMenuClick = onMenuClick
        )
        MainTab.Stream -> StreamScreen(
            mainViewModel, tmdbViewModel, navController,
            onMenuClick = onMenuClick
        )
    }
}
