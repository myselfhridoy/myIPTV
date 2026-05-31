package com.myselfhridoy.streambdplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.myselfhridoy.streambdplayer.ui.screens.LiveCategoryChannelsScreen
import com.myselfhridoy.streambdplayer.ui.screens.LivePlayerScreen
import com.myselfhridoy.streambdplayer.ui.screens.MainScreen
import com.myselfhridoy.streambdplayer.ui.screens.VodBrowseScreen
import com.myselfhridoy.streambdplayer.ui.screens.VodPlayerScreen
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

object Routes {
    const val MAIN = "main"
    const val LIVE_CATEGORY_CHANNELS = "live_category_channels"
    const val LIVE_PLAYER = "live_player"
    const val VOD_BROWSE = "vod_browse"
    const val VOD_PLAYER = "vod_player"
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.LIVE_CATEGORY_CHANNELS) {
            LiveCategoryChannelsScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.LIVE_PLAYER) {
            LivePlayerScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.VOD_BROWSE) {
            VodBrowseScreen(viewModel = viewModel, navController = navController)
        }
        composable(Routes.VOD_PLAYER) {
            val stream by viewModel.selectedStream.collectAsState()
            val addon by viewModel.selectedAddon.collectAsState()
            if (stream != null) {
                VodPlayerScreen(
                    videoUrl = stream!!.url,
                    videoTitle = addon?.name ?: "VOD Stream",
                    navController = navController
                )
            }
        }
    }
}
