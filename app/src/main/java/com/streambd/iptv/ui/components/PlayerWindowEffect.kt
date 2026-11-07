package com.streambd.iptv.ui.components

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
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
    /**
     * false (default) = the classic behaviour, unchanged: force landscape and
     * hide the system bars.
     * true = FLEXIBLE (phones with the portrait player): the screen may stay in
     * portrait; system bars are hidden only while in landscape (fullscreen).
     */
    flexible: Boolean = false,
    onPipChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Classic mode hides the bars right away; flexible mode does it
            // per orientation in the effect below.
            if (!flexible) applyImmersive(window, immersive = true)
        }

        if (flexible) {
            // Let the phone rotate (auto-rotate setting decides).
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            // Request LANDSCAPE — Activity rotates, status bar becomes landscape.
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

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

    // FLEXIBLE mode: bars follow the orientation (hidden only in landscape).
    if (flexible) {
        DisposableEffect(isLandscape) {
            val window = (context as? ComponentActivity)?.window
            if (window != null) applyImmersive(window, immersive = isLandscape)
            onDispose { }
        }
    }
}

/**
 * Immersive = no system bars, content under the cutout (fullscreen video).
 * Not immersive = normal bars, content below them (portrait player).
 */
private fun applyImmersive(window: android.view.Window, immersive: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (immersive) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
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

