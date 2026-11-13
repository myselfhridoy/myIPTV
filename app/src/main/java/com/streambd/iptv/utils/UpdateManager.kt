package com.streambd.iptv.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    val browser_download_url: String
)

object UpdateManager {

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/myselfhridoy/StreamBD-IPTV-Release/releases/latest"
    private const val LATEST_URL =
        "https://github.com/myselfhridoy/StreamBD-IPTV-Release/releases/latest"

    private val gson = Gson()

    suspend fun checkForUpdate(currentVersion: String): GitHubRelease? =
        withContext(Dispatchers.IO) {
            tryPrimaryApi(currentVersion)?.let { return@withContext it }
            tryRedirectFallback(currentVersion)
        }

    private fun tryPrimaryApi(currentVersion: String): GitHubRelease? {
        return try {
            val req = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header(
                    "User-Agent",
                    "StreamBD-IPTV/${currentVersion} (+https://github.com/myselfhridoy)"
                )
                .build()
            UnsafeOkHttpClient.safe.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string() ?: return null
                val release = gson.fromJson(body, GitHubRelease::class.java) ?: return null
                if (isNewerVersion(release.tag_name, currentVersion)) release else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun tryRedirectFallback(currentVersion: String): GitHubRelease? {
        return try {
            val req = Request.Builder()
                .url(LATEST_URL)
                .header("User-Agent", "StreamBD-IPTV/${currentVersion}")
                .build()
            UnsafeOkHttpClient.safe.newCall(req).execute().use { res ->
                val finalUrl = res.request.url.toString()
                val tag = finalUrl.substringAfter("/tag/", "").trim()
                if (tag.isEmpty()) return null
                if (!isNewerVersion(tag, currentVersion)) return null
                GitHubRelease(
                    tag_name = tag,
                    name = "StreamBD IPTV $tag",
                    body = "A new version ($tag) is available. Download for the latest fixes and performance improvements.",
                    assets = listOf(
                        GitHubAsset(
                            name = "app-release.apk",
                            browser_download_url =
                                "https://github.com/myselfhridoy/StreamBD-IPTV-Release/releases/download/$tag/app-release.apk"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SemVer (semver.org 2.0.0) — FIX #3
    //
    // OLD code stripped everything after "-" before comparing, so:
    //     5.0.0-beta  →  [5, 0, 0]
    //     5.0.0       →  [5, 0, 0]   ← considered EQUAL
    //
    // That meant a "beta → stable" upgrade would never trigger, because
    // the version comparison returned false for both directions.
    //
    // NEW code follows semver.org 2.0.0 exactly:
    //   • Parse "MAJOR.MINOR.PATCH[-PRERELEASE]" (optional leading "v"/"V")
    //   • Missing minor / patch = 0 (so "5" == "5.0.0")
    //   • Compare major → minor → patch numerically
    //   • Then:
    //       – stable > prerelease  (5.0.0 > 5.0.0-beta)
    //       – both prerelease: compare identifiers dot-by-dot:
    //           · numeric < non-numeric  (1 < alpha)
    //           · numeric → numeric compare
    //           · non-numeric → lexicographic (ASCII)
    //           · shorter prerelease < longer if all prefix identifiers equal
    //
    // Examples verified:
    //     1.0.0-alpha  < 1.0.0-alpha.1
    //     1.0.0-alpha.1 < 1.0.0-alpha.beta
    //     1.0.0-beta.2 < 1.0.0-beta.11
    //     1.0.0-rc.1   < 1.0.0
    // ═══════════════════════════════════════════════════════════════════
    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        /** null = stable; non-null list = prerelease identifiers */
        val prerelease: List<String>?
    ) : Comparable<SemVer> {

        override fun compareTo(other: SemVer): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)

            val a = prerelease
            val b = other.prerelease
            // No prerelease = stable > any prerelease
            if (a == null && b == null) return 0
            if (a == null) return 1
            if (b == null) return -1

            val n = maxOf(a.size, b.size)
            for (i in 0 until n) {
                val ai = a.getOrNull(i) ?: return -1 // shorter prerelease < longer
                val bi = b.getOrNull(i) ?: return 1
                val aNum = ai.toIntOrNull()
                val bNum = bi.toIntOrNull()

                if (aNum != null && bNum != null) {
                    if (aNum != bNum) return aNum.compareTo(bNum)
                } else if (aNum != null) {
                    return -1 // numeric < non-numeric
                } else if (bNum != null) {
                    return 1 // non-numeric > numeric
                } else {
                    val cmp = ai.compareTo(bi)
                    if (cmp != 0) return cmp
                }
            }
            return 0
        }

        companion object {
            fun parse(raw: String): SemVer? {
                val v = raw.trim().removePrefix("v").removePrefix("V")
                if (v.isEmpty()) return null

                val dashIdx = v.indexOf('-')
                val corePart = if (dashIdx >= 0) v.substring(0, dashIdx) else v
                val prePart = if (dashIdx >= 0) v.substring(dashIdx + 1) else null

                // Split on "+build" if present (SemVer build metadata is ignored)
                val coreClean = corePart.substringBefore('+')

                val nums = coreClean.split('.').mapNotNull { it.toIntOrNull() }
                if (nums.isEmpty()) return null

                val major = nums.getOrElse(0) { 0 }
                val minor = nums.getOrElse(1) { 0 }
                val patch = nums.getOrElse(2) { 0 }

                val prerelease = prePart
                    ?.substringBefore('+')
                    ?.split('.')
                    ?.filter { it.isNotEmpty() }
                    ?.takeIf { it.isNotEmpty() }

                return SemVer(major, minor, patch, prerelease)
            }
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = SemVer.parse(remote) ?: return false
        val l = SemVer.parse(local) ?: return false
        return r > l
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Download + install
    // ─────────────────────────────────────────────────────────────────────────
    fun startDownload(context: Context, release: GitHubRelease) {
        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAsset == null) {
            Toast.makeText(context, "No APK found in this release.", Toast.LENGTH_LONG).show()
            return
        }

        val request = DownloadManager.Request(Uri.parse(apkAsset.browser_download_url))
            .setTitle("StreamBD IPTV Update")
            .setDescription("Downloading ${release.name}…")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "StreamBD-IPTV-Update.apk"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != downloadId) return
                try {
                    installApk(ctx, dm, downloadId)
                } finally {
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        Toast.makeText(context, "Downloading update in background…", Toast.LENGTH_SHORT).show()
    }

    private fun installApk(context: Context, dm: DownloadManager, downloadId: Long) {
        try {
            val uri = dm.getUriForDownloadedFile(downloadId) ?: return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "Auto-install failed. Install manually from the Downloads folder.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
