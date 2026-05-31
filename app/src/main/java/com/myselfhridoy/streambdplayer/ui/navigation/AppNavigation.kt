package com.myselfhridoy.streambdplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myselfhridoy.streambdplayer.ui.screens.LiveCategoryChannelsScreen
import com.myselfhridoy.streambdplayer.ui.screens.LivePlayerScreen
import com.myselfhridoy.streambdplayer.ui.screens.MainScreen
import com.myselfhridoy.streambdplayer.ui.screens.TmdbDetailsScreen
import com.myselfhridoy.streambdplayer.ui.screens.VodPlayerScreen
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import com.myselfhridoy.streambdplayer.ui.viewmodels.TmdbViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

object Routes {
    const val MAIN = "main"
    const val LIVE_CATEGORY_CHANNELS = "live_category_channels"
    const val LIVE_PLAYER = "live_player"
    const val TMDB_DETAILS = "tmdb_details"
    const val VOD_PLAYER = "vod_player"
}

@Composable
fun AppNavigation(mainViewModel: MainViewModel, tmdbViewModel: TmdbViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(mainViewModel = mainViewModel, tmdbViewModel = tmdbViewModel, navController = navController)
        }
        composable(Routes.LIVE_CATEGORY_CHANNELS) {
            LiveCategoryChannelsScreen(viewModel = mainViewModel, navController = navController)
        }
        composable(Routes.LIVE_PLAYER) {
            LivePlayerScreen(viewModel = mainViewModel, navController = navController)
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
