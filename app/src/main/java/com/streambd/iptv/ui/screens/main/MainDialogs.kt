package com.streambd.iptv.ui.screens.main

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streambd.iptv.ui.components.PremiumButton
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.TvFocusableItem
import com.streambd.iptv.ui.navigation.AppMenuItem
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.PlaylistViewModel
import com.streambd.iptv.utils.AnalyticsLogger

/** "Are you sure you want to exit?" */
@Composable
fun ExitDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    PremiumDialogScaffold(
        title = "Exit App",
        subtitle = "Are you sure you want to exit?",
        onDismiss = onDismiss,
        actions = {
            PremiumButton("Cancel", onDismiss)
            PremiumButton("Exit", onConfirm, primary = true)
        }
    ) {}
}

@Composable
fun CopyrightDialog(onDismiss: () -> Unit) {
    PremiumDialogScaffold(
        title = "Copyright & Disclaimer",
        subtitle = "Legal notice",
        onDismiss = onDismiss,
        contentMaxHeight = 360.dp
    ) {
        Text(
            text = "We do not host, manage, or upload any of the channels " +
                "or video content provided in this application. All streams and " +
                "multimedia content are aggregated from publicly available free " +
                "sources on the internet.\n\n" +
                "This app acts purely as a streaming platform. If you believe any " +
                "content infringes upon your copyright, please contact the original " +
                "content hosts directly.\n\n" +
                "© 2026 StreamBD. All rights reserved.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

/**
 * "More Options" list. Now built on PremiumDialogScaffold, so it gets the
 * same look and the same TV focus safety-net as every other dialog.
 */
@Composable
fun MoreDialog(
    items: List<AppMenuItem>,
    labelOf: (AppMenuItem) -> String,
    onItemClick: (AppMenuItem) -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    PremiumDialogScaffold(
        title = "More Options",
        onDismiss = onDismiss,
        maxWidth = 360.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            items.forEach { item ->
                TvFocusableItem(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = labelOf(item),
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * TV Mode configuration. Logic is copied 1:1 from the old MainScreen.kt;
 * only the styling moved to theme tokens.
 */
@Composable
fun TvModeConfigDialog(
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val appContext = LocalContext.current.applicationContext

    val categories by mainViewModel.liveCategories.collectAsState()

    // Safe factory — no Activity context leak, no unchecked cast.
    val playlistVmFactory = remember {
        viewModelFactory {
            initializer {
                PlaylistViewModel(appContext)
            }
        }
    }
    val playlistViewModel: PlaylistViewModel = viewModel(factory = playlistVmFactory)
    val userPlaylists by playlistViewModel.playlists.collectAsState()

    var selectedCategories by remember { mutableStateOf(mainViewModel.tvModePlaylists.value) }
    var includeEvents by remember {
        mutableStateOf(
            appContext.getSharedPreferences("tv_mode_prefs", Context.MODE_PRIVATE)
                .getBoolean("tv_mode_include_events", true)
        )
    }

    PremiumDialogScaffold(
        title = "TV Mode Configuration",
        subtitle = "Select playlists to include in TV Mode",
        onDismiss = onDismiss,
        contentMaxHeight = 380.dp,
        actions = {
            PremiumButton("Cancel", onDismiss)
            PremiumButton("Save & Restart", {
                AnalyticsLogger.logSettingsChanged(
                    context = appContext,
                    key = "tv_mode",
                    value = "on"
                )
                mainViewModel.saveTvModeConfig(true, selectedCategories, includeEvents)
                onConfirm()
            }, primary = true)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            CheckRow(
                label = "Live Events (Default)",
                checked = includeEvents,
                onToggle = { includeEvents = !includeEvents }
            )

            categories.forEach { category ->
                val isChecked = selectedCategories.contains(category.name)
                CheckRow(
                    label = category.name,
                    checked = isChecked,
                    onToggle = {
                        selectedCategories = if (isChecked) selectedCategories - category.name
                        else selectedCategories + category.name
                    }
                )
            }

            if (userPlaylists.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "User Playlists",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                userPlaylists.forEach { playlist ->
                    val isChecked = selectedCategories.contains(playlist.title)
                    CheckRow(
                        label = playlist.title,
                        checked = isChecked,
                        onToggle = {
                            selectedCategories = if (isChecked) selectedCategories - playlist.title
                            else selectedCategories + playlist.title
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    TvFocusableItem(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = scheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = scheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
