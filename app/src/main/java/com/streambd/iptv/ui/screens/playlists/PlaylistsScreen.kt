package com.streambd.iptv.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.Playlist
import com.streambd.iptv.ui.components.AppIconButton
import com.streambd.iptv.ui.components.AppTextField
import com.streambd.iptv.ui.components.AppTopBar
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.FocusSurface
import com.streambd.iptv.ui.components.PremiumButton
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.initialFocus
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.PlaylistViewModel
import java.net.URLEncoder

/**
 * User playlists. Behaviour is unchanged (open / add / edit / delete);
 * UI is rebuilt on shared components.
 *
 * Changes you will notice:
 *  • Back button in the top bar, and "Add" is a top-bar button instead of a
 *    floating button — reachable with a TV remote too.
 *  • Delete asks for confirmation (one wrong OK press used to wipe a playlist).
 *  • Empty state has an "Add playlist" button (auto-focused on TV).
 *  • Wide screens show the playlists in two or more columns.
 */
@Composable
fun PlaylistsScreen(
    playlistViewModel: PlaylistViewModel,
    navController: NavController
) {
    val playlists by playlistViewModel.playlists.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    val d = AppTheme.dimens
    val scheme = MaterialTheme.colorScheme

    val openAddDialog = {
        playlistToEdit = null
        showDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        AppTopBar(
            title = "Playlists",
            onBack = { navController.popBackStack() }
        ) {
            AppIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Add playlist",
                onClick = openAddDialog
            )
        }
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))

        if (playlists.isEmpty()) {
            EmptyState(
                title = "No playlists added yet",
                message = "Add an M3U playlist (URL or local path) to get started.",
                icon = Icons.Filled.PlaylistPlay,
                actionLabel = "Add playlist",
                onAction = openAddDialog
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                contentPadding = PaddingValues(horizontal = d.screenPaddingH, vertical = d.spaceMd),
                horizontalArrangement = Arrangement.spacedBy(d.gridGap),
                verticalArrangement = Arrangement.spacedBy(d.gridGap),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = playlists,
                    key = { index, playlist -> "pl_${index}_${playlist.id}" }
                ) { index, playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        modifier = if (index == 0) Modifier.initialFocus() else Modifier,
                        onClick = {
                            // url may be null for file-based playlists → fall back to filePath
                            val target = playlist.url ?: playlist.filePath ?: ""
                            val encodedUrl = URLEncoder.encode(target, "UTF-8")
                            navController.navigate(
                                "playlist_channels/${playlist.title}/$encodedUrl"
                            )
                        },
                        onEdit = {
                            playlistToEdit = playlist
                            showDialog = true
                        },
                        onDelete = { playlistToDelete = playlist }
                    )
                }
            }
        }
    }

    if (showDialog) {
        PlaylistDialog(
            playlist = playlistToEdit,
            onDismiss = { showDialog = false },
            onSave = { title, url ->
                val editing = playlistToEdit
                if (editing != null) {
                    playlistViewModel.updatePlaylist(editing.copy(title = title, url = url))
                } else {
                    playlistViewModel.addPlaylist(title, url)
                }
                showDialog = false
            }
        )
    }

    playlistToDelete?.let { target ->
        PremiumDialogScaffold(
            title = "Delete playlist",
            subtitle = "“${target.title}” will be removed from this device.",
            onDismiss = { playlistToDelete = null },
            actions = {
                PremiumButton("Cancel", { playlistToDelete = null })
                PremiumButton(
                    text = "Delete",
                    onClick = {
                        playlistViewModel.deletePlaylist(target)
                        playlistToDelete = null
                    },
                    primary = true
                )
            }
        ) {}
    }
}

// ─────────────────────────────────────────────────────────────
// Playlist row — whole row opens the playlist; edit / delete are
// separate focusable buttons (D-pad: Right moves onto them).
// ─────────────────────────────────────────────────────────────
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val subtitle = playlist.url?.takeIf { it.isNotBlank() }
        ?: playlist.filePath?.takeIf { it.isNotBlank() }

    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        containerColor = scheme.surface.copy(alpha = 0.55f),
        focusedContainerColor = scheme.surfaceVariant,
        restingBorderColor = scheme.outline.copy(alpha = 0.55f),
        focusScale = 1f, // wide row: ring only, no scaling
        contentAlignment = Alignment.CenterStart,
        propagateMinConstraints = true
    ) { _ ->
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AppIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = "Edit ${playlist.title}",
                onClick = onEdit,
                tint = scheme.onSurfaceVariant
            )
            AppIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete ${playlist.title}",
                onClick = onDelete,
                tint = scheme.error
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Add / Edit dialog
// ─────────────────────────────────────────────────────────────
@Composable
fun PlaylistDialog(
    playlist: Playlist? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(playlist?.title ?: "") }
    var url by remember { mutableStateOf(playlist?.url ?: "") }

    val canSave = title.isNotBlank() && url.isNotBlank()
    val save = { if (canSave) onSave(title.trim(), url.trim()) }

    PremiumDialogScaffold(
        title = if (playlist == null) "Add New Playlist" else "Edit Playlist",
        subtitle = "M3U playlist (URL or local path)",
        onDismiss = onDismiss,
        scrollContent = false,
        actions = {
            PremiumButton("Cancel", onDismiss)
            PremiumButton(text = "Save", onClick = { save() }, primary = true)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = "Playlist Title",
                imeAction = ImeAction.Next,
                // New playlist: open the keyboard straight away (phones).
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (playlist == null) Modifier.initialFocus(tvOnly = false)
                        else Modifier
                    )
            )
            AppTextField(
                value = url,
                onValueChange = { url = it },
                label = "Playlist URL (M3U)",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onDone = { save() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
