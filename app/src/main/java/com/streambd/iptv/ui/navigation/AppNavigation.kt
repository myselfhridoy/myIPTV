package com.streambd.iptv.ui.navigation

import android.app.ActivityManager
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.streambd.iptv.ui.screens.live.LiveCategoryChannelsScreen
import com.streambd.iptv.ui.screens.live.LivePlayerScreen
import com.streambd.iptv.ui.screens.main.MainScreen
import com.streambd.iptv.ui.screens.vod.TmdbDetailsScreen
import com.streambd.iptv.ui.screens.vod.SourceSelectionScreen
import com.streambd.iptv.ui.screens.vod.VodPlayerScreen
import com.streambd.iptv.ui.screens.playlists.PlaylistsScreen
import com.streambd.iptv.ui.screens.playlists.PlaylistChannelsScreen
import com.streambd.iptv.ui.viewmodels.MainViewModel
import com.streambd.iptv.ui.viewmodels.TmdbViewModel
import com.streambd.iptv.ui.viewmodels.PlaylistViewModel
import com.streambd.iptv.ui.viewmodels.PlaylistChannelsViewModel
import com.streambd.iptv.utils.DeviceType
import com.streambd.iptv.utils.PlaybackService
import java.net.URLDecoder

object Routes {
    const val SPLASH = "splash"
    const val MAIN = "main"
    const val LIVE_CATEGORY_CHANNELS = "live_category_channels"
    const val LIVE_PLAYER = "live_player"
    const val TMDB_DETAILS = "tmdb_details"
    const val SOURCE_SELECTION = "source_selection"
    const val VOD_PLAYER = "vod_player"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_CHANNELS = "playlist_channels/{title}/{url}"
    const val ADMIN_CONVERTER = "admin_converter"
}

@UnstableApi
@Composable
fun AppNavigation(
    mainViewModel: MainViewModel,
    tmdbViewModel: TmdbViewModel,
    playlistViewModel: PlaylistViewModel,
    playlistChannelsViewModel: PlaylistChannelsViewModel,
    onCheckUpdate: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val useTvProfile = remember { DeviceType.isTv(context) }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            if (useTvProfile) {
                parameters = buildUponParameters().setTunnelingEnabled(false).build()
            }
        }
    }

    val loadControl = remember {
        val am = context.getSystemService(ActivityManager::class.java)
        val heapMb = am?.memoryClass ?: 256
        val lowRam = am?.isLowRamDevice == true || heapMb < 256

        val maxBufMs = when {
            heapMb < 128 -> if (useTvProfile) 20_000 else 12_000
            heapMb < 256 -> if (useTvProfile) 30_000 else 20_000
            heapMb < 512 -> if (useTvProfile) 40_000 else 30_000
            else         -> 45_000
        }

        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (lowRam) 5_000 else 8_000,
                maxBufMs,
                if (useTvProfile) 3_000 else 1_500,
                if (useTvProfile) 4_000 else 3_000
            )
            .setTargetBufferBytes(if (lowRam) 24 * 1024 * 1024 else C.LENGTH_UNSET)
            .setBackBuffer(if (lowRam) 5_000 else 15_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    val renderersFactory = remember {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .apply {
                if (useTvProfile) {
                    setEnableAudioTrackPlaybackParams(true)
                    forceEnableMediaCodecAsynchronousQueueing()
                }
            }
    }

    val audioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    val globalExoPlayer = remember {
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setUseLazyPreparation(true)
            .build()
            .apply { setRepeatMode(Player.REPEAT_MODE_OFF) }
    }

    // ── MediaSession: স্বয়ংক্রিয়ভাবে PiP ও নোটিফিকেশনে Play/Pause বাটন যুক্ত করবে ──
    val mediaSession = remember {
        MediaSession.Builder(context, globalExoPlayer).build()
    }

    var isPlayerPlaying by remember { mutableStateOf(false) }
    DisposableEffect(globalExoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayerPlaying = isPlaying
            }
        }
        globalExoPlayer.addListener(listener)
        onDispose { globalExoPlayer.removeListener(listener) }
    }

    // ── Foreground Service: ব্যাকগ্রাউন্ডে অডিও চালু রাখবে ──
    LaunchedEffect(isPlayerPlaying) {
        val intent = Intent(context, PlaybackService::class.java)
        if (isPlayerPlaying) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaSession.release()
            globalExoPlayer.release()
        }
    }

    // ── Safety net: stop playback when leaving player route ──
    DisposableEffect(navController) {
        val playerRoutes = setOf(Routes.LIVE_PLAYER, Routes.VOD_PLAYER)
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val arriving = destination.route
            if (arriving != null && arriving !in playerRoutes) {
                if (globalExoPlayer.isPlaying || globalExoPlayer.playbackState != Player.STATE_IDLE) {
                    globalExoPlayer.stop()
                    globalExoPlayer.clearMediaItems()
                }
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.SPLASH) {
            composable(Routes.SPLASH) {
                val isTvModeEnabled by mainViewModel.isTvModeEnabled.collectAsState()
                val selectedChannel by mainViewModel.selectedChannel.collectAsState()
                com.streambd.iptv.ui.screens.splash.SplashScreen(
                    onTimeout = {
                        if (isTvModeEnabled && selectedChannel != null) {
                            navController.navigate(Routes.LIVE_PLAYER) { popUpTo(Routes.SPLASH) { inclusive = true } }
                        } else {
                            navController.navigate(Routes.MAIN) { popUpTo(Routes.SPLASH) { inclusive = true } }
                        }
                    }
                )
            }
            composable(Routes.MAIN) {
                MainScreen(mainViewModel, tmdbViewModel, navController, onCheckUpdate)
            }
            composable(Routes.LIVE_CATEGORY_CHANNELS) {
                LiveCategoryChannelsScreen(mainViewModel, navController)
            }
            composable(Routes.LIVE_PLAYER) {
                LivePlayerScreen(mainViewModel, navController = navController, exoPlayer = globalExoPlayer, onEnterPiP = { })
            }
            composable(Routes.TMDB_DETAILS) {
                TmdbDetailsScreen(mainViewModel, tmdbViewModel, navController)
            }
            composable(Routes.SOURCE_SELECTION) {
                SourceSelectionScreen(mainViewModel, tmdbViewModel, navController)
            }
            composable(Routes.VOD_PLAYER) {
                VodPlayerScreen(mainViewModel, tmdbViewModel, navController, globalExoPlayer)
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(playlistViewModel, navController)
            }
            composable(Routes.PLAYLIST_CHANNELS) { backStackEntry ->
                val title = backStackEntry.arguments?.getString("title") ?: "Playlist"
                val url = backStackEntry.arguments?.getString("url")?.let {
                    try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
                } ?: ""
                PlaylistChannelsScreen(title, url, playlistChannelsViewModel, mainViewModel, navController)
            }
            composable(Routes.ADMIN_CONVERTER) {
                com.streambd.iptv.ui.screens.admin.AdminConverterScreen(navController)
            }
        }
    }
}
