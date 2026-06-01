package com.myselfhridoy.streambdplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.myselfhridoy.streambdplayer.ui.navigation.AppNavigation
import com.myselfhridoy.streambdplayer.ui.theme.StreamBDPlayerTheme
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.PlaylistViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.PlaylistChannelsViewModel
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val tmdbViewModel: TmdbViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreamBDPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val playlistViewModel = remember { PlaylistViewModel(applicationContext) }
                    val playlistChannelsViewModel = remember { PlaylistChannelsViewModel() }
                    
                    AppNavigation(
                        mainViewModel = mainViewModel, 
                        tmdbViewModel = tmdbViewModel,
                        playlistViewModel = playlistViewModel,
                        playlistChannelsViewModel = playlistChannelsViewModel
                    )
                }
            }
        }
    }
}
