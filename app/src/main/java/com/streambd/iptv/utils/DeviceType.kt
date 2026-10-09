package com.streambd.iptv.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log

/**
 * Robust TV detection for real Android TV, cheap Chinese TV boxes
 * (TX3 Mini, MXQ, H96, X88, Tanix, …), tablets, and phones.
 *
 * Cheap boxes typically fail the official `FEATURE_LEANBACK` check, so we
 * combine multiple signals and accept "any one matches". This is intentionally
 * generous — a false-positive (treating a big tablet as TV) is much less
 * harmful than a false-negative (a TV box stuck in mobile layout).
 *
 * CACHING
 * -------
 * The result is cached on first call. Device classification cannot change
 * during the process lifetime, and the detection chain touches
 * PackageManager, Configuration and several Build fields — all of which
 * are relatively expensive when called on every Compose recomposition.
 * The cache is guarded by double-checked locking so concurrent calls
 * from different threads (e.g. main + a coroutine) still run the probe
 * at most once.
 */
object DeviceType {

    private const val TAG = "DeviceType"

    @Volatile private var cachedIsTv: Boolean? = null

    fun isTv(context: Context): Boolean {
        cachedIsTv?.let { return it }
        synchronized(this) {
            cachedIsTv?.let { return it }
            val result = detectTv(context.applicationContext)
            cachedIsTv = result
            return result
        }
    }

    /**
     * The actual detection chain. Runs at most once per process thanks to
     * [isTv]'s cache. Uses `applicationContext` internally so no Activity
     * reference is retained across the process lifetime.
     */
    private fun detectTv(context: Context): Boolean {
        val pm = context.packageManager
        val config = context.resources.configuration

        // 1) UI mode — real Android TV & most boxes
        val uiMode = config.uiMode and Configuration.UI_MODE_TYPE_MASK
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
            Log.d(TAG, "isTv=true (uiMode=TELEVISION)")
            return true
        }

        // 2) Leanback — official Android TV
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            Log.d(TAG, "isTv=true (FEATURE_LEANBACK)")
            return true
        }

        // 3) Legacy television feature
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
            Log.d(TAG, "isTv=true (FEATURE_TELEVISION)")
            return true
        }

        // 4) No touchscreen → almost certainly a TV or set-top box
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            Log.d(TAG, "isTv=true (no touchscreen)")
            return true
        }

        // 5) Model / product / device-name / hardware heuristics.
        //    Build.HARDWARE is included because many cheap boxes ship an
        //    OEM-branded MODEL (e.g. "Mi Box", "NVIDIA Shield") while still
        //    exposing their real SoC in HARDWARE (e.g. "amlogic", "meson").
        //    TX3 Mini reports HARDWARE=amlogic, Platform=gxl.
        val model = (
            Build.MODEL + " " +
                Build.PRODUCT + " " +
                Build.DEVICE + " " +
                Build.BRAND + " " +
                Build.MANUFACTURER + " " +
                Build.HARDWARE
            ).lowercase()
        val boxPatterns = listOf(
            "tx3", "tx5", "tx6", "tx8", "tx9",          // Tanix boxes
            "mxq", "mx10", "m8s", "m8", "mxiii",         // MX / generic
            "h96", "x88", "x96", "t95", "a95",           // S905 / S912 generic
            "s905", "s912", "s922", "rk33", "rk32",      // SoC families
            "amlogic", "sunvell", "wetek", "beelink", "minix", "ugoos",
            "tv box", "androidtv", "android tv", "smarttv", "smart tv",
            "bravia", "firetv", "fire tv", "nexus player", "shield",
            "meson", "gxl", "g12"                        // Amlogic platform IDs
        )
        if (boxPatterns.any { model.contains(it) }) {
            Log.d(TAG, "isTv=true (model pattern matched in: $model)")
            return true
        }

        // 6) Landscape-only + no telephony (typical of cheap TV boxes).
        //    Real phones always have telephony; real tablets have rotation
        //    sensors. Together those two signals separate a box from both.
        val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val hasAccel = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)
        val hasGyro = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)
        val isLandscapeOnly = config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                !hasAccel && !hasGyro
        if (isLandscapeOnly && !hasTelephony) {
            Log.d(TAG, "isTv=true (landscape-only, no telephony, no sensors)")
            return true
        }

        Log.d(TAG, "isTv=false (all checks failed — model=$model)")
        return false
    }
}
