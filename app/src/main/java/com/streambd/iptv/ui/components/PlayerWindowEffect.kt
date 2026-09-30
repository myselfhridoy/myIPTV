package com.streambd.iptv.ui.components

import android.app.Activity
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
import androidx.appcompat.app.AppCompatDelegate

/**
 * Applies the standard "fullscreen player" window configuration on the host
 * Activity while this composable is in the composition tree:
 *
 *  - Requests landscape orientation (restores the original on dispose).
 *  - Hides system bars + applies a "swipe to show" gesture behaviour.
 *  - Sets a black window background and enables `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`
 *    (API 28+) so the video can extend into the notch.
 *  - Keeps the screen on for the duration of the player.
 *  - Hooks `onPictureInPictureModeChanged` and forwards the boolean to [onPipChanged].
 *
 *  Everything is restored in `onDispose` — including the Activity's original
 *  orientation and window background.
 *
 *  Extracted from LivePlayerScreen + VodPlayerScreen to remove duplication.
 */
@Composable
fun PlayerWindowEffect(
    onPipChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity

        // ── PiP listener ──
        val pipObserver = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            onPipChanged(info.isInPictureInPictureMode)
        }
        activity?.addOnPictureInPictureModeChangedListener(pipObserver)

        // ── Snapshot state to restore later ──
        val originalOrientation: Int? = activity?.requestedOrientation
        val window = activity?.window
        val originalBackground = window?.decorView?.background

        if (window != null) {
            // Draw under system bars + cutout.
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

        // Lock to landscape immediately.
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
