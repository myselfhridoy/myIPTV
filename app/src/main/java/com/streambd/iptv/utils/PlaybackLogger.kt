package com.streambd.iptv.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.PlaybackException
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralised Firebase logging for playback / resolution errors.
 *
 * Two destinations:
 *   • Crashlytics — non-fatal exception + custom keys (per-error detail)
 *   • Firebase Analytics — event with params (aggregate stats, trends)
 *
 * Throttling: at most [MAX_REPORTS_PER_CHANNEL] Crashlytics reports per
 * channel per app session. Analytics always fires (cheap + aggregated).
 *
 * Privacy: only the host portion of the URL is stored. Query strings
 * (which often hold tokens) are never sent.
 */
object PlaybackLogger {

    private const val TAG = "PlaybackLogger"
    private const val MAX_REPORTS_PER_CHANNEL = 2

    private val liveReportCounts = ConcurrentHashMap<String, Int>()
    private val vodReportCounts = ConcurrentHashMap<String, Int>()

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private fun hostOf(url: String?): String = try {
        if (url.isNullOrBlank()) "unknown"
        else Uri.parse(url).host ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    // ─────────────────────────────────────────────────────────────────
    // Live TV playback error
    // ─────────────────────────────────────────────────────────────────

    fun logLivePlaybackError(
        context: Context,
        channelName: String,
        channelGroup: String,
        channelUrl: String,
        sourceUrl: String,
        sourcesCount: Int,
        error: PlaybackException,
        isHlsFormatError: Boolean
    ) {
        val key = "$channelName|${hostOf(sourceUrl)}"
        val count = (liveReportCounts[key] ?: 0) + 1
        liveReportCounts[key] = count
        val shouldSendToCrashlytics = count <= MAX_REPORTS_PER_CHANNEL

        try {
            // ── Analytics: always fire (aggregate) ──
            FirebaseAnalytics.getInstance(context).logEvent(
                "live_playback_error",
                Bundle().apply {
                    putString("channel", channelName.take(50))
                    putString("group", channelGroup.take(50))
                    putString("host", hostOf(sourceUrl).take(60))
                    putString("error_code", error.errorCodeName.take(60))
                    putString("is_hls_format", isHlsFormatError.toString())
                    putInt("sources_count", sourcesCount)
                }
            )

            if (!shouldSendToCrashlytics) return

            // ── Crashlytics: non-fatal + custom keys ──
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("kind", "live")
            crashlytics.setCustomKey("channel_name", channelName.take(60))
            crashlytics.setCustomKey("channel_group", channelGroup.take(60))
            crashlytics.setCustomKey("channel_host", hostOf(channelUrl).take(60))
            crashlytics.setCustomKey("source_host", hostOf(sourceUrl).take(60))
            crashlytics.setCustomKey("sources_count", sourcesCount)
            crashlytics.setCustomKey("error_code", error.errorCodeName.take(60))
            crashlytics.setCustomKey("is_hls_format_error", isHlsFormatError)
            crashlytics.log("Live playback failed: $channelName @ ${hostOf(sourceUrl)}")
            crashlytics.recordException(error)
        } catch (e: Exception) {
            Log.w(TAG, "logLivePlaybackError failed", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // VOD / Network-Stream playback error (or resolution failure with error=null)
    // ─────────────────────────────────────────────────────────────────

    fun logVodPlaybackError(
        context: Context,
        title: String?,
        streamProvider: String,
        streamQuality: String,
        streamUrl: String,
        error: PlaybackException?
    ) {
        val key = "$streamProvider|${hostOf(streamUrl)}"
        val count = (vodReportCounts[key] ?: 0) + 1
        vodReportCounts[key] = count
        val shouldSendToCrashlytics = count <= MAX_REPORTS_PER_CHANNEL

        try {
            // ── Analytics ──
            FirebaseAnalytics.getInstance(context).logEvent(
                "vod_playback_error",
                Bundle().apply {
                    putString("title", title?.take(60) ?: "unknown")
                    putString("provider", streamProvider.take(40))
                    putString("quality", streamQuality.take(20))
                    putString("host", hostOf(streamUrl).take(60))
                    putString(
                        "error_code",
                        error?.errorCodeName?.take(60) ?: "resolve_failed"
                    )
                }
            )

            if (!shouldSendToCrashlytics) return

            // ── Crashlytics ──
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("kind", "vod")
            crashlytics.setCustomKey("title", title?.take(60) ?: "unknown")
            crashlytics.setCustomKey("provider", streamProvider.take(40))
            crashlytics.setCustomKey("quality", streamQuality.take(20))
            crashlytics.setCustomKey("stream_host", hostOf(streamUrl).take(60))
            crashlytics.log("VOD playback failed: $title via $streamProvider")

            if (error != null) {
                crashlytics.setCustomKey("error_code", error.errorCodeName.take(60))
                crashlytics.recordException(error)
            } else {
                crashlytics.recordException(
                    IllegalStateException("VOD resolution failed for $title")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "logVodPlaybackError failed", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Live TV: resolver returned null (no PlaybackException available)
    // ─────────────────────────────────────────────────────────────────

    fun logResolutionFailed(
        context: Context,
        channelName: String,
        sourceUrl: String,
        reason: String
    ) {
        try {
            FirebaseAnalytics.getInstance(context).logEvent(
                "resolution_failed",
                Bundle().apply {
                    putString("channel", channelName.take(50))
                    putString("host", hostOf(sourceUrl).take(60))
                    putString("reason", reason.take(40))
                }
            )

            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("kind", "resolution")
            crashlytics.setCustomKey("channel_name", channelName.take(60))
            crashlytics.setCustomKey("source_host", hostOf(sourceUrl).take(60))
            crashlytics.setCustomKey("reason", reason.take(40))
            crashlytics.log("Resolver failed: $channelName ($reason)")
            crashlytics.recordException(
                IllegalStateException("Resolver failed for $channelName: $reason")
            )
        } catch (e: Exception) {
            Log.w(TAG, "logResolutionFailed failed", e)
        }
    }
}
