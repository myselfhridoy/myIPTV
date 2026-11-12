package com.streambd.iptv.ui.components

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
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
 *
 * PiP SUPPORT (NEW):
 *   • [onPipChanged] fires with true on enter, false on exit.
 *   • When [onPipChanged] receives true, the caller MUST hide any non-video
 *     UI (overlays, dialogs, WebViews). We handle the activity-level window
 *     decoration here.
 *   • When the user taps X on the PiP window, the Activity is NOT finished —
 *     it is only backgrounded. Detection is done in the caller via onStop.
 */
@Composable
fun PlayerWindowEffect(
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
            if (!flexible) applyImmersive(window, immersive = true)
        }

        if (flexible) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
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

    if (flexible) {
        DisposableEffect(isLandscape) {
            val window = (context as? ComponentActivity)?.window
            if (window != null) applyImmersive(window, immersive = isLandscape)
            onDispose { }
        }
    }
}

/**
 * Helper called from the player screen when entering PiP.
 * Tells the OS to show ONLY this rect of the activity in the PiP window.
 *
 * The system's `setSourceRectHint` takes a screen-space Rect. We compute it
 * from the given View's location on screen.
 */
fun setPipSourceRectHint(activity: ComponentActivity, view: View?) {
    if (view == null) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val location = IntArray(2)
    view.getLocationOnScreen(location)
    val rect = Rect(
        location[0],
        location[1],
        location[0] + view.width,
        location[1] + view.height
    )

    val params = activity.pictureInPictureParamsBuilder()
        .setSourceRectHint(rect)
        .setAspectRatio(android.util.Rational(16, 9))
        .build()
    activity.setPictureInPictureParams(params)
}

/**
 * Convenience builder — returns a fresh PictureInPictureParams.Builder.
 * Callers can add actions before build().
 */
fun ComponentActivity.pictureInPictureParamsBuilder():
    android.app.PictureInPictureParams.Builder =
    android.app.PictureInPictureParams.Builder()

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

private fun androidx.core.app.PictureInPictureModeChangedInfo.isInPicturePipModeSafe(): Boolean =
    try {
        isInPictureInPictureMode
    } catch (_: Throwable) {
        false
    }
