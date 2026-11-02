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
 * The TV behaviour (button first, OK opens the editor) now lives in
 * [AppTextField], so every text field in the app behaves the same.
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

    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
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
    )
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
