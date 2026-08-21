package com.streambd.iptv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object JsPlaylistParser {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun executeJsPlaylist(context: Context, jsCode: String): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(45000L) { // 45 seconds timeout
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context)
                webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Bypass CORS for fetch API by allowing universal access from a file:// origin
                webView.settings.allowUniversalAccessFromFileURLs = true
                webView.settings.allowFileAccessFromFileURLs = true
                
                webView.addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun send(result: String) {
                        if (continuation.isActive) {
                            continuation.resume(result)
                            webView.post { webView.destroy() }
                        }
                    }
                    
                    @JavascriptInterface
                    fun log(msg: String) {
                        android.util.Log.d("JsPlaylistParser", msg)
                    }
                    
                    @JavascriptInterface
                    fun fetchUrl(url: String, headersJson: String?): String {
                        return try {
                            val client = com.streambd.iptv.utils.UnsafeOkHttpClient.getUnsafeOkHttpClient()
                            val reqBuilder = okhttp3.Request.Builder().url(url)
                            if (!headersJson.isNullOrBlank()) {
                                try {
                                    val obj = org.json.JSONObject(headersJson)
                                    obj.keys().forEach { k -> reqBuilder.header(k, obj.getString(k)) }
                                } catch (e: Exception) {}
                            }
                            val response = client.newCall(reqBuilder.build()).execute()
                            response.body?.string() ?: ""
                        } catch (e: Exception) {
                            ""
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
                
                // Load a dummy https URL to prevent file:// CORS restrictions on modern Android
                webView.loadDataWithBaseURL("https://localhost/", "<html><body></body></html>", "text/html", "UTF-8", null)
                
                continuation.invokeOnCancellation {
                    webView.post { webView.destroy() }
                }
            }
        }
    }
}
