package com.streambd.iptv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object CloudflareBypasser {

    data class BypassResult(val userAgent: String, val cookies: String)

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun bypass(context: Context, url: String): BypassResult? = withContext(Dispatchers.Main) {
        var result: BypassResult? = null
        var isDone = false
        
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Optional: Make User-Agent look slightly less like a WebView
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }

        webView.loadUrl(url)

        val userAgent = webView.settings.userAgentString
        val host = try {
            java.net.URI(url).host
        } catch (e: Exception) {
            url
        }
        
        // Wait up to 15 seconds (30 * 500ms) for cf_clearance or other clearance cookies
        for (i in 0 until 30) {
            val cookies = CookieManager.getInstance().getCookie(url)
            if (cookies != null && (cookies.contains("cf_clearance") || cookies.contains("clearance"))) {
                result = BypassResult(userAgent, cookies)
                isDone = true
                break
            }
            delay(500)
        }
        
        // If we didn't find specific clearance cookies, but we have some cookies, let's return them anyway as a fallback
        if (!isDone) {
            val fallbackCookies = CookieManager.getInstance().getCookie(url)
            if (!fallbackCookies.isNullOrBlank()) {
                result = BypassResult(userAgent, fallbackCookies)
            }
        }

        webView.destroy()
        return@withContext result
    }
}
