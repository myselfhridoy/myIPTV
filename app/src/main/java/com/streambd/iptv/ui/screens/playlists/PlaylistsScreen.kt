package com.streambd.iptv.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.streambd.iptv.data.models.Playlist
import com.streambd.iptv.ui.components.tvTouchClick
import com.streambd.iptv.ui.theme.DarkNavyBackground
import com.streambd.iptv.ui.theme.DarkNavySurface
import com.streambd.iptv.ui.theme.TealAccent
import com.streambd.iptv.ui.theme.WhiteText
import com.streambd.iptv.ui.viewmodels.PlaylistViewModel
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
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
                containerColor = TealAccent
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Playlist", tint = DarkNavyBackground)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Standard Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playlists",
                    color = WhiteText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No playlists added yet.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlists) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    val encodedUrl = URLEncoder.encode(playlist.url, "UTF-8")
                                    navController.navigate("playlist_channels/${playlist.title}/$encodedUrl")
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).tvTouchClick(onClick),
        headlineContent = {
            Text(
                text = playlist.title,
                color = WhiteText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            if (!playlist.url.isNullOrEmpty()) {
                Text(
                    text = playlist.url,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                Icons.Filled.PlaylistPlay,
                contentDescription = null,
                tint = TealAccent,
                modifier = Modifier.size(32.dp)
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.Gray)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f))
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = DarkNavySurface,
            focusedContainerColor = TealAccent.copy(alpha = 0.1f)
        ),
        shape = ListItemDefaults.shape(shape = RoundedCornerShape(12.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistDialog(
    playlist: Playlist? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(playlist?.title ?: "") }
    var url by remember { mutableStateOf(playlist?.url ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavySurface,
        title = {
            Text(if (playlist == null) "Add New Playlist" else "Edit Playlist", color = WhiteText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Playlist Title", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WhiteText,
                        unfocusedTextColor = WhiteText,
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Playlist URL (M3U)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WhiteText,
                        unfocusedTextColor = WhiteText,
                        focusedBorderColor = TealAccent,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && url.isNotBlank()) {
                        onSave(title, url)
                    }
                }
            ) {
                Text("SAVE", color = TealAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        }
    )
}
