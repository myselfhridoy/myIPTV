package com.streambd.iptv.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
    
    // Global Player Instance with Optimized IPTV / Live Stream settings
    val trackSelector = remember { DefaultTrackSelector(context) }
    
    val loadControl = remember {
        androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30000,       // 30s min buffer (was 15s)
                /* maxBufferMs = */ 300000,       // 5 min max buffer for 4K streams (was 120s)
                /* bufferForPlaybackMs = */ 2500, // Start playback after 2.5s buffered
                /* bufferForPlaybackAfterRebufferMs = */ 5000 // After rebuffer, wait 5s
            )
            .setBackBuffer(60000, true) // Keep 60s back-buffer, evict when needed
            .setPrioritizeTimeOverSizeThresholds(false) // Allow larger buffers for 4K
            .build()
    }
    
    val renderersFactory = remember {
        androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    }
    
    val audioAttributes = remember {
        androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    val globalExoPlayer = remember {
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            globalExoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.SPLASH) {
            composable(Routes.SPLASH) {
                val isTvModeEnabled by mainViewModel.isTvModeEnabled.collectAsState()
                val selectedChannel by mainViewModel.selectedChannel.collectAsState()
                
                com.streambd.iptv.ui.screens.splash.SplashScreen(
                    onTimeout = {
                        if (isTvModeEnabled && selectedChannel != null) {
                            navController.navigate(Routes.LIVE_PLAYER) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.MAIN) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(Routes.MAIN) {
                MainScreen(
                    mainViewModel = mainViewModel, 
                    tmdbViewModel = tmdbViewModel, 
                    navController = navController,
                    onCheckUpdate = onCheckUpdate
                )
            }
            composable(Routes.LIVE_CATEGORY_CHANNELS) {
                LiveCategoryChannelsScreen(viewModel = mainViewModel, navController = navController)
            }
            composable(Routes.LIVE_PLAYER) {
                LivePlayerScreen(
                    viewModel = mainViewModel, 
                    navController = navController,
                    exoPlayer = globalExoPlayer,
                    trackSelector = trackSelector,
                    onEnterPiP = {
                        // Handled natively inside LivePlayerScreen
                    }
                )
            }
            composable(Routes.TMDB_DETAILS) {
                TmdbDetailsScreen(mainViewModel = mainViewModel, tmdbViewModel = tmdbViewModel, navController = navController)
            }
            composable(Routes.SOURCE_SELECTION) {
                SourceSelectionScreen(
                    mainViewModel = mainViewModel,
                    tmdbViewModel = tmdbViewModel,
                    navController = navController
                )
            }
            composable(Routes.VOD_PLAYER) {
                VodPlayerScreen(
                    mainViewModel = mainViewModel,
                    tmdbViewModel = tmdbViewModel,
                    navController = navController,
                    exoPlayer = globalExoPlayer,
                    trackSelector = trackSelector
                )
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(playlistViewModel = playlistViewModel, navController = navController)
            }
            composable(Routes.PLAYLIST_CHANNELS) { backStackEntry ->
                val title = backStackEntry.arguments?.getString("title") ?: "Playlist"
                val url = backStackEntry.arguments?.getString("url")?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
                
                PlaylistChannelsScreen(
                    playlistTitle = title,
                    playlistUrl = url,
                    viewModel = playlistChannelsViewModel,
                    mainViewModel = mainViewModel,
                    navController = navController
                )
            }
            composable(Routes.ADMIN_CONVERTER) {
                com.streambd.iptv.ui.screens.admin.AdminConverterScreen(navController = navController)
            }
        }
    }
}
