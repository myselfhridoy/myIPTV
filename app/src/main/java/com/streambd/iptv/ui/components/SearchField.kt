package com.streambd.iptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.theme.AppTheme

/**
 * THE search input — every screen uses this one, so shape, colours and
 * behaviour are identical everywhere.
 *
 *  • Opens focused (keyboard on phones, a target for the remote on TV).
 *  • The keyboard's SEARCH key closes the keyboard, keeps the results and
 *    moves focus to the content below (the results), so the D-pad / user
 *    is free to pick one. A screen can replace this with [onSearch].
 *  • The close button is a real focusable button.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    autoFocus: Boolean = true,
    onSearch: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
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
            // Darker than the header band → reads as an inset field.
            focusedContainerColor = scheme.background,
            unfocusedContainerColor = scheme.background
        ),
        shape = MaterialTheme.shapes.medium,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboard?.hide()
                if (onSearch != null) {
                    onSearch()
                } else if (!focusManager.moveFocus(FocusDirection.Down)) {
                    focusManager.clearFocus()
                }
            }
        ),
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
    )
}

/**
 * Header in "search mode": optional [leading] control (e.g. a back button)
 * + [AppSearchField]. Same band as [AppTopBar] / [SearchableTopBar], so the
 * header does not change look when search opens.
 */
@Composable
fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onSearch: (() -> Unit)? = null
) {
    val d = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = d.spaceSm, vertical = d.spaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(d.spaceXs))
            } else {
                Spacer(Modifier.width(d.spaceMd))
            }
            AppSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                onClose = onClose,
                modifier = Modifier.weight(1f),
                onFocusChanged = onFocusChanged,
                onSearch = onSearch
            )
            Spacer(Modifier.width(d.spaceSm))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
}

/**
 * Top bar with an optional leading button (menu / back), a title and a
 * search button that swaps the title for the search field ([SearchHeader]).
 *
 *  • [showSearchButton] = false  → no search button
 *  • [keepNavigationWhileSearching] → keep the back button visible in search
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
    val scheme = MaterialTheme.colorScheme

    if (searchActive) {
        val navButton: (@Composable () -> Unit)? =
            if (navigationIcon != null && keepNavigationWhileSearching) {
                {
                    AppIconButton(
                        icon = navigationIcon,
                        contentDescription = navigationDescription,
                        onClick = onNavigationClick
                    )
                }
            } else null

        SearchHeader(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = searchPlaceholder,
            onClose = {
                onQueryChange("")
                onSearchActiveChange(false)
            },
            modifier = modifier,
            leading = navButton,
            onFocusChanged = onSearchFieldFocusChanged
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = d.spaceSm, vertical = d.spaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                AppIconButton(
                    icon = navigationIcon,
                    contentDescription = navigationDescription,
                    onClick = onNavigationClick
                )
                Spacer(Modifier.width(d.spaceXs))
            } else {
                Spacer(Modifier.width(d.spaceMd))
            }
            Text(
                text = title,
                color = scheme.onSurface,
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
        }
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
    }
}
