package com.streambd.iptv.ui.screens.playlists

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.Playlist
import com.streambd.iptv.ui.components.PremiumButton
import com.streambd.iptv.ui.components.PremiumDialogScaffold
import com.streambd.iptv.ui.components.TvFocusableItem
import com.streambd.iptv.ui.components.TvIconButton
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.theme.BioAccent
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.GrayText
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.PlaylistViewModel
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    playlistViewModel: PlaylistViewModel,
    navController: NavController
) {
    val playlists by playlistViewModel.playlists.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        containerColor = DarkNavyBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    playlistToEdit = null
                    showDialog = true
                },
                containerColor = BioAccent
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add Playlist",
                    tint = DarkNavyBackground
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Top bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playlists",
                    color = WhiteText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No playlists added yet.", color = GrayText)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(
                            items = playlists,
                            key = { index, playlist -> "pl_${index}_${playlist.id}" }
                        ) { _, playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    val encodedUrl = URLEncoder.encode(playlist.url, "UTF-8")
                                    navController.navigate(
                                        "playlist_channels/${playlist.title}/$encodedUrl"
                                    )
                                },
                                onEdit = {
                                    playlistToEdit = playlist
                                    showDialog = true
                                },
                                onDelete = { playlistViewModel.deletePlaylist(playlist) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        PlaylistDialog(
            playlist = playlistToEdit,
            onDismiss = { showDialog = false },
            onSave = { title, url ->
                if (playlistToEdit != null) {
                    playlistViewModel.updatePlaylist(playlistToEdit!!.copy(title = title, url = url))
                } else {
                    playlistViewModel.addPlaylist(title, url)
                }
                showDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Playlist card — custom, predictable on TV + mobile
// ─────────────────────────────────────────────────────────────
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    TvFocusableItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        restBackground = Color(0xFF151A25),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Leading icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(BioAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlaylistPlay,
                contentDescription = null,
                tint = BioAccent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))

        // Title + URL
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = WhiteText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!playlist.url.isNullOrEmpty()) {
                Text(
                    text = playlist.url,
                    color = GrayText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Trailing buttons
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TvIconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Edit",
                    tint = WhiteText,
                    modifier = Modifier.size(20.dp)
                )
            }
            TvIconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Add / Edit dialog
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDialog(
    playlist: Playlist? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(playlist?.title ?: "") }
    var url by remember { mutableStateOf(playlist?.url ?: "") }

    val canSave = title.isNotBlank() && url.isNotBlank()

    PremiumDialogScaffold(
        title = if (playlist == null) "Add New Playlist" else "Edit Playlist",
        subtitle = "M3U playlist (URL or local path)",
        onDismiss = onDismiss,
        scrollContent = false,
        actions = {
            PremiumButton("Cancel", onDismiss)
            PremiumButton(
                text = "Save",
                onClick = {
                    if (canSave) onSave(title.trim(), url.trim())
                },
                primary = true
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Playlist Title", color = GrayText) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = WhiteText,
                    unfocusedTextColor = WhiteText,
                    focusedBorderColor = BioAccent,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = BioAccent
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Playlist URL (M3U)", color = GrayText) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = WhiteText,
                    unfocusedTextColor = WhiteText,
                    focusedBorderColor = BioAccent,
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = BioAccent
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
