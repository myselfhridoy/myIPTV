package com.streambd.iptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Position / buffer / duration of a player, kept in observable state.
 *
 * WHY a holder: the screen passes this object down and only the composable
 * that actually READS `position` (the seek bar) recomposes every tick. When
 * the values were plain `var`s inside the screen function, the whole player
 * screen recomposed twice a second.
 */
@Stable
class PlayerProgress {
    var position by mutableLongStateOf(0L)
        private set
    var buffered by mutableLongStateOf(0L)
        private set

    /**
     * 0 when unknown. For a live stream with a DVR / time-shift window this is
     * the length of that window (ExoPlayer reports it), so it can be > 0 even
     * though the stream is live.
     */
    var duration by mutableLongStateOf(0L)
        private set

    /**
     * True when ExoPlayer itself says the current item is a live stream
     * (`Player.isCurrentMediaItemLive`). This is the reliable "is it live?"
     * signal — it does NOT depend on how long the DVR window is.
     */
    var isLiveStream by mutableStateOf(false)
        private set

    /**
     * While true the polled position is NOT applied (the user is dragging the
     * seek bar and we show the finger position instead). Buffer and duration
     * keep updating.
     */
    var holdPosition: Boolean = false

    /** Duration unknown (kept for existing callers). Not the same as "live". */
    val isLive: Boolean get() = duration <= 0L

    /** Show [ms] right now (after a seek) instead of waiting for the next poll. */
    fun overridePosition(ms: Long) {
        position = ms
    }

    internal fun update(player: Player) {
        if (!holdPosition) position = player.currentPosition
        buffered = player.bufferedPosition
        duration = player.duration.coerceAtLeast(0L)
        isLiveStream = player.isCurrentMediaItemLive
    }
}

/**
 * Polls [player] every [intervalMs] — but ONLY while [active] is true
 * (e.g. while the controls are visible). Nothing runs when nobody looks.
 */
@Composable
fun rememberPlayerProgress(
    player: Player,
    active: Boolean,
    intervalMs: Long = 500L
): PlayerProgress {
    val progress = remember(player) { PlayerProgress() }
    LaunchedEffect(player, active) {
        if (!active) return@LaunchedEffect
        while (currentCoroutineContext().isActive) {
            progress.update(player)
            delay(intervalMs)
        }
    }
    return progress
}
