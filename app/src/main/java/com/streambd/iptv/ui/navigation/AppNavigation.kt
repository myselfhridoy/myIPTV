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

    // ── Track selector ────────────────────────────────────────────────────
    // Plain default selector. We deliberately do NOT touch
    // TrackSelectionParameters.setAudioOffloadPreference here, because that
    // API only exists in media3 1.2.0+. Audio offload defaults to DISABLED
    // in media3 unless explicitly enabled, so leaving it alone keeps A/V
    // sync correction (via the Sonic audio processor) active.
    //
    // If you later upgrade to media3 1.2.0+ and want to explicitly force
    // offload off, add:
    //
    //   parameters = buildUponParameters()
    //       .setAudioOffloadPreference(C.AUDIO_OFFLOAD_PREFERENCE_DISABLED)
    //       .build()
    //
    // (requires importing androidx.media3.common.C).
    val trackSelector = remember { DefaultTrackSelector(context) }

    // ── LoadControl tuned for LIVE streaming ─────────────────────────────
    // Heap-scaled forward buffer. Fixed 45s values caused GC pressure on
    // 192–256 MB devices when the stream bitrate spiked.
    val loadControl = remember {
        val am = context.getSystemService(ActivityManager::class.java)
        val heapMb = am?.memoryClass ?: 256
        val lowRam = am?.isLowRamDevice == true || heapMb < 256

        val maxBufMs = when {
            heapMb < 128 -> 12_000
            heapMb < 256 -> 20_000
            heapMb < 512 -> 30_000
            else         -> 45_000
        }

        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs                 = */ if (lowRam) 4_000 else 8_000,
                /* maxBufferMs                 = */ maxBufMs,
                /* bufferForPlaybackMs         = */ 1_500,
                /* bufferForPlaybackAfterRebuf = */ 3_000
            )
            .setTargetBufferBytes(if (lowRam) 24 * 1024 * 1024 else C.LENGTH_UNSET)
            .setBackBuffer(if (lowRam) 5_000 else 15_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    // ── Renderers factory: hardware-first ────────────────────────────────
    //  • EXTENSION_RENDERER_MODE_ON (not PREFER): use platform decoders when
    //    available; FFmpeg only as fallback. PREFER forced software audio
    //    against hardware video — the classic A/V drift pattern.
    //  • setEnableDecoderFallback(true): if a hardware decoder chokes
    //    mid-stream, fall back instead of dying.
    //
    // NOTE: setEnableAudioOffload() was removed in media3 1.2.0; audio
    // offload is now controlled via TrackSelectionParameters. Since we're
    // on an older version and don't need it (default is disabled), we
    // simply don't touch it.
    val renderersFactory = remember {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
    }

    val audioAttributes = remember {
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }

    // ── Single global player for Live + VOD ──────────────────────────────
    val globalExoPlayer = remember {
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setUseLazyPreparation(true)
            .build()
            .apply {
                // setRepeatMode() is a method on the Player interface.
                // Kotlin property synthesis (repeatMode = ...) does not
                // happen here because ExoPlayer is a sub-interface.
                setRepeatMode(Player.REPEAT_MODE_OFF)
            }
    }

    DisposableEffect(Unit) {
        onDispose { globalExoPlayer.release() }
    }

    // ── Safety net: stop playback when leaving a player route via any path
    //    other than the in-screen back handler. ──
    DisposableEffect(navController) {
        val playerRoutes = setOf(Routes.LIVE_PLAYER, Routes.VOD_PLAYER)
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val arriving = destination.route
            // Guard on the ARRIVING destination only. route can briefly be
            // null during NavHost initialisation.
            if (arriving != null && arriving !in playerRoutes) {
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
