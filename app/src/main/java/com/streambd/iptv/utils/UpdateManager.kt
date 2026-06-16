package com.streambd.iptv.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
    private const val GITHUB_API_URL = "https://api.github.com/repos/myselfhridoy/StreamBD-IPTV-Release/releases/latest"
    private val client = OkHttpClient()
    private val gson = Gson()
    
    suspend fun checkForUpdate(currentVersion: String): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
                
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val release = gson.fromJson(responseBody, GitHubRelease::class.java)
                    
                    val remoteVersion = release.tag_name.replace("v", "")
                    val localVersion = currentVersion.replace("v", "")
                    
                    // Simple compare. If they are different, we prompt update.
                    if (remoteVersion != localVersion && remoteVersion.isNotEmpty()) {
                        return@withContext release
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    fun startDownload(context: Context, release: GitHubRelease) {
        val apkAsset = release.assets.find { it.name.endsWith(".apk") }
        if (apkAsset == null) {
            android.widget.Toast.makeText(context, "No APK found in the release.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val url = apkAsset.browser_download_url
        
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("StreamBD IPTV Update")
            .setDescription("Downloading ${release.name}...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "StreamBD-IPTV-Update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(ctx, downloadManager, downloadId)
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (e: Exception) {}
                }
            }
        }
        
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        
        android.widget.Toast.makeText(context, "Downloading update in background...", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun installApk(context: Context, downloadManager: DownloadManager, downloadId: Long) {
        try {
            val uri = downloadManager.getUriForDownloadedFile(downloadId)
            if (uri == null) return
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Auto-install failed. Please install manually from Downloads folder.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
