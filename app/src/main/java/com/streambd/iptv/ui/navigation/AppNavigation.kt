package com.streambd.iptv.ui.navigation

import android.app.ActivityManager
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

    // ── Track selector: default params, no forced preferences. ────────────
    // Forcing codec/bitrate preferences here historically caused more harm
    // than good (broke HEVC streams, broke multi-audio). Let ExoPlayer pick
    // the best track; we only step in when the user asks via the UI.
    val trackSelector = remember { DefaultTrackSelector(context) }

    // ── LoadControl tuned for LIVE streaming. ─────────────────────────────
    // The previous values (min 30s / max 300s / back buffer 60s) were VOD
    // values. For live, they fought the 20s target offset in
    // MediaItem.LiveConfiguration and gave A/V drift room to accumulate.
    //
    // Rule of thumb: minBufferMs <= targetOffsetMs, maxBufferMs <= 3×
    // targetOffsetMs. With the resolver now using a 10s target offset,
    // 8s / 30s is a safe and responsive window.
    val loadControl = remember {
        val am = context.getSystemService(ActivityManager::class.java)
        val lowRam = am?.isLowRamDevice == true || (am?.memoryClass ?: 256) < 256

        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs                = */ if (lowRam) 4_000 else 8_000,
                /* maxBufferMs                = */ if (lowRam) 20_000 else 45_000,
                /* bufferForPlaybackMs        = */ 1_500,
                /* bufferForPlaybackAfterRebuf= */ 3_000
            )
            .setTargetBufferBytes(
                if (lowRam) 24 * 1024 * 1024 else C.LENGTH_UNSET
            )
            // Live has no use for a long back buffer. Keeping it short frees
            // RAM for the forward buffer and reduces GC pauses.
            .setBackBuffer(/* backBufferDurationMs = */ if (lowRam) 5_000 else 15_000, true)
            // Time-based eviction keeps the live window stable regardless of
            // bitrate spikes.
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    // ── Renderers factory: hardware-first, sync-friendly. ─────────────────
    //  • EXTENSION_RENDERER_MODE_ON (not PREFER): use platform decoders when
    //    available; FFmpeg is only a fallback. PREFER forced software audio
    //    against hardware video, which is exactly the A/V drift pattern.
    //  • setEnableAudioOffload(false): audio offload bypasses ExoPlayer's
    //    Sonic audio processor, which is where speed/pitch correction for
    //    A/V sync lives. Offload ON = no auto-sync.
    //  • setEnableDecoderFallback(true): if a hardware decoder chokes mid-
    //    stream, fall back instead of dying.
    val renderersFactory = remember {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
            .setEnableAudioOffload(false)
    }

    val audioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    // ── Single global player for Live + VOD. ──────────────────────────────
    val globalExoPlayer = remember {
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Seamless: reuse the surface across media items instead of
            // re-creating it, which avoids a black flash on channel switch.
            .setUseLazyPreparation(true)
            .build()
            .apply {
                // Keep player state consistent even when it goes to the background.
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    DisposableEffect(Unit) {
        onDispose { globalExoPlayer.release() }
    }

    // ── Safety net: stop playback when leaving a player route via any path
    //    other than the in-screen back handler (deep link, programmatic
    //    navigation, config change). Prevents audio bleeding. ──
    DisposableEffect(navController) {
        val playerRoutes = setOf(Routes.LIVE_PLAYER, Routes.VOD_PLAYER)
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route !in playerRoutes) {
                if (globalExoPlayer.isPlaying ||
                    globalExoPlayer.playbackState != Player.STATE_IDLE
                ) {
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
                    onEnterPiP = { /* handled by PlayerWindowEffect */ }
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
                    ?.let { try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it } }
                    ?: ""

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
