package com.streambd.iptv.ui.adaptive

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.streambd.iptv.utils.DeviceType

/** Material window-width buckets (same breakpoints as the M3 guidelines). */
enum class WindowWidthClass {
    Compact,   // < 600dp   — phones (portrait)
    Medium,    // 600–839dp — tablets, phones (landscape)
    Expanded;  // ≥ 840dp   — large tablets, TVs

    companion object {
        fun fromDp(widthDp: Int): WindowWidthClass = when {
            widthDp < 600 -> Compact
            widthDp < 840 -> Medium
            else -> Expanded
        }
    }
}

/** What kind of device we are on. Decided once per process (see DeviceType). */
enum class FormFactor { Phone, Tablet, Tv }

/** Which top-level navigation pattern fits the device. */
enum class NavigationStyle {
    BottomBar,  // phones
    Rail,       // tablets
    Drawer      // TV / D-pad devices
}

/**
 * Single source of truth for "what device & window is this?".
 *
 * Screens read this instead of calling DeviceType / LocalConfiguration
 * themselves:
 *
 *     val device = LocalDeviceProfile.current
 *     if (device.isTv) … else …
 *     when (device.navigationStyle) { … }
 *
 * [formFactor] never changes while the app runs; the window-related
 * fields follow rotation / split-screen.
 */
@Immutable
data class DeviceProfile(
    val formFactor: FormFactor,
    val widthClass: WindowWidthClass,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isLandscape: Boolean
) {
    val isTv: Boolean get() = formFactor == FormFactor.Tv
    val isTablet: Boolean get() = formFactor == FormFactor.Tablet
    val isPhone: Boolean get() = formFactor == FormFactor.Phone

    /** True when navigation is driven by a remote / D-pad (focus matters). */
    val usesDpad: Boolean get() = isTv

    val navigationStyle: NavigationStyle
        get() = when (formFactor) {
            FormFactor.Phone -> NavigationStyle.BottomBar
            FormFactor.Tablet -> NavigationStyle.Rail
            FormFactor.Tv -> NavigationStyle.Drawer
        }

    companion object {
        /** Used by previews / when no provider is installed. */
        val Default = DeviceProfile(
            formFactor = FormFactor.Phone,
            widthClass = WindowWidthClass.Compact,
            screenWidthDp = 360,
            screenHeightDp = 800,
            isLandscape = false
        )
    }
}

val LocalDeviceProfile = compositionLocalOf { DeviceProfile.Default }

/**
 * Builds the profile from DeviceType + the current Configuration.
 * Installed once, in StreamBDPlayerTheme.
 *
 * A phone held in landscape is still a Phone: the form factor uses
 * smallestScreenWidthDp, which does not change on rotation.
 */
@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isTv = remember(context) { DeviceType.isTv(context) }

    val width = config.screenWidthDp
    val height = config.screenHeightDp
    val smallest = config.smallestScreenWidthDp
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    return remember(isTv, width, height, smallest, landscape) {
        DeviceProfile(
            formFactor = when {
                isTv -> FormFactor.Tv
                smallest >= 600 -> FormFactor.Tablet
                else -> FormFactor.Phone
            },
            widthClass = WindowWidthClass.fromDp(width),
            screenWidthDp = width,
            screenHeightDp = height,
            isLandscape = landscape
        )
    }
}
