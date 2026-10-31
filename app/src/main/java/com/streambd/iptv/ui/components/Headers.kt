package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.AppTheme

/** Title for a content row, with an optional "See all" style action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val d = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = d.screenPaddingH, vertical = d.spaceSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (actionText != null && onActionClick != null) {
            AppButton(
                text = actionText,
                onClick = onActionClick,
                style = AppButtonStyle.Text
            )
        }
    }
}

/**
 * Top bar for inner screens. Pass [onBack] to show a back button
 * (give it [backFocusRequester] if the screen wants to drive focus).
 * Insets are not handled yet — that arrives with the edge-to-edge step.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backFocusRequester: FocusRequester? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val d = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(horizontal = d.spaceSm, vertical = d.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(d.spaceXs)
    ) {
        if (onBack != null) {
            AppIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                focusRequester = backFocusRequester
            )
        } else {
            Spacer(Modifier.width(d.spaceSm))
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        actions()
    }
}
