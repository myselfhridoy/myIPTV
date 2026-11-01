package com.streambd.iptv.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Standard labelled text field (add/edit dialogs, forms).
 *
 *  • Themed colours & corners — no per-screen colour lists.
 *  • [imeAction] Next moves to the next field (default behaviour);
 *    Done runs [onDone] if given, otherwise just hides the keyboard.
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
    onDone: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
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
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface
        ),
        modifier = modifier
    )
}
