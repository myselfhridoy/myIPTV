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

    private class SnifferJSInterface(private val onKeyFound: (String) -> Unit) {
        @android.webkit.JavascriptInterface
        fun onClearKey(keyJson: String) {
            onKeyFound(keyJson)
        }
    }

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
                var foundMpdUrl: String? = null
                var foundMpdHeaders: Map<String, String>? = null
                var foundKeyJson: String? = null
                var mpdTimeoutRunnable: Runnable? = null
                val handler = Handler(Looper.getMainLooper())
                
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

                webView.addJavascriptInterface(SnifferJSInterface { keyJson ->
                    handler.post {
                        foundKeyJson = keyJson
                        if (!isResumed && foundMpdUrl != null) {
                            isResumed = true
                            cleanup()
                            val drmConfig = com.streambd.iptv.data.models.DrmConfig(type = "clearkey", licenseServer = keyJson)
                            continuation.resume(ResolvedToken(url = foundMpdUrl!!, headers = foundMpdHeaders, drm = drmConfig))
                            mpdTimeoutRunnable?.let { handler.removeCallbacks(it) }
                        }
                    }
                }, "AndroidSniffer")

                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    if (userAgent != null) {
                        userAgentString = userAgent
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view?.evaluateJavascript(
                            """
                            if (!window.hookedEME) {
                                window.hookedEME = true;
                                const originalUpdate = MediaKeySession.prototype.update;
                                MediaKeySession.prototype.update = function(response) {
                                    try {
                                        const text = new TextDecoder().decode(response);
                                        if (text.includes('"kty":"oct"')) {
                                            window.AndroidSniffer.onClearKey(text);
                                        }
                                    } catch(e) {}
                                    return originalUpdate.apply(this, arguments);
                                };
                            }
                            """.trimIndent(), null
                        )
                    }

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

                        val mediaExtensions = listOf(".m3u8", ".mpd", ".mp4", ".mkv", ".ts", ".avi", ".flv", ".webm")
                        val isMedia = mediaExtensions.any { lowerUrl.contains(it) }

                        if (isMedia) {
                            android.util.Log.d("WebViewSniffer", "Intercepted stream: ${'$'}reqUrl")
                            try {
                                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log("WebViewSniffer: Intercepted stream: ${'$'}reqUrl")
                            } catch (e: Exception) {}

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

                            handler.post {
                                if (isResumed) return@post
                                
                                if (lowerUrl.contains(".mpd")) {
                                    if (foundKeyJson != null) {
                                        isResumed = true
                                        cleanup()
                                        val drmConfig = com.streambd.iptv.data.models.DrmConfig(type = "clearkey", licenseServer = foundKeyJson!!)
                                        continuation.resume(ResolvedToken(url = reqUrl, headers = outHeaders, drm = drmConfig))
                                    } else {
                                        foundMpdUrl = reqUrl
                                        foundMpdHeaders = outHeaders
                                        // Wait up to 3 seconds for the key
                                        mpdTimeoutRunnable = Runnable {
                                            if (!isResumed) {
                                                isResumed = true
                                                cleanup()
                                                continuation.resume(ResolvedToken(url = reqUrl, headers = outHeaders))
                                            }
                                        }
                                        handler.postDelayed(mpdTimeoutRunnable!!, 3000)
                                    }
                                } else {
                                    isResumed = true
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
