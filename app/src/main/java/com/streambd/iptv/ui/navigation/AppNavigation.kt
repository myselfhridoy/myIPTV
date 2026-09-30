package com.streambd.iptv.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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

    // ── Global player (single instance shared across Live + VOD players) ──
    val trackSelector = remember { DefaultTrackSelector(context) }

    val loadControl = remember {
        val am = context.getSystemService(android.app.ActivityManager::class.java)
        val lowRam = am.isLowRamDevice || am.memoryClass < 256

        androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000,
                if (lowRam) 90000 else 300000,
                500,
                1000
            )
            .setTargetBufferBytes(
                if (lowRam) 48 * 1024 * 1024
                else androidx.media3.common.C.LENGTH_UNSET
            )
            .setBackBuffer(if (lowRam) 20000 else 60000, true)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    }

    val renderersFactory = remember {
        androidx.media3.exoplayer.DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            )
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
        onDispose { globalExoPlayer.release() }
    }

    // ── Safety net: if the user leaves a player route by any means other
    //    than the in-screen Back handler (deep link, programmatic nav,
    //    config change…), stop playback so audio doesn't bleed into the
    //    next screen. ──
    DisposableEffect(navController) {
        val playerRoutes = setOf(Routes.LIVE_PLAYER, Routes.VOD_PLAYER)
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route !in playerRoutes) {
                if (globalExoPlayer.isPlaying || globalExoPlayer.playbackState !=
                    androidx.media3.common.Player.STATE_IDLE
                ) {
                    globalExoPlayer.stop()
                    globalExoPlayer.clearMediaItems()
                }
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
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
                LiveCategoryChannelsScreen(
                    viewModel = mainViewModel,
                    navController = navController
                )
            }

            composable(Routes.LIVE_PLAYER) {
                LivePlayerScreen(
                    viewModel = mainViewModel,
                    navController = navController,
                    exoPlayer = globalExoPlayer,
                    onEnterPiP = {
                        // Handled natively inside LivePlayerScreen via PlayerWindowEffect
                    }
                )
            }

            composable(Routes.TMDB_DETAILS) {
                TmdbDetailsScreen(
                    mainViewModel = mainViewModel,
                    tmdbViewModel = tmdbViewModel,
                    navController = navController
                )
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
                    exoPlayer = globalExoPlayer
                )
            }

            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    playlistViewModel = playlistViewModel,
                    navController = navController
                )
            }

            composable(Routes.PLAYLIST_CHANNELS) { backStackEntry ->
                val title = backStackEntry.arguments?.getString("title") ?: "Playlist"
                val url = backStackEntry.arguments?.getString("url")
                    ?.let {
                        try {
                            URLDecoder.decode(it, "UTF-8")
                        } catch (_: Exception) {
                            it
                        }
                    } ?: ""

                PlaylistChannelsScreen(
                    playlistTitle = title,
                    playlistUrl = url,
                    viewModel = playlistChannelsViewModel,
                    mainViewModel = mainViewModel,
                    navController = navController
                )
            }

            composable(Routes.ADMIN_CONVERTER) {
                com.streambd.iptv.ui.screens.admin.AdminConverterScreen(
                    navController = navController
                )
            }
        }
    }
}
