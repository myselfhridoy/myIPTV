package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.theme.PrimeGold
import com.streambd.iptv.ui.theme.PrimeLive

/**
 * Flat base colour for a logo tile (used as the FocusSurface container,
 * so focus animation still works).
 */
private val LogoTileColor = Color(0xFFEDF1F5)

/**
 * Radial "spotlight" gradient painted BEHIND the logo.
 * Gives solid-white logos (Sony, Star, etc.) a soft contour against the
 * otherwise flat grey tile — the same trick used by Prime Video on TV.
 * Skipped while the card is focused (surface is already white).
 */
private val LogoTileBrush = Brush.radialGradient(
    colors = listOf(Color.White, Color(0xFFD9E2EC)),
    center = Offset.Unspecified
)

@Composable
fun ChannelCard(
    name: String,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isLive: Boolean = false,
    showTitle: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    val d = AppTheme.dimens
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            containerColor = LogoTileColor,
            focusedContainerColor = Color.White,
            restingBorderColor = Color.Black.copy(alpha = 0.08f),
            ringColor = MaterialTheme.colorScheme.primary,
            focusRequester = focusRequester,
            onFocused = onFocused
        ) { focused ->
            // Radial gradient behind the logo — subtle 3D pop.
            // While focused the surface turns solid white; the gradient
            // would just add noise there, so we skip it.
            if (!focused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LogoTileBrush)
                )
            }
            RemoteImage(
                url = logoUrl,
                label = name,
                contentScale = ContentScale.Fit,
                describe = !showTitle,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(d.spaceMd)
            )
            if (isLive) {
                LiveBadge(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(d.spaceXs)
                )
            }
        }
        if (showTitle) {
            Text(
                text = name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = d.spaceXs + 2.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: String? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    val d = AppTheme.dimens
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        FocusSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            containerColor = MaterialTheme.colorScheme.surface,
            focusRequester = focusRequester,
            onFocused = onFocused
        ) { _ ->
            RemoteImage(
                url = imageUrl,
                label = title,
                contentScale = ContentScale.Crop,
                describe = false,
                modifier = Modifier.fillMaxSize()
            )
            if (!rating.isNullOrBlank()) {
                RatingBadge(
                    text = rating,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(d.spaceXs)
                )
            }
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = d.spaceXs + 2.dp, start = 2.dp, end = 2.dp)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = "LIVE",
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PrimeLive)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun RatingBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = "★ $text",
        color = PrimeGold,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun RemoteImage(
    url: String?,
    label: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    describe: Boolean = true
) {
    val context = LocalContext.current
    var failed by remember(url) { mutableStateOf(false) }

    if (url.isNullOrBlank() || failed) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = initialsOf(label),
                color = Color(0xFF3A4A5C),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    } else {
        val request = remember(url, context) {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = if (describe) label else null,
            contentScale = contentScale,
            onError = { failed = true },
            modifier = modifier
        )
    }
}

private fun initialsOf(label: String): String =
    label.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
