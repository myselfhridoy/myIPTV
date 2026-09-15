package com.streambd.iptv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicBoolean

object JsPlaylistParser {

    private val jsExecutionSemaphore = Semaphore(5)

    private fun isUrlAllowed(url: String): Boolean {
        try {
            val host = android.net.Uri.parse(url).host ?: return false
            if (host.equals("localhost", ignoreCase = true) || host == "127.0.0.1") return false
            if (host.startsWith("169.254.") || host.startsWith("10.") || host.startsWith("192.168.")) return false
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private val memoryCookieJar = object : okhttp3.CookieJar {
        private val store = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()

        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            val host = url.host
            val existing = store.getOrPut(host) { mutableListOf() }
            synchronized(existing) {
                cookies.forEach { newCookie ->
                    existing.removeAll { it.name == newCookie.name && it.path == newCookie.path }
                    existing.add(newCookie)
                }
                existing.removeAll { it.expiresAt < System.currentTimeMillis() }
            }
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            val host = url.host
            return store[host]?.filter { it.matches(url) && it.expiresAt >= System.currentTimeMillis() } ?: emptyList()
        }
    }

    private val fetchClient by lazy {
        com.streambd.iptv.utils.UnsafeOkHttpClient.getUnsafeOkHttpClient().newBuilder()
            .cookieJar(memoryCookieJar)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun executeJsPlaylist(context: Context, jsCode: String): String? = withContext(Dispatchers.Main) {
        jsExecutionSemaphore.withPermit {
            withTimeoutOrNull(45000L) { // 45 seconds timeout
                suspendCancellableCoroutine { continuation ->
                    val webView = WebView(context)
                    val isDestroyed = AtomicBoolean(false)

                    fun safeDestroy() {
                        if (!isDestroyed.getAndSet(true)) {
                            webView.post {
                                try {
                                    webView.destroy()
                                } catch (e: Exception) {}
                            }
                        }
                    }

                    webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    
                    webView.addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun send(result: String?) {
                            if (continuation.isActive) {
                                continuation.resume(result)
                                safeDestroy()
                            }
                        }
                        
                        @JavascriptInterface
                        fun log(msg: String) {
                            android.util.Log.d("JsPlaylistParser", msg)
                        }
                        
                        @JavascriptInterface
                        fun fetchUrl(url: String, headersJson: String?): String? {
                            if (!isUrlAllowed(url)) return null
                            return try {
                                val reqBuilder = okhttp3.Request.Builder().url(url)
                                if (!headersJson.isNullOrBlank()) {
                                    try {
                                        val obj = org.json.JSONObject(headersJson)
                                        obj.keys().forEach { k -> reqBuilder.header(k, obj.getString(k)) }
                                    } catch (e: Exception) {}
                                }
                                val response = fetchClient.newCall(reqBuilder.build()).execute()
                                response.peekBody(10 * 1024 * 1024).string()
                            } catch (e: Exception) {
                                null
                            }
                        }

                        @JavascriptInterface
                        fun postUrl(url: String, headersJson: String?, bodyString: String?): String? {
                            if (!isUrlAllowed(url)) return null
                            return try {
                                val reqBuilder = okhttp3.Request.Builder().url(url)
                                var contentType = "application/x-www-form-urlencoded"
                                if (!headersJson.isNullOrBlank()) {
                                    try {
                                        val obj = org.json.JSONObject(headersJson)
                                        obj.keys().forEach { k ->
                                            reqBuilder.header(k, obj.getString(k))
                                            if (k.equals("Content-Type", ignoreCase = true)) {
                                                contentType = obj.getString(k)
                                            }
                                        }
                                    } catch (e: Exception) {}
                                }
                                val bodyText = bodyString ?: ""
                                val mediaType = contentType.toMediaTypeOrNull()
                                val requestBody = bodyText.toRequestBody(mediaType)
                                reqBuilder.post(requestBody)
                                val response = fetchClient.newCall(reqBuilder.build()).execute()
                                response.peekBody(10 * 1024 * 1024).string()
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }, "streamBD")

                    // Execute the user's JS script.
                    // We wrap it in a try-catch to log any syntax or execution errors.
                    val script = """
                        try {
                            $jsCode
                        } catch(e) {
                            if (typeof streamBD !== 'undefined') {
                                streamBD.log(e.toString());
                                streamBD.send("");
                            }
                        }
                    """.trimIndent()

                    // Load a dummy file URL and execute the JS when finished loading
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            webView.evaluateJavascript(script, null)
                        }
                    }
                    
                    // Extract custom BASE_URL from JS comments if provided, to bypass CORS for specific origins
                    val baseUrlRegex = Regex("//\\s*BASE_URL=(https://[^\\s]+)")
                    val match = baseUrlRegex.find(jsCode)
                    val webViewBaseUrl = match?.groupValues?.get(1) ?: "https://localhost/"
                    
                    // Load a dummy https URL to prevent file:// CORS restrictions on modern Android
                    webView.loadDataWithBaseURL(webViewBaseUrl, "<html><body></body></html>", "text/html", "UTF-8", null)
                    
                    continuation.invokeOnCancellation {
                        safeDestroy()
                    }
                }
            }
        }
    }
}
