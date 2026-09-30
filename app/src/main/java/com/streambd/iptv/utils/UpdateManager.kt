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
            // Primary: GitHub API (uses the SAFE client — GitHub has valid certs).
            tryPrimaryApi(currentVersion)?.let { return@withContext it }
            // Fallback: HTML redirect sniffing (rate-limited API, offline cache, etc.).
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

    /**
     * Semantic version compare.
     * Accepts formats like "v4.0", "4.0.1", "4.0.0-beta2".
     * Returns true iff remote > local.
     */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        fun parse(v: String): List<Int> =
            v.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-')
                .split('.')
                .mapNotNull { it.toIntOrNull() }

        val r = parse(remote)
        val l = parse(local)
        if (r.isEmpty()) return false
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
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

        // NOT_EXPORTED: nothing outside our app should be able to trigger this receiver.
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
