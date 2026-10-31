package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp

/** Phones (< 600dp wide) use tighter spacing in dialogs. */
@Composable
fun rememberIsCompactLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp < 600
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

/** One selectable line in a dialog list (title, optional subtitle/badge, check). */
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
        PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    else
        PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    val titleStyle = if (isCompact)
        MaterialTheme.typography.bodyLarge
    else
        MaterialTheme.typography.titleMedium

    TvFocusableItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        contentPadding = rowPadding,
        restBackground = if (selected) scheme.primary.copy(alpha = 0.12f) else Color.Transparent
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (selected) scheme.primary else scheme.onSurface,
                style = titleStyle,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = scheme.primary,
                modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
            )
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
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
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
        focusRequester = focusRequester
    )
}
