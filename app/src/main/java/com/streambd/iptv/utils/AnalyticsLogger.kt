package com.streambd.iptv.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Centralised Firebase Analytics logger for user-behaviour events.
 *
 * Design goals:
 *   • One-line caller API — no Bundle boilerplate at call sites.
 *   • Never crashes — every call is wrapped in runCatching.
 *   • Never blocks — FirebaseAnalytics.logEvent() is already async.
 *   • String truncation is automatic (Firebase limits: 100 chars/value,
 *     40 chars/name).
 *
 * Companion to PlaybackLogger (which handles error/crash reporting).
 * Behaviour stats live here; error stats live in PlaybackLogger.
 */
object AnalyticsLogger {

    private const val TAG = "AnalyticsLogger"

    // ─────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────

    private fun fa(context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context.applicationContext)

    private fun String?.trimmed(max: Int): String = this?.take(max) ?: ""

    private fun safeHost(url: String?): String = try {
        url?.let { Uri.parse(it).host } ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Live TV channel played
    // ─────────────────────────────────────────────────────────────
    /**
     * Fires every time a channel becomes the active playback target.
     * Called from MainViewModel.selectChannel(), so it covers:
     *   • LiveTvScreen card tap
     *   • LiveCategoryChannelsScreen card tap
     *   • LiveEventsScreen match tap
     *   • PlaylistChannelsScreen card tap
     *   • Player Up/Down channel surfing
     *   • Number-pad tuning
     *   • Saved-channel restore
     */
    fun logChannelPlayed(
        context: Context,
        name: String?,
        group: String?,
        url: String?
    ) {
        runCatching {
            fa(context).logEvent("channel_played", Bundle().apply {
                putString("name", name.trimmed(60))
                putString("group", group.trimmed(60))
                putString("host", safeHost(url))
            })
        }.onFailure { Log.w(TAG, "logChannelPlayed failed", it) }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. VOD playback started
    // ─────────────────────────────────────────────────────────────
    /**
     * Fires when playback actually starts in the VOD player.
     * Covers both TMDB (movie/TV) and Stream screen sources.
     */
    fun logVodPlayed(
        context: Context,
        title: String?,
        type: String?,        // "movie" | "tv" | "stream"
        provider: String? = null,
        season: Int? = null,
        episode: Int? = null
    ) {
        runCatching {
            fa(context).logEvent("vod_played", Bundle().apply {
                putString("title", title.trimmed(60))
                putString("type", type.trimmed(20))
                provider?.let { putString("provider", it.trimmed(40)) }
                season?.let { putInt("season", it) }
                episode?.let { putInt("episode", it) }
            })
        }.onFailure { Log.w(TAG, "logVodPlayed failed", it) }
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Search performed
    // ─────────────────────────────────────────────────────────────
    /**
     * Fires after the 300ms debounce in TmdbViewModel.search(),
     * so a real search fires exactly one event.
     */
    fun logSearchPerformed(
        context: Context,
        query: String?,
        resultCount: Int
    ) {
        if (query.isNullOrBlank()) return
        runCatching {
            fa(context).logEvent("search_performed", Bundle().apply {
                putString("query", query.trimmed(60))
                putInt("result_count", resultCount)
            })
        }.onFailure { Log.w(TAG, "logSearchPerformed failed", it) }
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Feature used (tab / drawer navigation)
    // ─────────────────────────────────────────────────────────────
    /**
     * Call on bottom-nav (mobile) or drawer (TV) item taps.
     *   feature examples: "live_tv", "events", "media", "stream",
     *                     "playlists", "check_update", "more"
     */
    fun logFeatureUsed(context: Context, feature: String) {
        runCatching {
            fa(context).logEvent("feature_used", Bundle().apply {
                putString("feature", feature.trimmed(40))
            })
        }.onFailure { Log.w(TAG, "logFeatureUsed failed", it) }
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Playlist added
    // ─────────────────────────────────────────────────────────────
    fun logPlaylistAdded(
        context: Context,
        title: String?,
        type: String? = "m3u_url"   // "m3u_url" | "m3u_file" | "xtream"
    ) {
        runCatching {
            fa(context).logEvent("playlist_added", Bundle().apply {
                putString("title", title.trimmed(60))
                putString("type", type.trimmed(20))
            })
        }.onFailure { Log.w(TAG, "logPlaylistAdded failed", it) }
    }

    // ─────────────────────────────────────────────────────────────
    // 6. Settings changed (TV mode, etc.)
    // ─────────────────────────────────────────────────────────────
    fun logSettingsChanged(context: Context, key: String, value: String) {
        runCatching {
            fa(context).logEvent("settings_changed", Bundle().apply {
                putString("key", key.trimmed(40))
                putString("value", value.trimmed(40))
            })
        }.onFailure { Log.w(TAG, "logSettingsChanged failed", it) }
    }
}
