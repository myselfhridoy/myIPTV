package com.streambd.iptv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import com.streambd.iptv.utils.DeviceType

/**
 * True on Android TV / set-top boxes. Result is cached inside [DeviceType],
 * so calling this in any composable is cheap.
 */
@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember(context) { DeviceType.isTv(context) }
}

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
 * FOCUS RESTORE
 * -------------
 * Apply to a row/column/grid that holds many focusable items.
 * When the user leaves the list and comes back (D-pad up/down),
 * focus returns to the item they were on — not the first item.
 *
 *     LazyRow(modifier = Modifier.restoreFocus()) { ... }
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.restoreFocus(): Modifier = this
    .focusRestorer()
    .focusGroup()
