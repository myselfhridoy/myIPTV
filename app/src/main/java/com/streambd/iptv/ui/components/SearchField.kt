package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.AppTheme

/**
 * Search input used by every screen.
 *
 *  • Takes focus by itself when it appears (opens the keyboard on phones,
 *    gives the remote a target on TV) — [autoFocus].
 *  • The keyboard's Search / Done key hides the keyboard but keeps the
 *    results, so the next Back press closes the search (see the
 *    `isSearchFocused` pattern in the screens).
 *  • The close button is a real focusable button (D-pad reachable).
 */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    autoFocus: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val ownRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownRequester

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = scheme.primary
            )
        },
        trailingIcon = {
            AppIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close search",
                onClick = onClose,
                tint = scheme.onSurfaceVariant
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            cursorColor = scheme.primary,
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
    )
}

/**
 * Top bar with an optional leading button (menu / back), a title and a
 * search button that swaps the title for [AppSearchField].
 *
 *  • [showSearchButton] = false  → no search button (TV: typing is awkward)
 *  • [keepNavigationWhileSearching] → keep the back button visible in search
 *
 * The screen owns the state (query / active) and decides what the
 * navigation button does — e.g. "close search first, otherwise go back".
 */
@Composable
fun SearchableTopBar(
    title: String,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    searchPlaceholder: String,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    navigationDescription: String = "",
    onNavigationClick: () -> Unit = {},
    keepNavigationWhileSearching: Boolean = false,
    showSearchButton: Boolean = true,
    onSearchFieldFocusChanged: (Boolean) -> Unit = {}
) {
    val d = AppTheme.dimens
    Column(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.spaceSm, vertical = d.spaceXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigationIcon != null && (!searchActive || keepNavigationWhileSearching)) {
            AppIconButton(
                icon = navigationIcon,
                contentDescription = navigationDescription,
                onClick = onNavigationClick
            )
            Spacer(Modifier.width(d.spaceXs))
        } else {
            Spacer(Modifier.width(d.spaceMd))
        }

        if (!searchActive) {
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
            if (showSearchButton) {
                AppIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Search",
                    onClick = { onSearchActiveChange(true) }
                )
            }
        } else {
            AppSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = searchPlaceholder,
                onClose = {
                    onQueryChange("")
                    onSearchActiveChange(false)
                },
                modifier = Modifier.weight(1f),
                onFocusChanged = onSearchFieldFocusChanged
            )
            Spacer(Modifier.width(d.spaceSm))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
}
