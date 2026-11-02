package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Standard labelled text field (add/edit dialogs, forms).
 *
 *  • Themed colours & corners — no per-screen colour lists.
 *  • [imeAction] Next moves to the next field (default behaviour);
 *    Done runs [onDone] if given, otherwise just hides the keyboard.
 *
 * TV ([tvButtonMode], on by default): with a remote, a focused text field
 * opens the on-screen keyboard and the D-pad then moves INSIDE the keyboard,
 * so focus can never leave the field (you could not go from one field to the
 * next). On TV the field is therefore drawn as a button first:
 *   D-pad  → moves between fields like any other button
 *   OK     → opens the real editor
 *   Next / Done / leaving the editor → back to the button
 *
 * To focus it automatically (e.g. in an "Add" dialog on a phone):
 *     AppTextField(…, modifier = Modifier.initialFocus(tvOnly = false))
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    tvButtonMode: Boolean = true
) {
    if (tvButtonMode && rememberIsTv()) {
        TvTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = modifier,
            keyboardType = keyboardType,
            imeAction = imeAction,
            onDone = onDone,
            trailingIcon = trailingIcon
        )
        return
    }

    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = {
                if (onDone != null) onDone() else focusManager.clearFocus()
            }
        ),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            focusedLabelColor = scheme.primary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            cursorColor = scheme.primary,
            // Transparent on purpose: a filled container ends at the border, but the
            // floating label pokes out above it — that showed up as a two-tone box
            // behind the label. Transparent keeps the label cut-out seamless.
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        modifier = modifier
    )
}

/** TV: button that turns into the real editor on OK. */
@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onDone: (() -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?
) {
    val scheme = MaterialTheme.colorScheme
    var isEditing by remember { mutableStateOf(false) }
    val editFocusRequester = remember { FocusRequester() }

    if (isEditing) {
        var hadFocus by remember { mutableStateOf(false) }
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            keyboardType = keyboardType,
            imeAction = imeAction,
            onDone = onDone,
            trailingIcon = trailingIcon,
            tvButtonMode = false,
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(editFocusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        // Left the editor (Next / Done / Back) → back to the button.
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
                trailingIcon?.invoke()
            }
        }
    }
}
