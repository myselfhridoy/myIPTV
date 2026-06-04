package com.streambd.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.streambd.iptv.ui.navigation.AppNavigation
import com.streambd.iptv.ui.theme.StreamBDPlayerTheme
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import com.streambd.iptv.ui.viewmodels.PlaylistViewModel
import com.streambd.iptv.ui.viewmodels.PlaylistChannelsViewModel
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val tmdbViewModel: TmdbViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.streambd.iptv.utils.RemoteConfigManager.init()
        mainViewModel.fetchAddons(applicationContext)
        
        setContent {
            var updateAvailable by androidx.compose.runtime.mutableStateOf<com.streambd.iptv.utils.GitHubRelease?>(null)
            
            androidx.compose.runtime.LaunchedEffect(Unit) {
                try {
                    val pInfo = packageManager.getPackageInfo(packageName, 0)
                    val currentVersion = pInfo.versionName
                    updateAvailable = com.streambd.iptv.utils.UpdateManager.checkForUpdate(currentVersion)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
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
                    
                    if (updateAvailable != null) {
                        com.streambd.iptv.ui.components.UpdateDialog(
                            release = updateAvailable!!,
                            onDismiss = { updateAvailable = null },
                            onUpdate = {
                                com.streambd.iptv.utils.UpdateManager.startDownload(applicationContext, updateAvailable!!)
                                updateAvailable = null
                            }
                        )
                    }
                }
            }
        }
    }
}
