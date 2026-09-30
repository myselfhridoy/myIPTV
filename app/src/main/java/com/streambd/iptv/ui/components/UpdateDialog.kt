package com.streambd.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.utils.GitHubRelease

/**
 * Update prompt. Rebuilt on `PremiumDialogScaffold` so it matches the rest of
 * the app and scrolls the changelog correctly on TV.
 *
 * Signature unchanged so `MainActivity` needs no edit.
 */
@Composable
fun UpdateDialog(
    release: GitHubRelease,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    PremiumDialogScaffold(
        title = "Update Available",
        subtitle = "Version ${release.tag_name.removePrefix("v")}",
        onDismiss = onDismiss,
        scrollContent = true,
        contentMaxHeight = 380.dp,
        actions = {
            PremiumButton("Later", onDismiss)
            PremiumButton("Update Now", onUpdate, primary = true)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = release.name.ifBlank { "StreamBD IPTV ${release.tag_name}" },
                color = BioAccent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "What's new",
                color = GrayText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = release.body.ifBlank { "Bug fixes and performance improvements." },
                color = GrayText,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
