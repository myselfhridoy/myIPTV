package com.streambd.iptv.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Configures the host Activity for immersive playback.
 *
 * IMPORTANT: This no longer changes the Activity orientation.
 * The PlayerRevealContainer handles the visual "landscape" by rotating
 * the player content 90° while the Activity stays portrait.
 */
@Composable
fun PlayerWindowEffect(
    onPipChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity

        // PiP listener
        val pipObserver = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            onPipChanged(info.isInPictureInPictureMode)
        }
        activity?.addOnPictureInPictureModeChangedListener(pipObserver)

        val window = activity?.window
        val originalBackground = window?.decorView?.background

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())

            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }

        // ⚠️ NO requestedOrientation call. Activity stays portrait.

        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(pipObserver)

            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.setBackgroundDrawable(originalBackground)

                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())

                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
    }
}
