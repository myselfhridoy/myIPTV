package com.streambd.iptv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.streambd.iptv.ui.adaptive.LocalDeviceProfile

/**
 * True on Android TV / set-top boxes. Reads the app-wide DeviceProfile that
 * StreamBDPlayerTheme installs, so there is ONE answer to "is this a TV?".
 */
@Composable
fun rememberIsTv(): Boolean = LocalDeviceProfile.current.isTv

/**
 * AUTO FOCUS
 * ----------
 * Puts D-pad focus on this element once, right after it first appears.
 * Without this, a TV screen opens with *nothing* focused and the first
 * D-pad press is wasted (or lands somewhere random).
 *
 * • Runs on TV only by default — a phone never needs a focus ring.
 * • Waits one frame so the node is attached; failures are swallowed
 *   (e.g. the item scrolled out of a lazy list before the frame).
 * • Apply it BEFORE any clickable/focusable in the chain
 *   (components in this package already do that for you).
 *
 *     AppButton("Retry", onClick, Modifier.initialFocus())
 */
fun Modifier.initialFocus(
    enabled: Boolean = true,
    tvOnly: Boolean = true
): Modifier = composed {
    val isTv = rememberIsTv()
    val requester = remember { FocusRequester() }
    val shouldRequest = enabled && (isTv || !tvOnly)

    LaunchedEffect(shouldRequest) {
        if (shouldRequest) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    this.focusRequester(requester)
}

/**
 * Marks a row / column / grid as one focus group.
 *
 * (It used to also call `focusRestorer()`. On Compose 1.6 that modifier loses
 * focus on Android TV when the remembered item has left a lazy list — the
 * focus ring "disappeared" until OK was pressed. Plain directional search
 * works reliably, so restoring is no longer attempted. The function name is
 * kept so existing call sites need no change.)
 */
fun Modifier.restoreFocus(): Modifier = this.focusGroup()
