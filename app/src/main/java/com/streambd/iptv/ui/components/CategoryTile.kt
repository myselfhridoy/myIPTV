@file:OptIn(ExperimentalFoundationApi::class)

package com.streambd.iptv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.streambd.iptv.ui.theme.AppTheme

/**
 * Wide tile for a category / playlist / group: round logo + name.
 *
 * Long names scroll (marquee) ONLY while the tile is focused — the old
 * code kept every tile's marquee running all the time, which wastes
 * frames on a grid of 30+ tiles.
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
    val d = AppTheme.dimens

    FocusSurface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.medium,
        containerColor = scheme.surface,
        focusedContainerColor = scheme.surfaceVariant,
        focusRequester = focusRequester,
        onFocused = onFocused,
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = d.spaceMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF2F5F8)),
                contentAlignment = Alignment.Center
            ) {
                if (!logoUrl.isNullOrBlank()) {
                    RemoteImage(
                        url = logoUrl,
                        label = name,
                        contentScale = ContentScale.Fit,
                        describe = false,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(d.spaceMd))
            Text(
                text = name,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        else Modifier
                    )
            )
        }
    }
}
