package com.streambd.iptv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URL
import kotlin.coroutines.resume

object WebViewSniffer {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(
        context: Context,
        url: String,
        headers: Map<String, String>? = null,
        userAgent: String? = null
    ): ResolvedToken? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(15000L) { // 15 seconds timeout
            suspendCancellableCoroutine { continuation ->
                var isResumed = false
                val webView = WebView(context)

                fun cleanup() {
                    try {
                        webView.stopLoading()
                        webView.destroy()
                    } catch (e: Exception) {}
                }

                continuation.invokeOnCancellation {
                    cleanup()
                }

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    if (userAgent != null) {
                        userAgentString = userAgent
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        // Inject JavaScript to auto-play or click play buttons to force stream loading
                        view?.evaluateJavascript(
                            """
                            setInterval(function() {
                                // Remove known ad layers
                                var ads = document.querySelectorAll('#first-click-ads-layer, .delayed-popup-ads-layer, [id*="ads"], [class*="ads"]');
                                ads.forEach(function(ad) { ad.remove(); });
                                
                                // Try playing any video element
                                var videos = document.getElementsByTagName('video');
                                if(videos.length > 0) { videos[0].play(); }
                                
                                // Click any play buttons
                                var playBtns = document.querySelectorAll('[class*="play"], [id*="play"]');
                                for(var i=0; i<playBtns.length; i++) { playBtns[i].click(); }
                                
                                // Click center of the screen to trigger any player
                                var clickEvent = document.createEvent('MouseEvents');
                                clickEvent.initMouseEvent('click', true, true, window, 0, 0, 0, window.innerWidth/2, window.innerHeight/2, false, false, false, false, 0, null);
                                document.elementFromPoint(window.innerWidth/2, window.innerHeight/2)?.dispatchEvent(clickEvent);
                            }, 1000);
                            """.trimIndent(), null
                        )
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                        val lowerUrl = reqUrl.lowercase()

                        // Check if it's a media stream
                        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd")) {
                            android.util.Log.d("WebViewSniffer", "Intercepted stream: ${'$'}reqUrl")
                            if (!isResumed) {
                                isResumed = true
                                val reqHeaders = request.requestHeaders ?: emptyMap()
                                val outHeaders = reqHeaders.toMutableMap()
                                
                                try {
                                    val u = URL(url)
                                    outHeaders["Referer"] = "${'$'}{u.protocol}://${'$'}{u.host}/"
                                    outHeaders["Origin"] = "${'$'}{u.protocol}://${'$'}{u.host}"
                                } catch (e: Exception) {}

                                Handler(Looper.getMainLooper()).post {
                                    cleanup()
                                    continuation.resume(ResolvedToken(url = reqUrl, headers = outHeaders))
                                }
                            }
                        } else {
                            // android.util.Log.d("WebViewSniffer", "Ignored: ${'$'}reqUrl")
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                val additionalHeaders = headers?.toMutableMap() ?: mutableMapOf()
                if (userAgent != null) additionalHeaders["User-Agent"] = userAgent
                
                webView.loadUrl(url, additionalHeaders)
            }
        }
    }
}
