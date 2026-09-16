package com.streambd.iptv.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.TimeUnit

object YouTubeExtractor {

    private var isInitialized = false

    private val okHttpClient: OkHttpClient by lazy {
        UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Synchronized
    private fun initIfNeeded() {
        if (!isInitialized) {
            NewPipe.init(object : Downloader() {
                @Throws(IOException::class)
                override fun execute(request: Request): Response {
                    val httpMethod = request.httpMethod()
                    val url = request.url()
                    val headers = request.headers()
                    val dataToSend = request.dataToSend()

                    val reqBuilder = okhttp3.Request.Builder().url(url)
                    headers?.forEach { (name, values) ->
                        values?.forEach { valStr ->
                            reqBuilder.addHeader(name, valStr)
                        }
                    }

                    when (httpMethod.uppercase()) {
                        "POST" -> {
                            val body = dataToSend?.toRequestBody() ?: byteArrayOf().toRequestBody()
                            reqBuilder.post(body)
                        }
                        "HEAD" -> reqBuilder.head()
                        else -> reqBuilder.get()
                    }

                    val response = okHttpClient.newCall(reqBuilder.build()).execute()
                    val code = response.code
                    val message = response.message
                    val respHeaders = response.headers.toMultimap()
                    val bodyStr = response.body?.string() ?: ""

                    return Response(code, message, respHeaders, bodyStr, response.request.url.toString())
                }
            }, org.schabi.newpipe.extractor.localization.Localization.DEFAULT, org.schabi.newpipe.extractor.localization.ContentCountry.DEFAULT)
            isInitialized = true
        }
    }

    suspend fun extractStreamUrl(url: String): ResolvedToken? = withContext(Dispatchers.IO) {
        try {
            initIfNeeded()
            val service = ServiceList.YouTube
            var targetUrl = url
            if (!targetUrl.contains("watch?v=") && !targetUrl.contains("youtu.be/")) {
                try {
                    val req = okhttp3.Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    val html = okHttpClient.newCall(req).execute().body?.string() ?: ""
                    val canonicalMatch = Regex("canonical.*?href=[\"']https://www\\.youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+)").find(html)
                        ?: Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"").find(html)
                    canonicalMatch?.groupValues?.getOrNull(1)?.let { vid ->
                        targetUrl = "https://www.youtube.com/watch?v=$vid"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val streamInfo = StreamInfo.getInfo(service, targetUrl)

            // 1. Try HLS live stream URL first
            val hlsUrl = streamInfo.hlsUrl
            if (!hlsUrl.isNullOrEmpty()) {
                return@withContext ResolvedToken(
                    url = hlsUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    ),
                    mimeType = "application/x-mpegURL"
                )
            }

            // 2. Try DASH live stream URL
            val dashUrl = streamInfo.dashMpdUrl
            if (!dashUrl.isNullOrEmpty()) {
                return@withContext ResolvedToken(
                    url = dashUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    ),
                    mimeType = "application/dash+xml"
                )
            }

            // 3. Try video streams (HLS/DASH/Progressive)
            val videoStreams = streamInfo.videoStreams
            if (!videoStreams.isNullOrEmpty()) {
                val bestStream = videoStreams.maxByOrNull { it.resolution?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0 }
                    ?: videoStreams.first()
                val streamUrl = bestStream.content
                if (!streamUrl.isNullOrEmpty()) {
                    return@withContext ResolvedToken(
                        url = streamUrl,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                    )
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun isYouTubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    suspend fun extractPlaylistChannels(url: String): List<com.streambd.iptv.data.models.Channel> = withContext(Dispatchers.IO) {
        try {
            initIfNeeded()
            val service = ServiceList.YouTube
            val playlistInfo = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(service, url)
            val playlistName = playlistInfo.name ?: "YouTube Playlist"
            
            val channels = mutableListOf<com.streambd.iptv.data.models.Channel>()
            
            // Add first page items
            playlistInfo.relatedItems.forEach { item ->
                if (item is StreamInfoItem) {
                    channels.add(
                        com.streambd.iptv.data.models.Channel(
                            name = item.name,
                            url = item.url,
                            logo = item.thumbnails?.firstOrNull()?.url ?: "",
                            group = playlistName
                        )
                    )
                }
            }
            
            // Paginate through remaining pages
            var nextPage = playlistInfo.nextPage
            while (nextPage != null) {
                try {
                    val moreItems = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(service, url, nextPage)
                    moreItems.items.forEach { item ->
                        if (item is StreamInfoItem) {
                            channels.add(
                                com.streambd.iptv.data.models.Channel(
                                    name = item.name,
                                    url = item.url,
                                    logo = item.thumbnails?.firstOrNull()?.url ?: "",
                                    group = playlistName
                                )
                            )
                        }
                    }
                    nextPage = moreItems.nextPage
                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }
            
            channels
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun isYouTubePlaylistUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("youtube.com") || lower.contains("youtu.be")) && lower.contains("list=")
    }
}
