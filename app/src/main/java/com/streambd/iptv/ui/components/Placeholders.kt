package com.streambd.iptv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.AppTheme

// ── Shimmer ──────────────────────────────────────────────────

/** Animated loading sweep. Animation state is read only while drawing,
 *  so it never triggers recomposition. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    val base = MaterialTheme.colorScheme.surface
    val highlight = MaterialTheme.colorScheme.surfaceVariant

    drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val startX = -w + progress * 3f * w
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(startX, 0f),
                end = Offset(startX + w, 0f)
            )
        )
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small
) {
    Box(modifier = modifier
        .clip(shape)
        .shimmer())
}

@Composable
fun ChannelCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ShimmerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Spacer(Modifier.height(AppTheme.dimens.spaceSm))
        ShimmerBox(Modifier.fillMaxWidth(0.7f).height(12.dp), RoundedCornerShape(4.dp))
    }
}

@Composable
fun PosterCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ShimmerBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Spacer(Modifier.height(AppTheme.dimens.spaceSm))
        ShimmerBox(Modifier.fillMaxWidth(0.8f).height(12.dp), RoundedCornerShape(4.dp))
    }
}

// ── Full-area states ─────────────────────────────────────────

/**
 * Empty list / no results. If an [actionLabel] is given the button is
 * auto-focused on TV, so D-pad never starts on an empty screen.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Outlined.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val d = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(d.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(d.spaceLg))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(d.spaceXs))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(d.spaceXl))
            AppButton(
                text = actionLabel,
                onClick = onAction,
                style = AppButtonStyle.Secondary,
                modifier = Modifier.initialFocus()
            )
        }
    }
}

/** Failure with a Retry button (auto-focused on TV). */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
    retryLabel: String = "Retry"
) {
    val d = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(d.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(d.spaceLg))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(d.spaceXs))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(d.spaceXl))
        AppButton(
            text = retryLabel,
            onClick = onRetry,
            icon = Icons.Filled.Refresh,
            modifier = Modifier.initialFocus()
        )
    }
}
