package com.streambd.iptv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Central spacing / size tokens. Screens must use these instead of
 * hard-coded dp values, so mobile and TV scale from one place.
 *
 * Usage:  AppTheme.dimens.spaceMd
 */
@Immutable
data class Dimens(
    // spacing scale
    val spaceXxs: Dp = 2.dp,
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 12.dp,
    val spaceLg: Dp = 16.dp,
    val spaceXl: Dp = 24.dp,
    val spaceXxl: Dp = 32.dp,

    // layout
    val screenPaddingH: Dp = 16.dp,
    val screenPaddingV: Dp = 12.dp,
    val minTouchTarget: Dp = 48.dp,

    // cards (used by shared components in step 2)
    val channelCardWidth: Dp = 140.dp,
    val posterCardWidth: Dp = 120.dp,
    val cardCorner: Dp = 8.dp,

    // focus (TV)
    val focusBorderWidth: Dp = 2.dp,
    val focusScale: Float = 1.0f
)

val MobileDimens = Dimens()

val TvDimens = Dimens(
    spaceXs = 6.dp,
    spaceSm = 12.dp,
    spaceMd = 16.dp,
    spaceLg = 24.dp,
    spaceXl = 32.dp,
    spaceXxl = 48.dp,

    screenPaddingH = 48.dp,
    screenPaddingV = 24.dp,
    minTouchTarget = 56.dp,

    channelCardWidth = 220.dp,
    posterCardWidth = 180.dp,
    cardCorner = 10.dp,

    focusBorderWidth = 3.dp,
    focusScale = 1.06f
)

val LocalDimens = staticCompositionLocalOf { MobileDimens }

object AppTheme {
    val dimens: Dimens
        @Composable
        @ReadOnlyComposable
        get() = LocalDimens.current
}
