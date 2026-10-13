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

    // Runtime permission for Android 13+ notifications
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MainActivity", "POST_NOTIFICATIONS granted: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ─────────────────────────────────────────────────────────────────
        // INSTALL CONSCRYPT FIRST — before any network client is created.
        //
        // WHY THIS IS THE VERY FIRST THING IN onCreate
        // --------------------------------------------
        // Cloudflare's Bot Management inspects the TLS handshake's JA3
        // fingerprint. On high-risk ISP IPs (some WiFi networks flagged
        // for bot traffic), Cloudflare only accepts handshakes whose JA3
        // matches a whitelisted client — curl (OpenSSL), Chrome, Firefox.
        //
        // OkHttp's default BoringSSL on Android has a JA3 that is NOT on
        // that whitelist, so those requests get a 403 — even with perfect
        // headers. That's why the same app works on mobile data (trusted
        // IP) but fails on some WiFi networks (flagged IP).
        //
        // Conscrypt is Android's official JSSE provider that uses
        // Google's BoringSSL with the SAME JA3 as Chrome / Android
        // System WebView. Installing it here, before any HTTPS call
        // anywhere in the app, makes every subsequent TLS handshake
        // inherit the browser-equivalent fingerprint — so Cloudflare
        // accepts the request and the WiFi 403 disappears.
        //
        // Must run BEFORE:
        //   • RemoteConfigManager.init() — may fetch remote config
        //   • mainViewModel.initTvMode()  — may hit Firebase
        //   • Any OkHttp / Retrofit / Coil client creation
        // ─────────────────────────────────────────────────────────────────
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

        // Configure Coil's global image loader (safe-for-IPTV-logos).
        // NOTE: the Conscrypt provider installed above is already active,
        // so this client inherits the browser-equivalent TLS fingerprint
        // automatically. No extra TLS configuration needed here.
        val imageLoader = coil.ImageLoader.Builder(applicationContext)
            .okHttpClient {
                com.streambd.iptv.utils.UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
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
