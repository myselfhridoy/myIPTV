package com.streambd.iptv.ui.components

import android.content.pm.ActivityInfo
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
 * Approach B: the Activity ITSELF rotates to landscape (status bar and insets
 * follow). The source screen is unmounted by the NavHost's `None` transition
 * *before* the rotation happens, so it never appears rotated.
 */
@Composable
fun PlayerWindowEffect(
    onPipChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity

        val pipObserver = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            onPipChanged(info.isInPicturePipModeSafe())
        }
        activity?.addOnPictureInPictureModeChangedListener(pipObserver)

        val originalOrientation: Int? = activity?.requestedOrientation
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

        // Request LANDSCAPE — Activity rotates, status bar becomes landscape.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(pipObserver)

            if (activity != null) {
                activity.requestedOrientation = originalOrientation
                    ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

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

/** Small safe accessor because the PiP API flag accessor changed across versions. */
private fun androidx.core.app.PictureInPictureModeChangedInfo.isInPicturePipModeSafe(): Boolean =
    try {
        isInPictureInPictureMode
    } catch (_: Throwable) {
        false
    }
