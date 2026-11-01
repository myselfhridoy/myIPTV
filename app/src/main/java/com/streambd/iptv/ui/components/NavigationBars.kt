package com.streambd.iptv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.navigation.MainTab

/**
 * Phone bottom bar — flat, same colour as the page, hairline on top.
 * Selected tab: filled icon + bold label in the brand colour with a short
 * indicator on the top edge. (No Material "pill".)
 */
@Composable
fun AppBottomBar(
    selected: MainTab,
    onSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.background)
    ) {
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.5f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MainTab.entries.forEach { tab ->
                BottomBarItem(
                    tab = tab,
                    isSelected = tab == selected,
                    onClick = { onSelected(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: MainTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        targetValue = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "tabTint"
    )

    Column(
        modifier = modifier
            .height(62.dp)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // top-edge indicator
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(if (isSelected) scheme.primary else Color.Transparent)
        )
        Spacer(Modifier.height(8.dp))
        Icon(
            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.title,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Tablet side rail: menu button on top, the four tabs centred. */
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
        containerColor = scheme.background,
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
                    selectedIconColor = scheme.primary,
                    selectedTextColor = scheme.primary,
                    indicatorColor = scheme.primary.copy(alpha = 0.14f),
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant
                )
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
