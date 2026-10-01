package com.streambd.iptv.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.streambd.iptv.MainActivity
import com.streambd.iptv.R

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Prefer explicit notification payload; fall back to data payload.
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]

        if (!title.isNullOrBlank() || !body.isNullOrBlank()) {
            sendNotification(title, body)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Hook for sending the token to your own backend if needed.
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "streambd_default"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title ?: "StreamBD IPTV")
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0+ requires a channel before posting.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "StreamBD Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Updates and announcements from StreamBD IPTV"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Unique ID per notification so they don't overwrite each other.
        val notifId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        notificationManager.notify(notifId, notificationBuilder.build())
    }
}
