package com.streambd.iptv.ui.screens.main

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.NetworkWifi
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.streambd.iptv.R
import com.streambd.iptv.ui.components.PremiumButton
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.TvFocusableItem
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.screens.events.LiveEventsScreen
import com.streambd.iptv.ui.screens.live.LiveTvScreen
import com.streambd.iptv.ui.screens.stream.StreamScreen
import com.streambd.iptv.ui.screens.vod.VodScreen
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.DarkNavySurface
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.SurfaceDark
import com.streambd.iptv.ui.theme.UnselectedItemColor
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.launch

data class TabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController,
    onCheckUpdate: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }
    var showCopyrightDialog by remember { mutableStateOf(false) }
    var showTvModeConfigDialog by remember { mutableStateOf(false) }

    val tvTabs = listOf(
        TabItem("Events", Icons.Filled.Event, Icons.Outlined.Event),
        TabItem("Live TV", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
        TabItem("Media", Icons.Filled.MovieFilter, Icons.Outlined.MovieFilter),
        TabItem("Stream", Icons.Filled.NetworkWifi, Icons.Outlined.NetworkWifi),
        TabItem("Playlists", Icons.Filled.Folder, Icons.Outlined.Folder),
        TabItem("Check Update", Icons.Filled.Refresh, Icons.Filled.Refresh),
        TabItem("More", Icons.Filled.MoreVert, Icons.Outlined.MoreVert),
        TabItem("Exit App", Icons.AutoMirrored.Filled.ExitToApp, Icons.AutoMirrored.Outlined.ExitToApp)
    )

    val mobileTabs = listOf(
        TabItem("Events", Icons.Filled.Event, Icons.Outlined.Event),
        TabItem("Live TV", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
        TabItem("Media", Icons.Filled.MovieFilter, Icons.Outlined.MovieFilter),
        TabItem("Stream", Icons.Filled.NetworkWifi, Icons.Outlined.NetworkWifi)
    )

    val configuration = LocalConfiguration.current
    val isTv = remember(configuration) {
        val uiMode = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val isTelevision = uiMode == Configuration.UI_MODE_TYPE_TELEVISION
        val pm = context.packageManager
        val isLeanback = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        val noTouch = !pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
        isTelevision || isLeanback || noTouch
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val openTelegram = {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/streamBD"))
            )
        } catch (_: Exception) {}
    }

    val shareApp = {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "StreamBD IPTV")
            putExtra(
                Intent.EXTRA_TEXT,
                "StreamBD IPTV is the best app for streaming!\n" +
                    "Download it here: https://github.com/myselfhridoy/myIPTV/releases/latest"
            )
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share App via"))
    }

    val showNotice = {
        android.widget.Toast.makeText(
            context, "No new notices at this time.", android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    // Restart helper — never kill the process.
    val restartApp: () -> Unit = {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        )
        if (intent != null) context.startActivity(intent)
        (context as? android.app.Activity)?.finishAffinity()
    }

    // ── Back handler:
    //   1. drawer open    → close drawer
    //   2. not home tab   → go to home tab
    //   3. home tab       → exit dialog
    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            selectedTab != 0 -> selectedTab = 0
            else -> showExitDialog = true
        }
    }

    // ── Exit dialog ──
    if (showExitDialog) {
        PremiumDialogScaffold(
            title = "Exit App",
            subtitle = "Are you sure you want to exit?",
            onDismiss = { showExitDialog = false },
            actions = {
                PremiumButton("Cancel", { showExitDialog = false })
                PremiumButton("Exit", {
                    showExitDialog = false
                    (context as? android.app.Activity)?.finishAffinity()
                }, primary = true)
            }
        ) {}
    }

    // ── Copyright dialog ──
    if (showCopyrightDialog) {
        PremiumDialogScaffold(
            title = "Copyright & Disclaimer",
            subtitle = "Legal notice",
            onDismiss = { showCopyrightDialog = false },
            contentMaxHeight = 360.dp
        ) {
            Text(
                text = "We do not host, manage, or upload any of the channels " +
                    "or video content provided in this application. All streams and " +
                    "multimedia content are aggregated from publicly available free " +
                    "sources on the internet.\n\n" +
                    "This app acts purely as a streaming platform. If you believe any " +
                    "content infringes upon your copyright, please contact the original " +
                    "content hosts directly.\n\n" +
                    "© 2026 StreamBD. All rights reserved.",
                color = GrayText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }

    // ── More dialog (mobile only — TV uses drawer) ──
    if (showMoreDialog && !isTv) {
        val isTvModeEnabled by mainViewModel.isTvModeEnabled.collectAsState()
        Dialog(onDismissRequest = { showMoreDialog = false }) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkNavySurface
            ) {
                Column(modifier = Modifier.padding(16.dp).width(300.dp)) {
                    Text(
                        "More Options",
                        color = WhiteText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val moreItems = listOf(
                        Pair("TV Mode: ${if (isTvModeEnabled) "ON" else "OFF"}", Icons.Outlined.LiveTv) to {
                            showMoreDialog = false
                            if (!isTvModeEnabled) {
                                showTvModeConfigDialog = true
                            } else {
                                mainViewModel.toggleTvMode(false)
                                restartApp()
                            }
                        },
                        Pair("Notice", Icons.Outlined.Notifications) to {
                            showMoreDialog = false; showNotice()
                        },
                        Pair("Join Us", Icons.Outlined.Group) to {
                            showMoreDialog = false; openTelegram()
                        },
                        Pair("Copyright", Icons.Outlined.Copyright) to {
                            showMoreDialog = false; showCopyrightDialog = true
                        },
                        Pair("Share Our App", Icons.Outlined.Share) to {
                            showMoreDialog = false; shareApp()
                        }
                    )
                    moreItems.forEach { (item, action) ->
                        TvFocusableItem(
                            onClick = action,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Icon(item.second, contentDescription = null, tint = GrayText, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(item.first, color = WhiteText, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (isTv) {
        TvMainScreen(
            mainViewModel = mainViewModel,
            tmdbViewModel = tmdbViewModel,
            navController = navController,
            context = context,
            tvTabs = tvTabs,
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onCheckUpdate = onCheckUpdate,
            onShowMore = { showMoreDialog = true },
            onShowExitDialog = { showExitDialog = true },
            onRestartApp = restartApp
        )
    } else {
        // ── Mobile layout: Navigation Drawer + Bottom nav ──
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(280.dp),
                    drawerContainerColor = Color(0xFF0F131A),
                    drawerContentColor = WhiteText
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var logoTapCount by remember { mutableStateOf(0) }
                        var logoLastTapTime by remember { mutableStateOf(0L) }

                        Image(
                            painter = painterResource(id = R.drawable.tv_app_banner),
                            contentDescription = "App Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(80.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val now = System.currentTimeMillis()
                                    if (now - logoLastTapTime > 500) logoTapCount = 1
                                    else logoTapCount++
                                    if (logoTapCount >= 7) {
                                        logoTapCount = 0
                                        navController.navigate("admin_converter")
                                        scope.launch { drawerState.close() }
                                    }
                                    logoLastTapTime = now
                                }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    HorizontalDivider(color = Color(0xFF222836))

                    val isTvModeEnabled by mainViewModel.isTvModeEnabled.collectAsState()
                    val drawerItems = listOf(
                        Pair("TV Mode: ${if (isTvModeEnabled) "ON" else "OFF"}", Icons.Outlined.LiveTv) to {
                            scope.launch { drawerState.close() }
                            if (!isTvModeEnabled) {
                                showTvModeConfigDialog = true
                            } else {
                                mainViewModel.toggleTvMode(false)
                                restartApp()
                            }
                        },
                        Pair("Playlists", Icons.Outlined.Folder) to {
                            scope.launch { drawerState.close() }
                            navController.navigate("playlists")
                        },
                        Pair("Notice", Icons.Outlined.Notifications) to {
                            scope.launch { drawerState.close() }; showNotice()
                        },
                        Pair("Join Us", Icons.Outlined.Group) to {
                            scope.launch { drawerState.close() }; openTelegram()
                        },
                        Pair("Copyright", Icons.Outlined.Copyright) to {
                            scope.launch { drawerState.close() }; showCopyrightDialog = true
                        },
                        Pair("Share Our App", Icons.Outlined.Share) to {
                            scope.launch { drawerState.close() }; shareApp()
                        },
                        Pair("Check Update", Icons.Filled.Refresh) to {
                            scope.launch { drawerState.close() }; onCheckUpdate()
                        },
                        Pair("Exit App", Icons.AutoMirrored.Outlined.ExitToApp) to {
                            scope.launch { drawerState.close() }; showExitDialog = true
                        }
                    )

                    drawerItems.forEach { (item, action) ->
                        NavigationDrawerItem(
                            icon = { Icon(item.second, contentDescription = null, tint = Color(0xFFDCDCDC)) },
                            label = { Text(item.first, color = WhiteText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                            selected = false,
                            onClick = { action() },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = WhiteText
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = DarkNavyBackground,
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent)
                    ) {
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
                            mobileTabs.forEachIndexed { index, tab ->
                                val isSelected = selectedTab == index
                                val bgColor by animateColorAsState(
                                    targetValue = if (isSelected) BioAccent.copy(alpha = 0.15f) else Color.Transparent,
                                    animationSpec = tween(250),
                                    label = "mobileBg"
                                )
                                val iconColor by animateColorAsState(
                                    targetValue = if (isSelected) BioAccent else UnselectedItemColor,
                                    animationSpec = tween(250),
                                    label = "mobileIcon"
                                )
                                val textColor by animateColorAsState(
                                    targetValue = if (isSelected) BioAccent else UnselectedItemColor,
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
                                        .tvTouchClick { selectedTab = index }
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
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(pillWidth)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isSelected) BioAccent else Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (selectedTab) {
                        0 -> {
                            val eventsViewModel: LiveEventsViewModel =
                                androidx.lifecycle.viewmodel.compose.viewModel()
                            LiveEventsScreen(
                                eventsViewModel, mainViewModel, navController,
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        }
                        1 -> LiveTvScreen(
                            mainViewModel, navController,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                        2 -> VodScreen(
                            tmdbViewModel, navController,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                        3 -> StreamScreen(
                            mainViewModel, tmdbViewModel, navController,
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }
                }
            }
        }
    }

    // ── TV Mode configuration dialog (both layouts) ──
    if (showTvModeConfigDialog) {
        TvModeConfigDialog(
            mainViewModel = mainViewModel,
            context = context,
            onDismiss = { showTvModeConfigDialog = false },
            onConfirm = {
                showTvModeConfigDialog = false
                restartApp()
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// TV Mode config dialog (extracted for reuse)
// ─────────────────────────────────────────────────────────────
@Composable
private fun TvModeConfigDialog(
    mainViewModel: MainViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val categories by mainViewModel.liveCategories.collectAsState()
    val playlistViewModel: com.streambd.iptv.ui.viewmodels.PlaylistViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return com.streambd.iptv.ui.viewmodels.PlaylistViewModel(context) as T
                }
            }
        )
    val userPlaylists by playlistViewModel.playlists.collectAsState()

    var selectedCategories by remember { mutableStateOf(mainViewModel.tvModePlaylists.value) }
    var includeEvents by remember {
        mutableStateOf(
            context.getSharedPreferences("tv_mode_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("tv_mode_include_events", true)
        )
    }

    PremiumDialogScaffold(
        title = "TV Mode Configuration",
        subtitle = "Select playlists to include in TV Mode",
        onDismiss = onDismiss,
        contentMaxHeight = 380.dp,
        actions = {
            PremiumButton("Cancel", onDismiss)
            PremiumButton("Save & Restart", {
                mainViewModel.saveTvModeConfig(true, selectedCategories, includeEvents)
                onConfirm()
            }, primary = true)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Events toggle
            TvFocusableItem(
                onClick = { includeEvents = !includeEvents },
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = includeEvents,
                    onCheckedChange = { includeEvents = it },
                    colors = CheckboxDefaults.colors(checkedColor = BioAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Live Events (Default)", color = WhiteText, fontSize = 14.sp)
            }

            categories.forEach { category ->
                val isChecked = selectedCategories.contains(category.name)
                TvFocusableItem(
                    onClick = {
                        selectedCategories = if (isChecked) selectedCategories - category.name
                        else selectedCategories + category.name
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = {
                            selectedCategories = if (it) selectedCategories + category.name
                            else selectedCategories - category.name
                        },
                        colors = CheckboxDefaults.colors(checkedColor = BioAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(category.name, color = WhiteText, fontSize = 14.sp)
                }
            }

            if (userPlaylists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "User Playlists",
                    color = GrayText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                userPlaylists.forEach { playlist ->
                    val isChecked = selectedCategories.contains(playlist.title)
                    TvFocusableItem(
                        onClick = {
                            selectedCategories = if (isChecked) selectedCategories - playlist.title
                            else selectedCategories + playlist.title
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = {
                                selectedCategories = if (it) selectedCategories + playlist.title
                                else selectedCategories - playlist.title
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BioAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(playlist.title, color = WhiteText, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
