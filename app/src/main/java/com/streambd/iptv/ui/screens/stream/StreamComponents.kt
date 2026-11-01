package com.streambd.iptv.ui.screens.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambd.iptv.ui.components.AppIconButton
import com.streambd.iptv.ui.components.AppTextField
import com.streambd.iptv.ui.components.FocusSurface
import com.streambd.iptv.ui.components.rememberIsTv

/**
 * Text field with Paste / Clear buttons.
 *
 *  • Phone: a normal text field.
 *  • TV: remote keyboards are awkward, so the field shows as a button; OK
 *    opens the real editor, and leaving it (or pressing Done) closes it.
 *    Same behaviour as the old StreamTextField.
 */
@Composable
fun StreamTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val isTv = rememberIsTv()

    val trailing: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(
                icon = Icons.Default.ContentPaste,
                contentDescription = "Paste $label",
                onClick = {
                    val clip = readClipboardText(context)
                    if (!clip.isNullOrBlank()) onValueChange(clip)
                },
                tint = scheme.primary,
                modifier = Modifier.size(40.dp)
            )
            if (value.isNotEmpty()) {
                AppIconButton(
                    icon = Icons.Default.Clear,
                    contentDescription = "Clear $label",
                    onClick = { onValueChange("") },
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }

    if (!isTv) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = modifier.fillMaxWidth(),
            trailingIcon = trailing
        )
        return
    }

    // ── TV ──
    var isEditing by remember { mutableStateOf(false) }
    val editFocusRequester = remember { FocusRequester() }

    if (isEditing) {
        var hadFocus by remember { mutableStateOf(false) }
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            imeAction = ImeAction.Done,
            onDone = { isEditing = false },
            trailingIcon = trailing,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(editFocusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        hadFocus = false
                        isEditing = false
                    }
                }
        )
        LaunchedEffect(Unit) {
            try { editFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    } else {
        FocusSurface(
            onClick = { isEditing = true },
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            containerColor = scheme.surface.copy(alpha = 0.55f),
            focusedContainerColor = scheme.surfaceVariant,
            restingBorderColor = scheme.outline,
            focusScale = 1f,
            contentAlignment = Alignment.CenterStart,
            propagateMinConstraints = true
        ) { _ ->
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = label,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = value,
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                trailing()
            }
        }
    }
}

/**
 * "Pick one of a list" field (User-Agent, DRM scheme). Tapping it opens the
 * selection dialog owned by the screen — [onClick].
 */
@Composable
fun SelectField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        restingBorderColor = scheme.outline,
        focusScale = 1f,
        role = Role.DropdownList,
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true
    ) { _ ->
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Collapsible block. The header always shows a one-line [summary] so users
 * can see what is configured without opening it.
 */
@Composable
fun CollapsibleSection(
    title: String,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        FocusSurface(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            shape = MaterialTheme.shapes.medium,
            containerColor = scheme.surface.copy(alpha = 0.55f),
            focusedContainerColor = scheme.surfaceVariant,
            restingBorderColor = scheme.outline.copy(alpha = 0.55f),
            focusScale = 1f,
            contentAlignment = Alignment.CenterStart,
            propagateMinConstraints = true
        ) { _ ->
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!summary.isNullOrBlank()) {
                        Text(
                            text = summary,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp
                    else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}
