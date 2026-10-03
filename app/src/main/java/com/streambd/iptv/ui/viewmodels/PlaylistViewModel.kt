package com.streambd.iptv.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambd.iptv.data.models.Playlist
import com.streambd.iptv.data.repository.AppDatabase
import com.streambd.iptv.utils.AnalyticsLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistViewModel(context: Context) : ViewModel() {
    private val playlistDao = AppDatabase.getDatabase(context).playlistDao()

    // Kept for analytics — the application context is safe to retain in a
    // ViewModel (it lives as long as the process, not the Activity).
    private val appContext = context.applicationContext

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    init {
        viewModelScope.launch {
            playlistDao.getAllPlaylists().collect { list ->
                _playlists.value = list
            }
        }
    }

    fun addPlaylist(title: String, url: String) {
        // ── Analytics: track playlist creation ──
        AnalyticsLogger.logPlaylistAdded(
            context = appContext,
            title = title,
            type = when {
                url.startsWith("http://", true) ||
                    url.startsWith("https://", true) -> "m3u_url"
                url.startsWith("file://") ||
                    url.startsWith("/") -> "m3u_file"
                else -> "unknown"
            }
        )

        viewModelScope.launch {
            playlistDao.insert(Playlist(title = title, url = url))
        }
    }

    fun updatePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistDao.update(playlist)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistDao.delete(playlist)
        }
    }
}
