package com.streambd.iptv.ui.screens.playlists

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.Playlist
import com.streambd.iptv.ui.components.AppButton
import com.streambd.iptv.ui.components.AppButtonSize
import com.streambd.iptv.ui.components.AppButtonStyle
import com.streambd.iptv.ui.components.AppIconButton
import com.streambd.iptv.ui.components.CollapsibleSection
import com.streambd.iptv.ui.components.HeaderEditor
import com.streambd.iptv.ui.components.entriesToHeaders
import com.streambd.iptv.ui.components.headersToEntries
import com.streambd.iptv.ui.components.rememberIsTv
import com.streambd.iptv.ui.components.AppTextField
import com.streambd.iptv.ui.components.AppTopBar
import com.streambd.iptv.ui.components.EmptyState
import com.streambd.iptv.ui.components.FocusSurface
import com.streambd.iptv.ui.components.PremiumButton
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.initialFocus
import com.streambd.iptv.ui.theme.AppTheme
import com.streambd.iptv.ui.viewmodels.PlaylistViewModel
import com.streambd.iptv.utils.PlaylistSpec
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
    // `url` may carry optional headers (url|Name=Value&…) — show only the address.
    val spec = remember(playlist.url) { PlaylistSpec.parse(playlist.url.orEmpty()) }
    val subtitle = when {
        spec.isLocal -> "Local file"
        spec.url.isNotBlank() -> spec.url +
            if (spec.headers.isNotEmpty()) "  •  ${spec.headers.size} header(s)" else ""
        else -> playlist.filePath?.takeIf { it.isNotBlank() }
    }

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
//   • address: a URL, or a local file chosen with the system picker
//   • optional "Headers" (User-Agent, Referer, … or any custom header)
// Everything is saved in the single `url` string, see PlaylistSpec.
// ─────────────────────────────────────────────────────────────
@Composable
fun PlaylistDialog(
    playlist: Playlist? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    val isTv = rememberIsTv()
    val initial = remember(playlist) { PlaylistSpec.parse(playlist?.url.orEmpty()) }

    var title by remember { mutableStateOf(playlist?.title ?: "") }
    var url by remember { mutableStateOf(initial.url) }
    var entries by remember { mutableStateOf(headersToEntries(initial.headers)) }
    var headersExpanded by remember { mutableStateOf(initial.headers.isNotEmpty()) }
    var fileName by remember {
        mutableStateOf(if (initial.isLocal) context.displayNameOf(Uri.parse(initial.url)) else null)
    }
    val isLocal = url.startsWith("content://") || url.startsWith("file://")

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keep access to the file after the app restarts.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            url = uri.toString()
            val name = context.displayNameOf(uri)
            fileName = name
            if (title.isBlank() && !name.isNullOrBlank()) title = name.substringBeforeLast('.')
        }
    }
    val openPicker: () -> Unit = {
        try {
            picker.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No file picker available on this device", Toast.LENGTH_LONG).show()
        }
    }

    val canSave = title.isNotBlank() && url.isNotBlank()
    val save = {
        if (canSave) {
            val finalUrl = if (isLocal) url.trim()
            else PlaylistSpec.build(url, entriesToHeaders(entries))
            onSave(title.trim(), finalUrl)
        }
    }

    PremiumDialogScaffold(
        title = if (playlist == null) "Add New Playlist" else "Edit Playlist",
        subtitle = "M3U playlist: a link or a file",
        onDismiss = onDismiss,
        contentMaxHeight = 440.dp,
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

            if (isLocal) {
                LocalFileRow(
                    name = fileName ?: "Selected file",
                    onChange = openPicker,
                    onClear = {
                        url = ""
                        fileName = null
                    },
                    showChange = !isTv
                )
            } else {
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "Playlist URL (M3U)",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth()
                )
                // TV boxes often have no document picker, so it is offered on phones/tablets.
                if (!isTv) {
                    AppButton(
                        text = "Choose a file instead",
                        onClick = openPicker,
                        style = AppButtonStyle.Text,
                        size = AppButtonSize.Compact,
                        icon = Icons.Default.FolderOpen
                    )
                }

                CollapsibleSection(
                    title = "Headers",
                    summary = if (entries.isEmpty()) "Optional"
                    else entries.map { it.name }.filter { it.isNotBlank() }.joinToString(", "),
                    expanded = headersExpanded,
                    onToggle = { headersExpanded = !headersExpanded }
                ) {
                    HeaderEditor(
                        entries = entries,
                        onEntriesChange = { entries = it }
                    )
                }
            }
        }
    }
}

/** Shown instead of the URL field when the playlist is a local file. */
@Composable
private fun LocalFileRow(
    name: String,
    onChange: () -> Unit,
    onClear: () -> Unit,
    showChange: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, scheme.outline, MaterialTheme.shapes.medium)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Local file",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (showChange) {
            AppButton(
                text = "Change",
                onClick = onChange,
                style = AppButtonStyle.Text,
                size = AppButtonSize.Compact
            )
        }
        AppIconButton(
            icon = Icons.Default.Close,
            contentDescription = "Remove file",
            onClick = onClear,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
    }
}

/** File name shown to the user for a picked document (falls back to the last path part). */
private fun Context.displayNameOf(uri: Uri): String? {
    val fromProvider = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
        null
    }
    return fromProvider ?: uri.lastPathSegment
}
