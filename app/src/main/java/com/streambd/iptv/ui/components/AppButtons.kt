package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.AppTheme

enum class AppButtonStyle { Primary, Secondary, Text }

/**
 * Standard button. When focused (D-pad) it turns white with dark text —
 * the same strong focus cue Prime Video uses on TV.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null
) {
    val scheme = MaterialTheme.colorScheme
    val d = AppTheme.dimens

    val container = when (style) {
        AppButtonStyle.Primary -> scheme.primary
        AppButtonStyle.Secondary -> scheme.secondaryContainer
        AppButtonStyle.Text -> Color.Transparent
    }
    val focusedContainer = when (style) {
        AppButtonStyle.Text -> scheme.surfaceVariant
        else -> Color.White
    }
    val restContent = when (style) {
        AppButtonStyle.Primary -> scheme.onPrimary
        AppButtonStyle.Secondary -> scheme.onSecondaryContainer
        AppButtonStyle.Text -> scheme.primary
    }
    val focusedContent = if (style == AppButtonStyle.Text) scheme.onSurface else scheme.background

    FocusSurface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = d.minTouchTarget),
        enabled = enabled,
        containerColor = container,
        focusedContainerColor = focusedContainer,
        focusRequester = focusRequester
    ) { focused ->
        val color = if (focused) focusedContent else restContent
        Row(
            modifier = Modifier.padding(horizontal = d.spaceXl, vertical = d.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.spaceSm)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Round icon button. [contentDescription] is REQUIRED — an icon-only
 * control without it is invisible to TalkBack.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    focusRequester: FocusRequester? = null
) {
    val d = AppTheme.dimens
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(d.minTouchTarget),
        enabled = enabled,
        shape = CircleShape,
        containerColor = Color.Transparent,
        focusedContainerColor = Color.White.copy(alpha = 0.18f),
        focusScale = if (d.focusScale > 1f) 1.08f else 1f,
        role = Role.Button,
        focusRequester = focusRequester
    ) { _ ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
