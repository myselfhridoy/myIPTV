package com.streambd.iptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
 * the three values were plain `var`s inside the screen function, the whole
 * player screen recomposed twice a second.
 */
@Stable
class PlayerProgress {
    var position by mutableLongStateOf(0L)
        private set
    var buffered by mutableLongStateOf(0L)
        private set
    /** 0 when unknown (live stream) — `Player.duration` is clamped like before. */
    var duration by mutableLongStateOf(0L)
        private set

    val isLive: Boolean get() = duration <= 0L

    internal fun update(player: Player) {
        position = player.currentPosition
        buffered = player.bufferedPosition
        duration = player.duration.coerceAtLeast(0L)
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
