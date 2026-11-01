package com.streambd.iptv.ui.screens.events

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.streambd.iptv.data.models.SportzfyEvent
import com.streambd.iptv.ui.components.FocusSurface
import com.streambd.iptv.ui.theme.PrimeLive
import androidx.compose.material3.Icon
import java.text.SimpleDateFormat
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// Sport filter tab (icon tile + count badge + label)
// ─────────────────────────────────────────────────────────────
@Composable
internal fun SportTab(
    name: String,
    count: Int,
    icon: ImageVector,
    selected: Boolean,
    tileSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            FocusSurface(
                onClick = onClick,
                modifier = Modifier.size(tileSize),
                shape = MaterialTheme.shapes.medium,
                containerColor = if (selected) scheme.primary.copy(alpha = 0.16f)
                else scheme.surface.copy(alpha = 0.55f),
                focusedContainerColor = scheme.surfaceVariant,
                restingBorderColor = if (selected) scheme.primary
                else scheme.outline.copy(alpha = 0.55f)
            ) { _ ->
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.82f),
                    modifier = Modifier.size(iconSize)
                )
            }
            if (count > 0) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) scheme.primary else scheme.surfaceVariant)
                        .border(1.dp, scheme.background, RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Match card
// ─────────────────────────────────────────────────────────────
@Composable
internal fun MatchCard(
    event: SportzfyEvent,
    isTv: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    val logoWidth = when { isTv -> 88.dp; isCompact -> 60.dp; else -> 72.dp }
    val logoHeight = when { isTv -> 62.dp; isCompact -> 44.dp; else -> 52.dp }
    val centerWidth = when { isTv -> 112.dp; isCompact -> 76.dp; else -> 92.dp }
    val padH = if (isTv) 16.dp else 12.dp
    val padV = if (isTv) 14.dp else 12.dp

    val isLive = event.is_live == 1
    val dateObj = parseEventDate(event.starts_at)
    val timeText = if (dateObj != null) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(dateObj)
    } else event.starts_at ?: "--:--"

    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = scheme.surface.copy(alpha = 0.55f),
        focusedContainerColor = scheme.surfaceVariant,
        restingBorderColor = scheme.outline.copy(alpha = 0.55f),
        focusScale = if (isTv) 1.02f else 1f,
        contentAlignment = Alignment.TopStart,
        propagateMinConstraints = true
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = padH, vertical = padV)
        ) {
            Text(
                text = listOfNotNull(event.sport, event.league)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(if (isTv) 14.dp else 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamColumn(
                    name = event.team_a_name ?: "Team A",
                    logoUrl = event.team_a_logo,
                    logoWidth = logoWidth,
                    logoHeight = logoHeight,
                    modifier = Modifier.weight(1f)
                )

                Column(
                    modifier = Modifier.width(centerWidth),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLive) {
                        LiveTag()
                        Spacer(Modifier.height(4.dp))
                    } else {
                        Text(
                            text = "Kickoff",
                            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = timeText,
                        color = if (isLive) scheme.onSurface.copy(alpha = 0.55f) else scheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                TeamColumn(
                    name = event.team_b_name ?: "Team B",
                    logoUrl = event.team_b_logo,
                    logoWidth = logoWidth,
                    logoHeight = logoHeight,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TeamColumn(
    name: String,
    logoUrl: String?,
    logoWidth: Dp,
    logoHeight: Dp,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = logoWidth, height = logoHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.06f), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            if (!logoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            } else {
                Text(
                    text = name.take(2).uppercase(),
                    color = scheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            color = scheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Pulsing red dot + "LIVE". Replaces the old icon + "HOT" ribbon. */
@Composable
internal fun LiveTag(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "livePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(PrimeLive.copy(alpha = alpha))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "LIVE",
            color = PrimeLive.copy(alpha = alpha),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
