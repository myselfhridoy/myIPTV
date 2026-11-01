@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.streambd.iptv.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.BuildConfig
import com.streambd.iptv.ui.components.FocusSurface
import com.streambd.iptv.ui.navigation.AppMenuItem
import com.streambd.iptv.ui.navigation.MainTab
import com.streambd.iptv.ui.navigation.MenuSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DrawerBg = Color(0xFF0B1218)

/**
 * Shell for TV / D-pad devices: left drawer, no bottom bar.
 *
 * Behaviour is kept from the old TvMainScreen:
 *  • Back (drawer closed) → open drawer; Back again within 2 s → exit prompt
 *  • Back (drawer open)   → close drawer
 *  • Focus jumps to the current tab when the drawer finishes opening
 *  • D-pad Right anywhere in the drawer closes it
 *  • Drawer items are NOT focusable while the drawer is closed, so the
 *    D-pad never "falls into" an invisible drawer.
 */
@Composable
fun TvShell(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onMenuItem: (AppMenuItem) -> Unit,
    onExitRequested: () -> Unit,
    content: @Composable (openMenu: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var lastBackTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackTime in 1..2000) {
                lastBackTime = 0L
                onExitRequested()
            } else {
                lastBackTime = now
                scope.launch { drawerState.open() }
            }
        }
    }

    val anchorFocusRequester = remember { FocusRequester() }

    // Focus the current tab once the drawer animation has SETTLED.
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            delay(60) // one extra frame so layout bounds are final
            runCatching { anchorFocusRequester.requestFocus() }
        }
    }

    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val closeMenu: () -> Unit = { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            TvDrawerContent(
                selectedTab = selectedTab,
                isDrawerOpen = drawerState.isOpen,
                anchorFocusRequester = anchorFocusRequester,
                onCloseDrawer = closeMenu,
                onTabClick = { tab ->
                    onTabSelected(tab)
                    closeMenu()
                },
                onMenuItemClick = { item ->
                    closeMenu()
                    onMenuItem(item)
                }
            )
        },
        scrimColor = Color.Black.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            content(openMenu)
        }
    }
}

@Composable
private fun TvDrawerContent(
    selectedTab: MainTab,
    isDrawerOpen: Boolean,
    anchorFocusRequester: FocusRequester,
    onCloseDrawer: () -> Unit,
    onTabClick: (MainTab) -> Unit,
    onMenuItemClick: (AppMenuItem) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val sections = remember { AppMenuItem.TvDrawer.groupBy { it.section } }

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
            TvDrawerHeader()
            Spacer(Modifier.height(20.dp))
            TvDrawerDivider()
            Spacer(Modifier.height(12.dp))

            TvSectionLabel("Content")
            MainTab.entries.forEach { tab ->
                TvDrawerItem(
                    icon = tab.selectedIcon,
                    label = tab.title,
                    selected = tab == selectedTab,
                    danger = false,
                    isDrawerOpen = isDrawerOpen,
                    focusRequester = if (tab == selectedTab) anchorFocusRequester else null,
                    onClick = { onTabClick(tab) }
                )
            }

            sections.forEach { (section, items) ->
                Spacer(Modifier.height(16.dp))
                if (section == MenuSection.Session) {
                    TvDrawerDivider()
                    Spacer(Modifier.height(8.dp))
                } else {
                    TvSectionLabel(section.name)
                }
                items.forEach { item ->
                    TvDrawerItem(
                        icon = item.icon,
                        label = item.label,
                        selected = false,
                        danger = item.danger,
                        isDrawerOpen = isDrawerOpen,
                        focusRequester = null,
                        onClick = { onMenuItemClick(item) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "StreamBD IPTV • v${BuildConfig.VERSION_NAME}",
                color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun TvDrawerHeader() {
    val scheme = MaterialTheme.colorScheme
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
                .background(scheme.primary.copy(alpha = 0.15f))
                .border(1.dp, scheme.primary.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LiveTv,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "StreamBD IPTV",
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                color = scheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(scheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TvSectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun TvDrawerDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 24.dp),
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.06f)
    )
}

@Composable
private fun TvDrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    danger: Boolean,
    isDrawerOpen: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    FocusSurface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            // Closed drawer must never take focus; open drawer keeps it
            // inside (Left/Right are cancelled, Right closes it above).
            .focusProperties {
                canFocus = isDrawerOpen
                if (isDrawerOpen) {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
            },
        shape = MaterialTheme.shapes.medium,
        containerColor = if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        focusedContainerColor = scheme.surfaceVariant,
        focusScale = 1.03f,
        focusRequester = focusRequester,
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true
    ) { focused ->
        val contentColor = when {
            danger -> scheme.error
            focused -> Color.White
            selected -> scheme.primary
            else -> scheme.onSurface.copy(alpha = 0.85f)
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
