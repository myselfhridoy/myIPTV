package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streambd.iptv.utils.GitHubRelease

/**
 * Update prompt, built on [PremiumDialogScaffold].
 *
 * Signature unchanged, so `MainActivity` needs no edit.
 * NEW: on TV, "Update Now" receives focus automatically.
 */
@Composable
fun UpdateDialog(
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    PremiumDialogScaffold(
        title = "Update Available",
        subtitle = "Version ${release.tag_name.removePrefix("v")}",
        onDismiss = onDismiss,
        scrollContent = true,
        contentMaxHeight = 380.dp,
        actions = {
            PremiumButton("Later", onDismiss)
            PremiumButton(
                text = "Update Now",
                onClick = onUpdate,
                primary = true,
                modifier = Modifier.initialFocus()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = release.name.ifBlank { "StreamBD IPTV ${release.tag_name}" },
                color = scheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "What's new",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = release.body.ifBlank { "Bug fixes and performance improvements." },
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
