package com.myselfhridoy.streambdplayer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class BypassResult(
    val url: String,
    val headers: Map<String, String>?,
    val content: String? = null
)

object InfinityFreeBypass {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun resolve(url: String, headers: Map<String, String>? = null): BypassResult? = withContext(Dispatchers.IO) {
        try {
            val bypassUrlStr = if (url.contains("?")) "$url&_t=${System.currentTimeMillis()}" else "$url?_t=${System.currentTimeMillis()}"
            val bypassUrl = URL(bypassUrlStr)

            var requestBuilder = Request.Builder()
                .url(bypassUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "*/*")
                .addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .addHeader("Pragma", "no-cache")
                .addHeader("Expires", "0")

            headers?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            
            val initialRequest = requestBuilder.build()
            val initialResponse = client.newCall(initialRequest).execute()
            
            if (!initialResponse.isSuccessful) return@withContext null
            val htmlContent = initialResponse.body?.string() ?: return@withContext null

            // Regex extraction
            val aPattern = Pattern.compile("var\\s+a\\s*=\\s*toNumbers\\(\"([a-fA-F0-9]+)\"\\)")
            val bPattern = Pattern.compile("b\\s*=\\s*toNumbers\\(\"([a-fA-F0-9]+)\"\\)")
            val cPattern = Pattern.compile("c\\s*=\\s*toNumbers\\(\"([a-fA-F0-9]+)\"\\)")
            val urlPattern = Pattern.compile("location\\.href\\s*=\\s*\"([^\"]+)\"")

            val aMatcher = aPattern.matcher(htmlContent)
            val bMatcher = bPattern.matcher(htmlContent)
            val cMatcher = cPattern.matcher(htmlContent)
            val urlMatcher = urlPattern.matcher(htmlContent)

            if (!aMatcher.find() || !bMatcher.find() || !cMatcher.find() || !urlMatcher.find()) {
                return@withContext null
            }

            val a = aMatcher.group(1)
            val b = bMatcher.group(1)
            val c = cMatcher.group(1)
            val redirectPath = urlMatcher.group(1)

            if (a == null || b == null || c == null || redirectPath == null) return@withContext null

            val decryptedValue = CryptoUtils.decryptSlowAES(c, a, b)
            val payloadCookie = "__test=$decryptedValue"

            val redirectUrl = try {
                URL(bypassUrl, redirectPath).toString()
            } catch (e: Exception) {
                if (redirectPath.startsWith("/")) {
                    "${bypassUrl.protocol}://${bypassUrl.host}$redirectPath"
                } else {
                    val path = bypassUrl.path.replaceAfterLast("/", "")
                    "${bypassUrl.protocol}://${bypassUrl.host}$path$redirectPath"
                }
            }

            val outHeaders = headers?.toMutableMap() ?: mutableMapOf()
            if (outHeaders.containsKey("Cookie") || outHeaders.containsKey("cookie")) {
                val key = if (outHeaders.containsKey("Cookie")) "Cookie" else "cookie"
                outHeaders[key] = "${outHeaders[key]}; $payloadCookie"
            } else {
                outHeaders["Cookie"] = payloadCookie
            }

            var content = ""
            var fetchSuccess = false

            for (attempt in 1..4) {
                try {
                    if (attempt > 1) {
                        val delayMs = if (attempt == 2) 1000L else if (attempt == 3) 2000L else 3000L
                        kotlinx.coroutines.delay(delayMs)
                    }

                    val finalRequestBuilder = Request.Builder()
                        .url(redirectUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Cookie", payloadCookie)
                    
                    headers?.forEach { (k, v) -> finalRequestBuilder.addHeader(k, v) }
                    
                    val finalResponse = client.newCall(finalRequestBuilder.build()).execute()
                    if (finalResponse.isSuccessful) {
                        content = finalResponse.body?.string() ?: ""
                        fetchSuccess = true
                        break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (fetchSuccess) {
                BypassResult(url = redirectUrl, headers = outHeaders, content = content)
            } else {
                BypassResult(url = redirectUrl, headers = outHeaders)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
