package com.streambd.iptv.utils

import android.content.Context

/**
 * Small user settings for the player.
 *
 * Portrait player (phones & tablets only — TV is never affected):
 *   ON  → a channel opens in portrait: video on top, channel list below;
 *         rotate the phone (or tap the fullscreen button) for fullscreen.
 *   OFF → the classic behaviour: always fullscreen landscape.
 */
object PlayerPreferences {
    private const val PREFS = "player_prefs"
    private const val KEY_PORTRAIT_PLAYER = "portrait_player_enabled"

    /** Default ON: it is what sports / streaming apps do and what users expect. */
    fun isPortraitPlayerEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PORTRAIT_PLAYER, true)

    fun setPortraitPlayerEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PORTRAIT_PLAYER, enabled)
            .apply()
    }
}
