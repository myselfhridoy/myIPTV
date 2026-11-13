package com.streambd.iptv

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.google.firebase.messaging.FirebaseMessaging
import com.streambd.iptv.ui.components.UpdateDialog
import com.streambd.iptv.ui.navigation.AppNavigation
import com.streambd.iptv.ui.theme.StreamBDPlayerTheme
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.PlaylistChannelsViewModel
import com.streambd.iptv.ui.viewmodels.PlaylistViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val tmdbViewModel: TmdbViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MainActivity", "POST_NOTIFICATIONS granted: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // INSTALL CONSCRYPT FIRST — before any network client is created.
        // (See the original comment: this fixes Cloudflare JA3 fingerprint
        // blocking on high-risk ISP IPs. Must run before any HTTPS call.)
        try {
            val conscryptProvider = org.conscrypt.Conscrypt.newProvider()
            java.security.Security.insertProviderAt(conscryptProvider, 1)
            Log.d(
                "MainActivity",
                "Conscrypt installed as top TLS provider — " +
                    "top provider is now " +
                    java.security.Security.getProviders().firstOrNull()?.name
            )
        } catch (t: Throwable) {
            Log.e("MainActivity", "Conscrypt install failed", t)
        }

        com.streambd.iptv.utils.RemoteConfigManager.init()
        mainViewModel.initTvMode(applicationContext)
        mainViewModel.fetchAddons(applicationContext)

        // Ask for POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Exception) {}
        }

        // ────────────────────────────────────────────────────────────
        // Coil image loader — FIX #2
        //
        // CHANGED: previously used UnsafeOkHttpClient.getUnsafeOkHttpClient()
        // (trust-all TLS) for every logo image. That meant any image URL
        // — including one inside a malicious playlist — could be served
        // with a self-signed cert and the app would still trust it.
        // Logo images are just bitmaps, but a MITM could:
        //   • swap a channel's logo for a phishing graphic
        //   • exfiltrate the logo URL (which may carry a token in the
        //     query string) to a fake server
        //
        // We now use UnsafeOkHttpClient.safe (default certificate
        // validation). The Browser User-Agent + 403-retry logic is kept
        // EXACTLY as before — those are needed because some IPTV CDNs
        // reject the default UA / require browser fingerprints.
        //
        // Trade-off: a handful of logos hosted on servers with expired /
        // self-signed certs will fail to load and show the initials
        // fallback. That is the correct security trade — the same
        // reason browsers refuse to load images over bad TLS.
        // ────────────────────────────────────────────────────────────
        val imageLoader = coil.ImageLoader.Builder(applicationContext)
            .okHttpClient {
                com.streambd.iptv.utils.UnsafeOkHttpClient.safe.newBuilder()
                    .addInterceptor { chain ->
                        val req = chain.request()
                        val newReq = req.newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/120.0.0.0 Safari/537.36"
                            )
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
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FCM", "Subscribed to all_users topic successfully!")
                    }
                }
        } catch (_: Exception) {}

        setContent {
            var updateAvailable by remember {
                mutableStateOf<com.streambd.iptv.utils.GitHubRelease?>(null)
            }
            val scope = rememberCoroutineScope()

            val checkUpdate = { showToastIfLatest: Boolean ->
                scope.launch {
                    try {
                        if (showToastIfLatest) {
                            android.widget.Toast.makeText(
                                applicationContext,
                                "Checking for updates…",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        val pInfo = packageManager.getPackageInfo(packageName, 0)
                        val currentVersion = pInfo.versionName ?: ""
                        val update = com.streambd.iptv.utils.UpdateManager
                            .checkForUpdate(currentVersion)
                        if (update != null) {
                            updateAvailable = update
                        } else if (showToastIfLatest) {
                            android.widget.Toast.makeText(
                                applicationContext,
                                "You are on the latest version!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            LaunchedEffect(Unit) {
                checkUpdate(false)
            }

            StreamBDPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val playlistViewModel = remember { PlaylistViewModel(applicationContext) }
                    val playlistChannelsViewModel =
                        remember { PlaylistChannelsViewModel(application) }

                    AppNavigation(
                        mainViewModel = mainViewModel,
                        tmdbViewModel = tmdbViewModel,
                        playlistViewModel = playlistViewModel,
                        playlistChannelsViewModel = playlistChannelsViewModel,
                        onCheckUpdate = { checkUpdate(true) }
                    )

                    if (updateAvailable != null) {
                        UpdateDialog(
                            release = updateAvailable!!,
                            onDismiss = { updateAvailable = null },
                            onUpdate = {
                                com.streambd.iptv.utils.UpdateManager.startDownload(
                                    applicationContext,
                                    updateAvailable!!
                                )
                                updateAvailable = null
                            }
                        )
                    }
                }
            }
        }
    }
}
