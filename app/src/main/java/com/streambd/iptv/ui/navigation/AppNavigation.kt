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
import com.streambd.iptv.utils.DeviceType
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

    // ── Device profile ───────────────────────────────────────────────────
    // Single source of truth: the existing DeviceType helper. Anything
    // the UI layer (TvIconButton etc.) treats as a TV will also get the
    // TV-tuned player profile here, so behaviour stays consistent.
    val useTvProfile = remember { DeviceType.isTv(context) }

    // ── Track selector ────────────────────────────────────────────────────
    // Tunneling disabled on TV boxes. Amlogic's tunneled playback path is
    // partially implemented and freezes on many MPEG-TS streams; software
    // path is more predictable. Mobile keeps the media3 default.
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            if (useTvProfile) {
                parameters = buildUponParameters()
                    .setTunnelingEnabled(false)
                    .build()
            }
        }
    }

    // ── LoadControl ──────────────────────────────────────────────────────
    // On TV boxes we give the hardware video decoder more time to
    // initialize before playback starts. Cheap Amlogic S905 decoders
    // (TX3 Mini and friends) need ~2-3 s to come online; with the mobile
    // 1.5 s buffer the player starts audio while the video decoder is
    // still warming up, producing the "audio first, video later" symptom
    // the user reports only on that box. Mobile stays at 1.5 s because
    // phone decoders are ready in <300 ms and a longer buffer would just
    // add zapping latency.
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
                /* minBufferMs                 = */ if (lowRam) 5_000 else 8_000,
                /* maxBufferMs                 = */ maxBufMs,
                /* bufferForPlaybackMs         = */ if (useTvProfile) 3_000 else 1_500,
                /* bufferForPlaybackAfterRebuf = */ if (useTvProfile) 4_000 else 3_000
            )
            .setTargetBufferBytes(if (lowRam) 24 * 1024 * 1024 else C.LENGTH_UNSET)
            .setBackBuffer(if (lowRam) 5_000 else 15_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    // ── Renderers factory ────────────────────────────────────────────────
    // Hardware-first decoder selection everywhere. On TV boxes we also
    // route A/V sync speed corrections through AudioTrack HAL instead of
    // the Sonic software processor — this is THE critical fix for the
    // Amlogic audio-ahead bug.
    //
    // Why it matters: ExoPlayer keeps A/V aligned by nudging audio playback
    // speed between 0.97x and 1.03x. By default those nudges are applied
    // in the Sonic audio processor, an application-level component whose
    // latency on Amlogic is measured in hundreds of milliseconds. The
    // correction therefore lands too late to converge, and the initial
    // drift never recovers.
    //
    // AudioTrack.setPlaybackParams (API 23+) applies the same correction
    // inside the platform audio HAL, where it takes effect near-instantly.
    // On phones the change is skipped entirely to keep existing behaviour.
    val renderersFactory = remember {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
            .apply {
                if (useTvProfile) {
                    // Route A/V sync corrections through AudioTrack HAL.
                    setEnableAudioTrackPlaybackParams(true)

                    // Reduce audio underruns and dropped video frames on
                    // slower Amlogic / Rockchip decoders.
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
                // setRepeatMode() is a method on Player. Kotlin property
                // synthesis (repeatMode = ...) does not happen here because
                // ExoPlayer is a sub-interface.
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
