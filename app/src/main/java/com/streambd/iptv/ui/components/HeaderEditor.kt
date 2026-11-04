package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Header names offered in the dropdown. "Custom…" lets the user type any name. */
val HeaderPresets = listOf(
    "User-Agent",
    "Referer",
    "Origin",
    "Cookie",
    "Authorization",
    "Accept",
    "Accept-Language",
    "Accept-Encoding",
    "X-Requested-With"
)

/** Ready-made User-Agent values (shown from the arrow inside the value field). */
private val UserAgentPresets = listOf(
    "VLC" to "VLC/3.0.20 LibVLC/3.0.20",
    "Chrome (Android)" to "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
    "Chrome (Windows)" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "okhttp" to "okhttp/4.12.0",
    "FFmpeg (Lavf)" to "Lavf/60.3.100"
)

/** One header row. [custom] = the name is typed by the user, not picked. */
data class HeaderEntry(
    val name: String = "",
    val value: String = "",
    val custom: Boolean = false
)

fun headersToEntries(headers: Map<String, String>): List<HeaderEntry> =
    headers.map { (name, value) ->
        HeaderEntry(name, value, custom = HeaderPresets.none { it.equals(name, ignoreCase = true) })
    }

/** Rows → map. Rows with a blank name or value are ignored. */
fun entriesToHeaders(entries: List<HeaderEntry>): Map<String, String> {
    val result = linkedMapOf<String, String>()
    entries.forEach { e ->
        val name = e.name.trim()
        val value = e.value.trim()
        if (name.isNotEmpty() && value.isNotEmpty()) result[name] = value
    }
    return result
}

/**
 * Editor for OPTIONAL HTTP headers.
 *
 *   [ Header ▾ ] [ value ........ ] [🗑]
 *
 * • The header name is picked from [HeaderPresets] (no typing), or
 *   "Custom…" for any other name.
 * • User-Agent rows get a ▾ in the value field with ready-made strings.
 * • "Add header" appends a row; 🗑 removes it.
 */
@Composable
fun HeaderEditor(
    entries: List<HeaderEntry>,
    onEntriesChange: (List<HeaderEntry>) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var namePickerFor by remember { mutableStateOf<Int?>(null) }
    var uaPickerFor by remember { mutableStateOf<Int?>(null) }

    val stacked = rememberIsCompactLayout()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEachIndexed { index, entry ->

            // ── name: picked from the list, or typed (Custom) ──
            val nameField: @Composable (Modifier) -> Unit = { m ->
                if (entry.custom) {
                    AppTextField(
                        value = entry.name,
                        onValueChange = { onEntriesChange(entries.replaceAt(index, entry.copy(name = it))) },
                        label = "Name",
                        trailingIcon = {
                            AppIconButton(
                                icon = Icons.Default.ArrowDropDown,
                                contentDescription = "Pick a header name",
                                onClick = { namePickerFor = index },
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        },
                        modifier = m
                    )
                } else {
                    SelectField(
                        label = "Header",
                        value = entry.name.ifBlank { "Select" },
                        onClick = { namePickerFor = index },
                        modifier = m
                    )
                }
            }

            // ── value (+ User-Agent presets) ──
            val valueField: @Composable (Modifier) -> Unit = { m ->
                AppTextField(
                    value = entry.value,
                    onValueChange = { onEntriesChange(entries.replaceAt(index, entry.copy(value = it))) },
                    label = "Value",
                    trailingIcon = if (entry.name.equals("User-Agent", ignoreCase = true)) {
                        {
                            AppIconButton(
                                icon = Icons.Default.ArrowDropDown,
                                contentDescription = "User-Agent presets",
                                onClick = { uaPickerFor = index },
                                tint = scheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    } else null,
                    modifier = m
                )
            }

            val removeButton: @Composable () -> Unit = {
                AppIconButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Remove header",
                    onClick = { onEntriesChange(entries.filterIndexed { i, _ -> i != index }) },
                    tint = scheme.error,
                    modifier = Modifier.size(40.dp)
                )
            }

            if (stacked) {
                // Phones: side by side was too tight (the name wrapped letter by
                // letter), so the value goes on its own line.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        nameField(Modifier.weight(1f))
                        removeButton()
                    }
                    valueField(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    nameField(Modifier.weight(0.4f))
                    valueField(Modifier.weight(0.6f))
                    removeButton()
                }
            }
        }

        AppButton(
            text = "Add header",
            onClick = { onEntriesChange(entries + HeaderEntry()) },
            style = AppButtonStyle.Text,
            size = AppButtonSize.Compact,
            icon = Icons.Default.Add
        )
    }

    // ── header-name picker ──
    namePickerFor?.let { index ->
        val current = entries.getOrNull(index)
        PremiumDialogScaffold(
            title = "Header",
            subtitle = "Pick a header, or choose Custom to type your own",
            onDismiss = { namePickerFor = null },
            maxWidth = 360.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rememberDialogRowGap())
            ) {
                HeaderPresets.forEach { preset ->
                    PremiumOptionRow(
                        title = preset,
                        selected = current != null && !current.custom &&
                            current.name.equals(preset, ignoreCase = true),
                        onClick = {
                            if (current != null) {
                                onEntriesChange(
                                    entries.replaceAt(index, current.copy(name = preset, custom = false))
                                )
                            }
                            namePickerFor = null
                        }
                    )
                }
                PremiumOptionRow(
                    title = "Custom…",
                    subtitle = "Type any header name",
                    selected = current?.custom == true,
                    onClick = {
                        if (current != null) {
                            onEntriesChange(
                                entries.replaceAt(
                                    index,
                                    current.copy(custom = true, name = if (current.custom) current.name else "")
                                )
                            )
                        }
                        namePickerFor = null
                    }
                )
            }
        }
    }

    // ── User-Agent presets ──
    uaPickerFor?.let { index ->
        val current = entries.getOrNull(index)
        PremiumDialogScaffold(
            title = "User-Agent",
            subtitle = "Fills the value — you can still edit it",
            onDismiss = { uaPickerFor = null },
            maxWidth = 360.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rememberDialogRowGap())
            ) {
                UserAgentPresets.forEach { (label, ua) ->
                    PremiumOptionRow(
                        title = label,
                        subtitle = ua,
                        selected = current?.value == ua,
                        onClick = {
                            if (current != null) {
                                onEntriesChange(entries.replaceAt(index, current.copy(value = ua)))
                            }
                            uaPickerFor = null
                        }
                    )
                }
            }
        }
    }
}

private fun <T> List<T>.replaceAt(index: Int, item: T): List<T> =
    mapIndexed { i, old -> if (i == index) item else old }
