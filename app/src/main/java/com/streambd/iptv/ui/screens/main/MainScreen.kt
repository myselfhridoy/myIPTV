package com.streambd.iptv.ui.screens.main

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.NetworkWifi
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavController
import com.streambd.iptv.R
import com.streambd.iptv.ui.screens.live.LiveTvScreen
import com.streambd.iptv.ui.screens.stream.StreamScreen
import com.streambd.iptv.ui.screens.vod.VodScreen
import com.streambd.iptv.ui.theme.*
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.launch

data class TabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel, 
    tmdbViewModel: TmdbViewModel, 
    navController: NavController,
    onCheckUpdate: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(1) } // Default: Media tab
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }

    val tvTabs = listOf(
        TabItem("Live TV", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
        TabItem("Media", Icons.Filled.MovieFilter, Icons.Outlined.MovieFilter),
        TabItem("Stream", Icons.Filled.NetworkWifi, Icons.Outlined.NetworkWifi),
        TabItem("Playlists", Icons.Filled.Folder, Icons.Outlined.Folder),
        TabItem("Check Update", Icons.Filled.Refresh, Icons.Filled.Refresh),
        TabItem("More", Icons.Filled.MoreVert, Icons.Outlined.MoreVert),
        TabItem("Exit App", Icons.AutoMirrored.Filled.ExitToApp, Icons.AutoMirrored.Outlined.ExitToApp)
    )

    val mobileTabs = listOf(
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
        
        // AOSP cheap TV boxes often have weird dpi scaling causing small screenWidthDp,
        // and obscure names. The most robust fallback is treating ANY landscape orientation 
        // as a TV/Tablet layout (Navigation Rail). This also adheres to Material 3 adaptive UI for mobile.
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        // Check for common AOSP TV box names
        val deviceName = (android.os.Build.MODEL + " " + android.os.Build.PRODUCT).lowercase()
        val isKnownBox = deviceName.contains("box") || deviceName.contains("tv") || deviceName.contains("stick")
        
        isTelevision || isLeanback || noTouch || isLandscape || isKnownBox
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val openTelegram = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/streamBD"))
        context.startActivity(intent)
    }

    val shareApp = {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "StreamBD IPTV")
            putExtra(Intent.EXTRA_TEXT, "StreamBD IPTV is the best app for streaming!\nDownload it here: https://github.com/myselfhridoy/myIPTV/releases/latest")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share App via"))
    }
    
    val showNotice = {
        android.widget.Toast.makeText(context, "No new notices at this time.", android.widget.Toast.LENGTH_SHORT).show()
    }

    val showCopyright = {
        android.widget.Toast.makeText(context, "© 2026 StreamBD. All rights reserved.", android.widget.Toast.LENGTH_SHORT).show()
    }

    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit App", color = Color.White) },
            text = { Text("Are you sure you want to exit?", color = Color.LightGray) },
            containerColor = DarkNavyBackground,
            confirmButton = {
                TextButton(onClick = { 
                    showExitDialog = false
                    val activity = context as? android.app.Activity
                    activity?.finish()
                }) {
                    Text("Yes", color = TealAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("No", color = Color.Gray)
                }
            }
        )
    }

    if (showMoreDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showMoreDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkNavySurface
            ) {
                Column(modifier = Modifier.padding(16.dp).width(300.dp)) {
                    Text("More Options", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val moreItems = listOf(
                        Pair("Notice", Icons.Outlined.Notifications) to {
                            showMoreDialog = false
                            showNotice()
                        },
                        Pair("Join Us", Icons.Outlined.Group) to {
                            showMoreDialog = false
                            openTelegram()
                        },
                        Pair("Copyright", Icons.Outlined.Copyright) to {
                            showMoreDialog = false
                            showCopyright()
                        },
                        Pair("Share Our App", Icons.Outlined.Share) to {
                            showMoreDialog = false
                            shareApp()
                        }
                    )
                    
                    moreItems.forEach { (item, action) ->
                        androidx.tv.material3.ListItem(
                            selected = false,
                            onClick = action,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).tvTouchClick(action),
                            headlineContent = { Text(item.first, color = Color.White) },
                            leadingContent = { Icon(item.second, contentDescription = null, tint = Color.Gray) },
                            colors = androidx.tv.material3.ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = TealAccent.copy(alpha = 0.1f)
                            ),
                            shape = androidx.tv.material3.ListItemDefaults.shape(shape = RoundedCornerShape(8.dp))
                        )
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
            onShowExitDialog = { showExitDialog = true }
        )
    } else {
        // ── Mobile Layout: Modal Navigation Drawer & Bottom Nav ──

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(280.dp),
                    drawerContainerColor = Color(0xFF0F131A),
                    drawerContentColor = Color.White
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
                                    if (now - logoLastTapTime > 500) logoTapCount = 1 else logoTapCount++
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
                    
                    val drawerItems = listOf(
                        Pair("Playlists", Icons.Outlined.Folder) to {
                            scope.launch { drawerState.close() }
                            navController.navigate("playlists")
                        },
                        Pair("Notice", Icons.Outlined.Notifications) to {
                            scope.launch { drawerState.close() }
                            showNotice()
                        },
                        Pair("Join Us", Icons.Outlined.Group) to {
                            scope.launch { drawerState.close() }
                            openTelegram()
                        },
                        Pair("Copyright", Icons.Outlined.Copyright) to {
                            scope.launch { drawerState.close() }
                            showCopyright()
                        },
                        Pair("Share Our App", Icons.Outlined.Share) to {
                            scope.launch { drawerState.close() }
                            shareApp()
                        },
                        Pair("Check Update", Icons.Filled.Refresh) to {
                            scope.launch { drawerState.close() }
                            onCheckUpdate()
                        },
                        Pair("Exit App", Icons.AutoMirrored.Outlined.ExitToApp) to {
                            scope.launch { drawerState.close() }
                            showExitDialog = true
                        }
                    )

                    drawerItems.forEach { (item, action) ->
                        NavigationDrawerItem(
                            icon = { Icon(item.second, contentDescription = null, tint = Color(0xFFDCDCDC)) },
                            label = { Text(item.first, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                            selected = false,
                            onClick = { action() },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = Color.White
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
                                mobileTabs.forEachIndexed { index, tab ->
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
                                                selectedTab = index
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
                        0 -> LiveTvScreen(mainViewModel, navController, onMenuClick = { scope.launch { drawerState.open() } })
                        1 -> VodScreen(tmdbViewModel, navController, onMenuClick = { scope.launch { drawerState.open() } })
                        2 -> StreamScreen(mainViewModel, tmdbViewModel, navController, onMenuClick = { scope.launch { drawerState.open() } })
                    }
                }
            }
        }
    }
}

@Composable
fun TvPopupItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TealAccent.copy(alpha = 0.2f) else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) TealAccent else Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = if (isFocused) TealAccent else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
