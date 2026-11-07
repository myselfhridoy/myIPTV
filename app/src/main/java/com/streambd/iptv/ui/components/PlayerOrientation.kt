package com.streambd.iptv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Fullscreen button support for the portrait player.
 *
 * Tapping the button forces the requested orientation. If we left it forced
 * forever, rotating the phone would no longer work. If we released it at once,
 * the system would rotate straight back (the phone is still held upright).
 * So the lock is kept only until the phone is PHYSICALLY in the requested
 * orientation, then released (UNSPECIFIED) so the sensor takes over — the same
 * way video apps behave.
 */
@Stable
class PlayerOrientationController(context: Context) {
    private val activity: Activity? = context.findActivity()
    private var target: Int? = null

    private val listener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(angle: Int) {
            val wanted = target ?: return
            if (angle == ORIENTATION_UNKNOWN) return
            val physicallyLandscape = angle in 60..120 || angle in 240..300
            val physicallyPortrait = angle <= 30 || angle >= 330 || angle in 150..210
            val reached = when (wanted) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> physicallyPortrait
                else -> physicallyLandscape
            }
            if (reached) release()
        }
    }

    /** true → fullscreen (landscape), false → back to portrait. */
    fun setFullscreen(fullscreen: Boolean) {
        val act = activity ?: return
        val wanted = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        target = wanted
        act.requestedOrientation = wanted
        if (listener.canDetectOrientation()) listener.enable()
    }

    /** Hand orientation back to the system (auto-rotate setting decides). */
    fun release() {
        target = null
        listener.disable()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    internal fun stop() {
        target = null
        listener.disable()
    }
}

@Composable
fun rememberPlayerOrientationController(): PlayerOrientationController {
    val context = LocalContext.current
    val controller = remember(context) { PlayerOrientationController(context) }
    DisposableEffect(controller) { onDispose { controller.stop() } }
    return controller
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
