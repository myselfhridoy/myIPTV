package com.streambd.iptv

import android.os.Bundle
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val tmdbViewModel: TmdbViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.streambd.iptv.utils.RemoteConfigManager.init()
        mainViewModel.fetchAddons(applicationContext)
        
        // Configure global Coil ImageLoader with UnsafeOkHttpClient & standard Browser User-Agent to bypass SSL/TLS & CDN blocks on IPTV logo servers
        val imageLoader = coil.ImageLoader.Builder(applicationContext)
            .okHttpClient {
                com.streambd.iptv.utils.UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
                    .addInterceptor { chain ->
                        val req = chain.request()
                        val newReq = req.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                            .build()
                        var response = chain.proceed(newReq)
                        if (response.code == 403) {
                            response.close()
                            val retryReq = req.newBuilder()
                                .removeHeader("User-Agent")
                                .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                                .build()
                            response = chain.proceed(retryReq)
                        }
                        response
                    }
                    .build()
            }
            .crossfade(true)
            .build()
        coil.Coil.setImageLoader(imageLoader)
        
        // Subscribe to FCM topic for live match notifications
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Subscribed to all_users topic successfully!")
                }
            }
        
        setContent {
            var updateAvailable by androidx.compose.runtime.mutableStateOf<com.streambd.iptv.utils.GitHubRelease?>(null)
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            
            val checkUpdate = { showToastIfLatest: Boolean ->
                scope.launch {
                    try {
                        if (showToastIfLatest) {
                            android.widget.Toast.makeText(applicationContext, "Checking for updates...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        val pInfo = packageManager.getPackageInfo(packageName, 0)
                        val currentVersion = pInfo.versionName
                        val update = com.streambd.iptv.utils.UpdateManager.checkForUpdate(currentVersion)
                        if (update != null) {
                            updateAvailable = update
                        } else if (showToastIfLatest) {
                            android.widget.Toast.makeText(applicationContext, "You are on the latest version!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            androidx.compose.runtime.LaunchedEffect(Unit) {
                checkUpdate(false)
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
                        playlistChannelsViewModel = playlistChannelsViewModel,
                        onCheckUpdate = { checkUpdate(true) }
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
