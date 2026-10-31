package com.streambd.iptv.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.streambd.iptv.utils.DeviceType

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

    // DeviceType caches its result, so this is cheap.
    val dimens = remember { if (DeviceType.isTv(context)) TvDimens else MobileDimens }

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

    CompositionLocalProvider(LocalDimens provides dimens) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
