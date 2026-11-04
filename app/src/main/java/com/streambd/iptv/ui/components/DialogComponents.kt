package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dialog density: tight on phones — in portrait (narrow) AND in landscape
 * (wide but short, < 480dp tall). Tablets and TVs keep the roomy layout.
 */
@Composable
fun rememberIsCompactLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp < 600 || config.screenHeightDp < 480
}

/**
 * General focusable ROW (touch + D-pad + TalkBack), built on [FocusSurface].
 * Same signature as before, so existing call sites keep working.
 *
 * Rows are wide, so they do not scale on focus — they get the ring and a
 * soft brand tint instead.
 */
@Composable
fun TvFocusableItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    restBackground: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val focusedBackground = scheme.primary.copy(alpha = 0.22f).compositeOver(scheme.surface)

    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        containerColor = restBackground,
        focusedContainerColor = focusedBackground,
        focusScale = 1f,
        focusRequester = focusRequester,
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true
    ) { _ ->
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * One selectable line in a dialog list — Material "single choice" pattern:
 * radio button · title (+ subtitle) · optional badge.
 */
@Composable
fun PremiumOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    focusRequester: FocusRequester? = null
) {
    val scheme = MaterialTheme.colorScheme
    val isCompact = rememberIsCompactLayout()
    val rowPadding = if (isCompact)
        PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    else
        PaddingValues(horizontal = 14.dp, vertical = 10.dp)

    TvFocusableItem(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
        focusRequester = focusRequester,
        shape = MaterialTheme.shapes.medium,
        contentPadding = rowPadding
    ) {
        // onClick = null → purely visual; the whole row is the click target.
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = scheme.primary,
                unselectedColor = scheme.onSurfaceVariant
            )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.onSurface,
                style = if (isCompact) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            PremiumBadge(badge)
        }
    }
}

@Composable
fun PremiumBadge(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        color = scheme.primary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .border(1.dp, scheme.primary.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * Dialog action button. Thin wrapper over [AppButton] so every button in the
 * app shares one look and one focus behaviour.
 *   primary = true  → filled brand button
 *   primary = false → quiet secondary button
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = if (primary) AppButtonStyle.Primary else AppButtonStyle.Secondary,
        size = AppButtonSize.Compact,
        focusRequester = focusRequester
    )
}

/**
 * Material-style tabs for dialogs: label with an underline indicator on the
 * selected tab and a hairline under the whole row. Works with touch and D-pad.
 */
@Composable
fun DialogTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isCompact = rememberIsCompactLayout()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                FocusSurface(
                    onClick = { onSelect(index) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { this.selected = isSelected },
                    shape = MaterialTheme.shapes.small,
                    containerColor = Color.Transparent,
                    focusedContainerColor = scheme.primary.copy(alpha = 0.16f)
                        .compositeOver(scheme.surface),
                    focusScale = 1f,
                    role = Role.Tab
                ) { _ ->
                    Column(
                        modifier = Modifier.padding(top = if (isCompact) 8.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(Modifier.height(if (isCompact) 6.dp else 10.dp))
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (isSelected) scheme.primary else Color.Transparent)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
    }
}

/** Dialog lists never take more than half the screen (and never more than [max]). */
@Composable
fun rememberDialogListMaxHeight(max: Dp): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    return (screenHeight * 0.5f).dp.coerceIn(140.dp, max)
}

/** Gap between rows in a dialog list: tight on phones. */
@Composable
fun rememberDialogRowGap(): Dp = if (rememberIsCompactLayout()) 2.dp else 6.dp
