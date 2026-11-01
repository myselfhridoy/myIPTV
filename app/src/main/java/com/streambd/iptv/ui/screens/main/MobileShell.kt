package com.streambd.iptv.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streambd.iptv.BuildConfig
import com.streambd.iptv.R
import com.streambd.iptv.ui.components.AppBottomBar
import com.streambd.iptv.ui.components.AppNavigationRail
import com.streambd.iptv.ui.navigation.AppMenuItem
import com.streambd.iptv.ui.navigation.MainTab
import kotlinx.coroutines.launch

/**
 * Shell for PHONES (bottom bar) and TABLETS (side rail).
 * Both share the same side drawer, back behaviour and content slot.
 *
 * [content] receives `openMenu` — screens call it from their menu button.
 */
@Composable
fun MobileShell(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onBackToStart: () -> Unit,
    drawerItems: List<AppMenuItem>,
    labelOf: (AppMenuItem) -> String,
    onMenuItem: (AppMenuItem) -> Unit,
    onOpenAdmin: () -> Unit,
    onExitRequested: () -> Unit,
    useRail: Boolean,
    content: @Composable (openMenu: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scheme = MaterialTheme.colorScheme

    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val closeMenu: () -> Unit = { scope.launch { drawerState.close() } }

    // Back: close drawer → return to first tab → ask to exit.
    BackHandler {
        when {
            drawerState.isOpen -> closeMenu()
            selectedTab != MainTab.Events -> onBackToStart()
            else -> onExitRequested()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MobileDrawerSheet(
                items = drawerItems,
                labelOf = labelOf,
                onItemClick = { item ->
                    closeMenu()
                    onMenuItem(item)
                },
                onLogoSecretTap = {
                    onOpenAdmin()
                    closeMenu()
                }
            )
        }
    ) {
        if (useRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.background)
            ) {
                AppNavigationRail(
                    selected = selectedTab,
                    onSelected = onTabSelected,
                    onMenuClick = openMenu,
                    modifier = Modifier.fillMaxHeight()
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    content(openMenu)
                }
            }
        } else {
            Scaffold(
                containerColor = scheme.background,
                bottomBar = {
                    AppBottomBar(
                        selected = selectedTab,
                        onSelected = onTabSelected
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    content(openMenu)
                }
            }
        }
    }
}

@Composable
private fun MobileDrawerSheet(
    items: List<AppMenuItem>,
    labelOf: (AppMenuItem) -> String,
    onItemClick: (AppMenuItem) -> Unit,
    onLogoSecretTap: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = scheme.surface,
        drawerContentColor = scheme.onSurface
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // ── Header (7 quick taps on the logo = hidden admin tool) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var logoTapCount by remember { mutableIntStateOf(0) }
                var logoLastTapTime by remember { mutableLongStateOf(0L) }
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
                                onLogoSecretTap()
                            }
                            logoLastTapTime = now
                        }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            // ── Items ──
            items.forEach { item ->
                val tint = if (item.danger) scheme.error else scheme.onSurface
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (item.danger) scheme.error else scheme.onSurfaceVariant
                        )
                    },
                    label = {
                        Text(
                            text = labelOf(item),
                            color = tint,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    selected = false,
                    onClick = { onItemClick(item) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
