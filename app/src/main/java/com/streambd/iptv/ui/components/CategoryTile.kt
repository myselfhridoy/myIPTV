package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Backdrop behind a logo. Subtle translucent white — logos still read, but
 *  the tile no longer has a bright white patch. For dark-text logos that fade,
 *  raise the alpha (or use Color(0xFFC7CFD8)). */
private val LogoBackground = Color.White.copy(alpha = 0.16f)

/**
 * Category / playlist tile: dark fill + hairline border, square-ish logo
 * with a thin outline (brand colour on focus), regular-weight name that
 * may wrap to two lines.
 */
@Composable
fun CategoryTile(
    name: String,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme

    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        containerColor = scheme.surface.copy(alpha = 0.55f),
        focusedContainerColor = scheme.surfaceVariant,
        restingBorderColor = scheme.outline.copy(alpha = 0.55f),
        focusRequester = focusRequester,
        onFocused = onFocused,
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (focused) scheme.primary else scheme.outline,
                        shape = CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(LogoBackground),
                contentAlignment = Alignment.Center
            ) {
                if (!logoUrl.isNullOrBlank()) {
                    RemoteImage(
                        url = logoUrl,
                        label = name,
                        contentScale = ContentScale.Fit,
                        describe = false,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
