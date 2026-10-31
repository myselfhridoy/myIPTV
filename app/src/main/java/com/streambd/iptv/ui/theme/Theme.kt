package com.streambd.iptv.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.streambd.iptv.ui.adaptive.FormFactor
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile
import com.streambd.iptv.ui.adaptive.rememberDeviceProfile

private val AppColorScheme = darkColorScheme(
    primary                = PrimeBlue,
    onPrimary              = PrimeTextPrimary,
    primaryContainer       = PrimeBlueDark,
    onPrimaryContainer     = PrimeTextPrimary,

    secondary              = PrimeCyan,
    onSecondary            = PrimeBackground,
    secondaryContainer     = PrimeSurfaceHighest,
    onSecondaryContainer   = PrimeTextPrimary,

    tertiary               = PrimeGold,
    onTertiary             = PrimeBackground,

    background             = PrimeBackground,
    onBackground           = PrimeTextPrimary,

    surface                = PrimeSurface,
    onSurface              = PrimeTextPrimary,
    surfaceVariant         = PrimeSurfaceHigh,
    onSurfaceVariant       = PrimeTextSecondary,
    surfaceTint            = PrimeBlue,

    outline                = PrimeOutline,
    outlineVariant         = PrimeSurfaceHighest,

    error                  = PrimeError,
    onError                = PrimeTextPrimary,

    inverseSurface         = PrimeTextPrimary,
    inverseOnSurface       = PrimeBackground,
    scrim                  = PrimeBackground
)

/** Finds the hosting Activity even when the Context is wrapped. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun StreamBDPlayerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    // One DeviceProfile for the whole app; spacing tokens follow the form factor.
    val profile = rememberDeviceProfile()
    val dimens = when (profile.formFactor) {
        FormFactor.Tv -> TvDimens
        FormFactor.Tablet -> TabletDimens
        FormFactor.Phone -> MobileDimens
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = AppColorScheme.background.toArgb()
                window.navigationBarColor = AppColorScheme.background.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalDeviceProfile provides profile,
        LocalDimens provides dimens
    ) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
