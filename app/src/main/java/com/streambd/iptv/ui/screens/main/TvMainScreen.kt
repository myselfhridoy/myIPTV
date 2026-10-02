@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.streambd.iptv.ui.screens.main

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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.BuildConfig
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
// Drawer entry indices — no more magic numbers scattered around.
// ─────────────────────────────────────────────────────────────
private object DrawerIndex {
    const val EVENTS = 0
    const val LIVE_TV = 1
    const val MEDIA = 2
    const val STREAM = 3
    const val PLAYLISTS = 4
    const val CHECK_UPDATE = 5
    const val MORE = 6
    const val EXIT = 7
}

/** Number of "primary" tabs that map 1:1 to the top-level screens. */
private const val PRIMARY_TAB_COUNT = 4

// ─────────────────────────────────────────────────────────────
// Drawer palette
// ─────────────────────────────────────────────────────────────
private val DrawerBg = Color(0xFF0D1220)
private val DrawerSelectedBg = BioAccent.copy(alpha = 0.14f)
private val DrawerSectionLabel = Color(0xFF6B7280)
private val DrawerFocusedBg = BioAccent.copy(alpha = 0.08f)
private val DrawerFocusedBorder = BioAccent

// ─────────────────────────────────────────────────────────────
// A single drawer entry (icon + label + stable index).
// ─────────────────────────────────────────────────────────────
private data class DrawerEntry(
    val index: Int,
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false
)

@Composable
fun TvMainScreen(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    navController: NavController,
    tvTabs: List<TabItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCheckUpdate: () -> Unit,
    onShowMore: () -> Unit,
    onShowExitDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var lastBackTime by remember { mutableLongStateOf(0L) }

    // ── Back behavior (drawer-aware) ──
    //   Drawer closed + Back      → open drawer
    //   Drawer open   + Back      → close drawer
    //                               (toggle — matches Smarttube & most TV apps)
    //   Exit is done via the "Exit App" drawer item.
    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackTime in 1..2000) {
                lastBackTime = 0L
                onShowExitDialog()
            } else {
                lastBackTime = now
                scope.launch { drawerState.open() }
            }
        }
    }

    val drawerFocusRequester = remember { FocusRequester() }

    // Which drawer item should receive initial focus when the drawer opens.
    // → the currently-selected primary tab (or the first primary tab if
    //   selection is outside 0..3).
    val focusAnchorIndex = if (selectedTab in 0 until PRIMARY_TAB_COUNT) selectedTab
    else DrawerIndex.EVENTS

    // ── Focus the anchor item once the drawer animation SETTLES ──
    // Keyed on currentValue so it fires when Open is reached, not while
    // animating. No magic delay.
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            // one extra frame after settle, so layout bounds are final
            delay(60)
            runCatching { drawerFocusRequester.requestFocus() }
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
                focusAnchorIndex = focusAnchorIndex,
                anchorFocusRequester = drawerFocusRequester,
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onItemClick = { index ->
                    when (index) {
                        DrawerIndex.PLAYLISTS -> {
                            scope.launch { drawerState.close() }
                            navController.navigate("playlists")
                        }
                        DrawerIndex.CHECK_UPDATE -> {
                            scope.launch { drawerState.close() }
                            onCheckUpdate()
                        }
                        DrawerIndex.MORE -> {
                            scope.launch { drawerState.close() }
                            onShowMore()
                        }
                        DrawerIndex.EXIT -> {
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
        // Main content. The source screen's own focus ring might still be
        // around, but it will be invisible: the drawer's scrim covers it and
        // focus is moved into the drawer as soon as `currentValue == Open`.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            when (selectedTab) {
                DrawerIndex.EVENTS -> {
                    val eventsViewModel: LiveEventsViewModel =
                        androidx.lifecycle.viewmodel.compose.viewModel()
                    LiveEventsScreen(
                        eventsViewModel, mainViewModel, navController,
                        onMenuClick = openDrawer
                    )
                }
                DrawerIndex.LIVE_TV -> LiveTvScreen(
                    mainViewModel, navController, onMenuClick = openDrawer
                )
                DrawerIndex.MEDIA -> VodScreen(
                    tmdbViewModel, navController, onMenuClick = openDrawer
                )
                DrawerIndex.STREAM -> StreamScreen(
                    mainViewModel, tmdbViewModel, navController, onMenuClick = openDrawer
                )
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
    focusAnchorIndex: Int,
    anchorFocusRequester: FocusRequester,
    onCloseDrawer: () -> Unit,
    onItemClick: (Int) -> Unit
) {
    // Compose the full item list once so the body is a single loop.
    val entries: List<DrawerEntry> = remember(tvTabs) {
        val primary = tvTabs.take(PRIMARY_TAB_COUNT).mapIndexed { i, tab ->
            DrawerEntry(i, tab.selectedIcon, tab.title)
        }
        primary + listOf(
            DrawerEntry(DrawerIndex.PLAYLISTS, Icons.Filled.Folder, "Playlists"),
            DrawerEntry(DrawerIndex.CHECK_UPDATE, Icons.Filled.Refresh, "Check Update"),
            DrawerEntry(DrawerIndex.MORE, Icons.Outlined.MoreVert, "More"),
            DrawerEntry(DrawerIndex.EXIT, Icons.AutoMirrored.Filled.ExitToApp, "Exit App", danger = true)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(268.dp)
            .background(DrawerBg)
            // Right from anywhere in the drawer closes it.
            .onKeyEvent { ev ->
                if (isDrawerOpen &&
                    ev.type == KeyEventType.KeyUp &&
                    ev.key == Key.DirectionRight
                ) {
                    onCloseDrawer()
                    true
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp)
        ) {
            DrawerHeader()
            Spacer(Modifier.height(20.dp))
            DrawerDivider()
            Spacer(Modifier.height(12.dp))

            DrawerSectionLabelText("CONTENT")
            entries.take(PRIMARY_TAB_COUNT).forEach { entry ->
                DrawerItem(
                    entry = entry,
                    selected = selectedTab == entry.index,
                    isDrawerOpen = isDrawerOpen,
                    focusRequester = if (entry.index == focusAnchorIndex) anchorFocusRequester else null,
                    onClick = { onItemClick(entry.index) }
                )
            }

            Spacer(Modifier.height(16.dp))
            DrawerSectionLabelText("LIBRARY")
            DrawerItem(
                entry = entries[DrawerIndex.PLAYLISTS],
                selected = false,
                isDrawerOpen = isDrawerOpen,
                onClick = { onItemClick(DrawerIndex.PLAYLISTS) }
            )

            Spacer(Modifier.height(16.dp))
            DrawerSectionLabelText("SETTINGS")
            DrawerItem(
                entry = entries[DrawerIndex.CHECK_UPDATE],
                selected = false,
                isDrawerOpen = isDrawerOpen,
                onClick = { onItemClick(DrawerIndex.CHECK_UPDATE) }
            )
            DrawerItem(
                entry = entries[DrawerIndex.MORE],
                selected = false,
                isDrawerOpen = isDrawerOpen,
                onClick = { onItemClick(DrawerIndex.MORE) }
            )

            Spacer(Modifier.height(16.dp))
            DrawerDivider()
            Spacer(Modifier.height(8.dp))
            DrawerItem(
                entry = entries[DrawerIndex.EXIT],
                selected = false,
                isDrawerOpen = isDrawerOpen,
                onClick = { onItemClick(DrawerIndex.EXIT) }
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "StreamBD IPTV • v${BuildConfig.VERSION_NAME}",
                color = GrayText.copy(alpha = 0.4f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = BioAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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

@Composable
private fun DrawerItem(
    entry: DrawerEntry,
    selected: Boolean,
    isDrawerOpen: Boolean,
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
        label = "drawerBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            entry.danger -> Color(0xFFFF6B6B)
            isFocused || selected -> BioAccent
            else -> WhiteText.copy(alpha = 0.85f)
        },
        animationSpec = tween(180),
        label = "drawerContent"
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
                // Prevent focus from escaping into the main content while open.
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
                imageVector = entry.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = entry.label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
        }
    }
}
