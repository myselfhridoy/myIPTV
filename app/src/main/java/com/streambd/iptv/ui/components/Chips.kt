package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.AppTheme

/** Pill-shaped filter chip. Selected = filled with the brand colour. Optional leading [icon]. */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val d = AppTheme.dimens

    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = d.minTouchTarget - 8.dp)
            .semantics { this.selected = selected },
        shape = CircleShape,
        containerColor = if (selected) scheme.primary else scheme.secondaryContainer,
        focusedContainerColor = if (selected) scheme.primary else scheme.outline,
        role = Role.Tab,
        focusRequester = focusRequester,
        onFocused = onFocused
    ) { _ ->
        Row(
            modifier = Modifier.padding(horizontal = d.spaceLg, vertical = d.spaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = scheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Horizontal category/filter row.
 *  • Opens already scrolled to the selected chip.
 *  • Remembers the focused chip when D-pad leaves and re-enters.
 *  • Vertical padding leaves room for the focus ring/scale.
 */
@Composable
fun CategoryChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = AppTheme.dimens.screenPaddingH,
        vertical = 6.dp
    )
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0)
    )
    LazyRow(
        modifier = modifier.restoreFocus(),
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceSm)
    ) {
        itemsIndexed(labels, key = { index, _ -> index }) { index, label ->
            CategoryChip(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelected(index) }
            )
        }
    }
}
