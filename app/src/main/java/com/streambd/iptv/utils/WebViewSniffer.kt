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
                
                var windowManager: android.view.WindowManager? = null
                var layoutParams: android.view.WindowManager.LayoutParams? = null
                
                try {
                    windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                    layoutParams = android.view.WindowManager.LayoutParams(
                        1, 1,
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        else
                            android.view.WindowManager.LayoutParams.TYPE_PHONE,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        android.graphics.PixelFormat.TRANSPARENT
                    )
                    // If context is an Activity, we can attach to its Window instead of SYSTEM_ALERT_WINDOW
                    if (context is android.app.Activity) {
                        layoutParams.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
                    }
                    windowManager.addView(webView, layoutParams)
                } catch (e: Exception) {
                    // Fallback: If we don't have permission, try adding to Activity root view
                    try {
                        if (context is android.app.Activity) {
                            val rootView = context.findViewById<android.view.ViewGroup>(android.R.id.content)
                            rootView?.addView(webView, android.view.ViewGroup.LayoutParams(1, 1))
                        }
                    } catch (e2: Exception) {}
                }

                fun cleanup() {
                    try {
                        webView.stopLoading()
                        try {
                            if (webView.parent != null) {
                                (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                            } else {
                                windowManager?.removeView(webView)
                            }
                        } catch (e: Exception) {}
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

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // Prevent navigating away from the page due to ad clicks
                        return true
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
                            try {
                                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("WebViewSniffer: Intercepted stream: ${'$'}reqUrl")
                            } catch (e: Exception) {}

                            if (!isResumed) {
                                isResumed = true
                                val reqHeaders = request.requestHeaders ?: emptyMap()
                                val outHeaders = reqHeaders.toMutableMap()
                                
                                // Only add Referer/Origin if they are missing. Do not overwrite if the WebView correctly provided them!
                                val hasReferer = outHeaders.keys.any { it.equals("referer", ignoreCase = true) }
                                val hasOrigin = outHeaders.keys.any { it.equals("origin", ignoreCase = true) }

                                try {
                                    val u = URL(url)
                                    if (!hasReferer) {
                                        outHeaders["Referer"] = "${'$'}{u.protocol}://${'$'}{u.host}/"
                                    }
                                    if (!hasOrigin) {
                                        outHeaders["Origin"] = "${'$'}{u.protocol}://${'$'}{u.host}"
                                    }
                                } catch (e: Exception) {}

                                Handler(Looper.getMainLooper()).post {
                                    cleanup()
                                    continuation.resume(ResolvedToken(url = reqUrl, headers = outHeaders))
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                val additionalHeaders = headers?.toMutableMap() ?: mutableMapOf()
                if (userAgent != null) additionalHeaders["User-Agent"] = userAgent
                
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("WebViewSniffer: Loading URL: ${'$'}url")
                } catch (e: Exception) {}
                
                webView.loadUrl(url, additionalHeaders)
            }
        } ?: run {
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(Exception("WebViewSniffer Timeout after 15s for URL: ${'$'}url"))
            } catch (e: Exception) {}
            null
        }
    }
}
