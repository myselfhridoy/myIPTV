package com.streambd.iptv.ui.screens.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * App-level side effects that the shell (phone / tablet / TV) triggers:
 * open Telegram, share the app, show the notice, exit, restart.
 *
 * Moved out of MainScreen's composable body so every layout calls the
 * SAME code. Behaviour is copied 1:1 from MainScreen.
 *
 *     val actions = rememberAppActions()
 *     actions.shareApp()
 */
@Stable
class AppActions(private val context: Context) {

    fun openTelegram() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL))
            )
        }
    }

    fun shareApp() {
        runCatching {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "StreamBD IPTV")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "StreamBD IPTV is the best app for streaming!\n" +
                        "Download it here: $RELEASES_URL"
                )
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share App via"))
        }
    }

    fun showNotice() {
        Toast.makeText(context, "No new notices at this time.", Toast.LENGTH_SHORT).show()
    }

    fun exitApp() {
        (context as? Activity)?.finishAffinity()
    }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
        )
        if (intent != null) context.startActivity(intent)
        (context as? Activity)?.finishAffinity()
    }

    private companion object {
        const val TELEGRAM_URL = "https://t.me/streamBD"
        const val RELEASES_URL = "https://github.com/myselfhridoy/myIPTV/releases/latest"
    }
}

@Composable
fun rememberAppActions(): AppActions {
    val context = LocalContext.current
    return remember(context) { AppActions(context) }
}
