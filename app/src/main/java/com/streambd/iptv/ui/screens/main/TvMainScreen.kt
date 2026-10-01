package com.streambd.iptv.ui.screens.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.screens.events.LiveEventsScreen
import com.streambd.iptv.ui.screens.live.LiveTvScreen
import com.streambd.iptv.ui.screens.stream.StreamScreen
import com.streambd.iptv.ui.screens.vod.VodScreen
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.LiveEventsViewModel
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Drawer palette
// ─────────────────────────────────────────────────────────────
private val DrawerBg = Color(0xFF0D1220)
private val DrawerSelectedBg = BioAccent.copy(alpha = 0.14f)
private val DrawerSectionLabel = Color(0xFF6B7280)
private val DrawerFocusedBg = BioAccent.copy(alpha = 0.08f)
private val DrawerFocusedBorder = BioAccent

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

    // Back:
    //   • Drawer open → close drawer
    //   • Double-back (2s) → exit dialog
    //   • Single-back → toast hint
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
            delay(220) // let the slide-in finish
            try { drawerFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val openDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            TvDrawerContent(
                tvTabs = tvTabs,
                selectedTab = selectedTab,
                isDrawerOpen = drawerState.isOpen,
                firstItemFocusRequester = drawerFocusRequester,
                onItemClick = { index ->
                    when (index) {
                        4 -> {
                            scope.launch { drawerState.close() }
                            navController.navigate("playlists")
                        }
                        5 -> {
                            scope.launch { drawerState.close() }
                            onCheckUpdate()
                        }
                        6 -> {
                            scope.launch { drawerState.close() }
                            onShowMore()
                        }
                        7 -> {
                            scope.launch { drawerState.close() }
                            onShowExitDialog()
                        }
                        else -> {
                            onTabSelected(index)
                            scope.launch { drawerState.close() }
                        }
                    }
                }
            )
        },
        scrimColor = Color.Black.copy(alpha = 0.55f),
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
                    LiveEventsScreen(
                        eventsViewModel, mainViewModel, navController,
                        onMenuClick = openDrawer
                    )
                }
                1 -> LiveTvScreen(mainViewModel, navController, onMenuClick = openDrawer)
                2 -> VodScreen(tmdbViewModel, navController, onMenuClick = openDrawer)
                3 -> StreamScreen(mainViewModel, tmdbViewModel, navController, onMenuClick = openDrawer)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Drawer content
// ─────────────────────────────────────────────────────────────
@Composable
private fun TvDrawerContent(
    tvTabs: List<TabItem>,
    selectedTab: Int,
    isDrawerOpen: Boolean,
    firstItemFocusRequester: FocusRequester,
    onItemClick: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(268.dp)
            .background(DrawerBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp)
        ) {
            // ── Header ──
            DrawerHeader()

            Spacer(Modifier.height(20.dp))
            DrawerDivider()
            Spacer(Modifier.height(12.dp))

            // ── Content ──
            DrawerSectionLabelText("CONTENT")
            DrawerItem(
                icon = Icons.Filled.Event,
                label = tvTabs[0].title,
                selected = selectedTab == 0,
                isDrawerOpen = isDrawerOpen,
                focusRequester = if (selectedTab != 0) firstItemFocusRequester else null,
                onClick = { onItemClick(0) }
            )
            DrawerItem(
                icon = Icons.Filled.LiveTv,
                label = tvTabs[1].title,
                selected = selectedTab == 1,
                isDrawerOpen = isDrawerOpen,
                focusRequester = if (selectedTab != 1) firstItemFocusRequester else null,
                onClick = { onItemClick(1) }
            )
            DrawerItem(
                icon = Icons.Filled.MovieFilter,
                label = tvTabs[2].title,
                selected = selectedTab == 2,
                isDrawerOpen = isDrawerOpen,
                focusRequester = if (selectedTab != 2) firstItemFocusRequester else null,
                onClick = { onItemClick(2) }
            )
            DrawerItem(
                icon = Icons.Filled.NetworkWifi,
                label = tvTabs[3].title,
                selected = selectedTab == 3,
                isDrawerOpen = isDrawerOpen,
                focusRequester = if (selectedTab != 3) firstItemFocusRequester else null,
                onClick = { onItemClick(3) }
            )

            Spacer(Modifier.height(16.dp))

            // ── Library ──
            DrawerSectionLabelText("LIBRARY")
            DrawerItem(
                icon = Icons.Filled.Folder,
                label = "Playlists",
                selected = false,
                isDrawerOpen = isDrawerOpen,
                onClick = { onItemClick(4) }
            )

            Spacer(Modifier.height(16.dp))

            // ── Settings ──
            DrawerSectionLabelText("SETTINGS")
            DrawerItem(
                icon = Icons.Filled.Refresh,
                label = "Check Update",
                selected = false,
                isDrawerOpen = isDrawerOpen,
                onClick = { onItemClick(5) }
            )
            DrawerItem(
                icon = Icons.Outlined.MoreVert,
                label = "More",
                selected = false,
                isDrawerOpen = isDrawerOpen,
                onClick = { onItemClick(6) }
            )

            Spacer(Modifier.height(16.dp))

            // ── Exit ──
            DrawerDivider()
            Spacer(Modifier.height(8.dp))
            DrawerItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                label = "Exit App",
                selected = false,
                isDrawerOpen = isDrawerOpen,
                danger = true,
                onClick = { onItemClick(7) }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "StreamBD IPTV • v4.0",
                color = GrayText.copy(alpha = 0.4f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────
@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(BioAccent.copy(alpha = 0.15f))
                .border(1.dp, BioAccent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LiveTv,
                contentDescription = null,
                tint = BioAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "StreamBD IPTV",
                color = WhiteText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BioAccent.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "v4.0",
                    color = BioAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Section label + divider
// ─────────────────────────────────────────────────────────────
@Composable
private fun DrawerSectionLabelText(text: String) {
    Text(
        text = text,
        color = DrawerSectionLabel,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp),
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.05f)
    )
}

// ─────────────────────────────────────────────────────────────
// Drawer item — with lift-up + focus trap + closed-focus block
// ─────────────────────────────────────────────────────────────
@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    isDrawerOpen: Boolean,
    badge: String? = null,
    danger: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> DrawerFocusedBg
            selected -> DrawerSelectedBg
            else -> Color.Transparent
        },
        animationSpec = tween(180),
        label = "drawerItemBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            danger -> Color(0xFFFF6B6B)
            isFocused || selected -> BioAccent
            else -> WhiteText.copy(alpha = 0.85f)
        },
        animationSpec = tween(180),
        label = "drawerItemContent"
    )
    val accentBarColor by animateColorAsState(
        targetValue = if (isFocused || selected) BioAccent else Color.Transparent,
        animationSpec = tween(180),
        label = "drawerAccent"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) DrawerFocusedBorder else Color.Transparent,
        animationSpec = tween(180),
        label = "drawerBorder"
    )

    val liftY by animateFloatAsState(
        targetValue = if (isFocused) -6f else 0f,
        animationSpec = tween(200),
        label = "drawerLift"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(200),
        label = "drawerScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .focusProperties {
                canFocus = isDrawerOpen
                if (isDrawerOpen) {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
            }
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.hasFocus }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = liftY
            }
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .tvTouchClick(onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentBarColor)
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BioAccent.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = BioAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
    }
}
