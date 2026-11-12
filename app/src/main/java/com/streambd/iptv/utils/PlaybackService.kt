package com.streambd.iptv.utils

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.streambd.iptv.data.api.DecryptionInterceptor
import okhttp3.OkHttpClient

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Build the OkHttpClient with generic network interceptors
        val okHttpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
            .addInterceptor(DecryptionInterceptor())
            .build()

        // 2. Custom MediaSource Factory to extract HTTP Headers from MediaItem
        val mediaSourceFactory = object : MediaSource.Factory {
            override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider
            ) = this

            override fun setLoadErrorHandlingPolicy(
                loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
            ) = this

            override fun createMediaSource(mediaItem: androidx.media3.common.MediaItem): MediaSource {
                val headers = mutableMapOf<String, String>()
                mediaItem.requestMetadata.extras?.let { bundle ->
                    bundle.keySet().forEach { key ->
                        bundle.getString(key)?.let { headers[key] = it }
                    }
                }

                // Inject headers for this specific stream
                val customOkHttp = okHttpClient.newBuilder().apply {
                    addNetworkInterceptor { chain ->
                        val req = chain.request().newBuilder().apply {
                            headers.forEach { (k, v) -> header(k, v) }
                        }.build()
                        chain.proceed(req)
                    }
                }.build()

                val httpDataSourceFactory = OkHttpDataSource.Factory(customOkHttp)
                    .setDefaultRequestProperties(headers)
                val dataSourceFactory = DefaultDataSource.Factory(this@PlaybackService, httpDataSourceFactory)

                val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                    .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

                val factory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                return factory.createMediaSource(mediaItem)
            }
        }

        // 3. Build ExoPlayer
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true // Handle audio focus
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // 4. Build MediaSession
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
