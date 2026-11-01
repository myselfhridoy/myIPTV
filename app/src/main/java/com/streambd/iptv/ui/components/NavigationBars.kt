package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.navigation.MainTab

/**
 * Phone bottom navigation for the four top-level tabs.
 * Built on Material 3 NavigationBar → touch, keyboard focus and
 * TalkBack (selected state, tab role) come for free.
 */
@Composable
fun AppBottomBar(
    selected: MainTab,
    onSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
        NavigationBar(
            containerColor = scheme.surface,
            contentColor = scheme.onSurface,
            tonalElevation = 0.dp
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = null
                        )
                    },
                    label = { Text(text = tab.title, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.onPrimary,
                        selectedTextColor = scheme.primary,
                        indicatorColor = scheme.primary,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/**
 * Tablet side rail: menu button on top, the four tabs centred.
 */
@Composable
fun AppNavigationRail(
    selected: MainTab,
    onSelected: (MainTab) -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        header = {
            Spacer(Modifier.height(8.dp))
            AppIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = "Open menu",
                onClick = onMenuClick
            )
        }
    ) {
        Spacer(Modifier.weight(1f))
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(text = tab.title, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = scheme.onPrimary,
                    selectedTextColor = scheme.primary,
                    indicatorColor = scheme.primary,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant
                )
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
