package com.streambd.iptv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.provider.Settings
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
 *
 * Auto-rotate ON  → release after the phone reaches the target orientation,
 *                   so the sensor takes over naturally (Prime / Netflix style).
 *
 * Auto-rotate OFF → DO NOT release. `SCREEN_ORIENTATION_UNSPECIFIED` snaps
 *                   back to the user's preferred orientation (usually
 *                   portrait), which visibly kicks the user out of fullscreen
 *                   the moment they tilt the phone past the landscape
 *                   threshold while watching. In this case we keep the lock
 *                   and let the user exit via the fullscreen / back button.
 */
@Stable
class PlayerOrientationController(context: Context) {
    private val appContext: Context = context.applicationContext
    private val activity: Activity? = context.findActivity()
    private var target: Int? = null

    private val listener = object : OrientationEventListener(appContext) {
        override fun onOrientationChanged(angle: Int) {
            val wanted = target ?: return
            if (angle == ORIENTATION_UNKNOWN) return
            val physicallyLandscape = angle in 60..120 || angle in 240..300
            val physicallyPortrait = angle <= 30 || angle >= 330 || angle in 150..210
            val reached = when (wanted) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> physicallyPortrait
                else -> physicallyLandscape
            }
            if (reached) {
                if (isAutoRotateOn()) {
                    // Sensor will take over cleanly after this — the classic
                    // Prime / Netflix behaviour.
                    release()
                } else {
                    // Auto-rotate is OFF: UNSPECIFIED would snap the user
                    // back to their preferred orientation (usually portrait).
                    // Keep the lock, just stop calling the listener.
                    target = null
                    listener.disable()
                }
            }
        }
    }

    private fun isAutoRotateOn(): Boolean = try {
        Settings.System.getInt(
            appContext.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 1
    } catch (_: Throwable) {
        // If we can't read the setting, assume OFF — better to keep the
        // lock than risk snapping the user out of fullscreen.
        false
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
