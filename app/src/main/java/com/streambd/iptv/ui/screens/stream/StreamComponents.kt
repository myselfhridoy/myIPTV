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
