package com.myselfhridoy.streambdplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.myselfhridoy.streambdplayer.ui.navigation.AppNavigation
import com.myselfhridoy.streambdplayer.ui.theme.StreamBDPlayerTheme
import com.myselfhridoy.streambdplayer.ui.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreamBDPlayerTheme {
                AppNavigation(viewModel)
            }
        }
    }
}
