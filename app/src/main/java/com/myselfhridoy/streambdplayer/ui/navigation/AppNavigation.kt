package com.myselfhridoy.streambdplayer.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myselfhridoy.streambdplayer.ui.screens.LiveCategoryChannelsScreen
import com.myselfhridoy.streambdplayer.ui.screens.LivePlayerScreen
import com.myselfhridoy.streambdplayer.ui.screens.MainScreen
import com.myselfhridoy.streambdplayer.ui.screens.TmdbDetailsScreen
import com.myselfhridoy.streambdplayer.ui.screens.VodPlayerScreen
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel

object Routes {
    const val MAIN = "main"
    const val LIVE_CATEGORY_CHANNELS = "live_category_channels"
    const val LIVE_PLAYER = "live_player"
    const val TMDB_DETAILS = "tmdb_details"
    const val VOD_PLAYER = "vod_player"
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AppNavigation(mainViewModel: MainViewModel, tmdbViewModel: TmdbViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Global Player Instance
    val trackSelector = remember { DefaultTrackSelector(context) }
    val globalExoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            globalExoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {
                MainScreen(mainViewModel = mainViewModel, tmdbViewModel = tmdbViewModel, navController = navController)
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
            composable(Routes.VOD_PLAYER) {
                val stream by mainViewModel.selectedStream.collectAsState()
                val media by tmdbViewModel.selectedMedia.collectAsState()
                if (stream != null) {
                    VodPlayerScreen(
                        videoUrl = stream!!.url,
                        videoTitle = media?.displayTitle ?: "VOD Stream",
                        navController = navController
                    )
                }
            }
        }
    }
}
