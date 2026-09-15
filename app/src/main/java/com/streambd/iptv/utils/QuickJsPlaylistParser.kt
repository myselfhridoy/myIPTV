package com.streambd.iptv.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class JsPlaylistResult {
    data class Success(val m3u: String) : JsPlaylistResult()
    data class Error(val message: String, val exception: Exception? = null) : JsPlaylistResult()
    object Empty : JsPlaylistResult()
}

object QuickJsPlaylistParser {
    private const val TAG = "QuickJsParser"
    
    // Concurrency Limit
    private val jsExecutionSemaphore = Semaphore(5)

    // Security: Validate IPs to prevent SSRF
    // This DNS will be used by OkHttp for every request AND every automatic redirect
    private val safeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            for (addr in addresses) {
                    val hostAddr = addr.hostAddress ?: ""
                    if (addr.isLoopbackAddress || 
                        addr.isSiteLocalAddress || 
                        addr.isAnyLocalAddress || 
                        addr.isLinkLocalAddress ||
                        hostAddr.startsWith("10.") ||
                        hostAddr.startsWith("192.168.") ||
                        hostAddr.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))) {
                        throw UnknownHostException("Blocked local/private IP: $hostAddr for host $hostname")
                    }
                }
                return addresses
            }
        }
    
        // Security: Proper SSL Client with Safe DNS
        // Intentionally NOT using UnsafeOkHttpClient
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

        private val safeFetchClient by lazy {
            OkHttpClient.Builder()
                .dns(safeDns)
                .cookieJar(memoryCookieJar)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    
        // Interface for exposing methods to QuickJS
        interface NativeBridge {
            fun send(result: String?)
            fun log(msg: String)
            fun error(msg: String)
            fun btoa(str: String): String
            fun atob(b64: String): String
            fun fetchSync(url: String, optionsJson: String?): String
        }
    
        @Suppress("UNUSED_PARAMETER")
        suspend fun executeJsPlaylist(context: Context, jsCode: String): JsPlaylistResult = withContext(Dispatchers.IO) {
        jsExecutionSemaphore.withPermit {
            // Overall timeout for execution (45s)
            val result = withTimeoutOrNull(45000L) {
                var scriptResult: JsPlaylistResult? = null
                
                var quickJs: QuickJs? = null
                try {
                    quickJs = QuickJs.create()
                    
                    val bridge = object : NativeBridge {
                        override fun send(result: String?) {
                            if (scriptResult == null) {
                                scriptResult = if (result.isNullOrBlank()) {
                                    JsPlaylistResult.Empty
                                } else {
                                    JsPlaylistResult.Success(result)
                                }
                            }
                        }

                        override fun log(msg: String) {
                            Log.d(TAG, "JS Log: $msg")
                        }

                        override fun error(msg: String) {
                            Log.e(TAG, "JS Error: $msg")
                        }

                        override fun btoa(str: String): String {
                            return Base64.encodeToString(str.toByteArray(), Base64.NO_WRAP)
                        }

                        override fun atob(b64: String): String {
                            return String(Base64.decode(b64, Base64.DEFAULT))
                        }

                        override fun fetchSync(url: String, optionsJson: String?): String {
                            return try {
                                val reqBuilder = Request.Builder().url(url)
                                var method = "GET"
                                var bodyString: String? = null
                                var contentType = "application/x-www-form-urlencoded"

                                if (!optionsJson.isNullOrBlank()) {
                                    val obj = JSONObject(optionsJson)
                                    if (obj.has("method")) method = obj.getString("method").uppercase()
                                    if (obj.has("body")) bodyString = obj.getString("body")
                                    if (obj.has("headers")) {
                                        val headersObj = obj.getJSONObject("headers")
                                        headersObj.keys().forEach { k ->
                                            val v = headersObj.getString(k)
                                            reqBuilder.header(k, v)
                                            if (k.equals("Content-Type", ignoreCase = true)) {
                                                contentType = v
                                            }
                                        }
                                    }
                                }

                                if (method == "POST" || method == "PUT" || method == "PATCH") {
                                    val mediaType = contentType.toMediaTypeOrNull()
                                    val requestBody = (bodyString ?: "").toRequestBody(mediaType)
                                    reqBuilder.method(method, requestBody)
                                } else {
                                    reqBuilder.method(method, null)
                                }

                                val response = safeFetchClient.newCall(reqBuilder.build()).execute()
                                
                                val resHeaders = JSONObject()
                                response.headers.names().forEach { name ->
                                    resHeaders.put(name, response.headers[name])
                                }
                                
                                val responseBody = response.peekBody(10 * 1024 * 1024).string()
                                
                                val resultObj = JSONObject().apply {
                                    put("ok", response.isSuccessful)
                                    put("status", response.code)
                                    put("headers", resHeaders)
                                    put("body", responseBody)
                                }
                                resultObj.toString()
                            } catch (e: Exception) {
                                JSONObject().apply {
                                    put("internal_error", e.message ?: "Network error")
                                }.toString()
                            }
                        }
                    }

                    quickJs.set("NativeBridge", NativeBridge::class.java, bridge)

                    val polyfill = """
                        var console = {
                            log: function(msg) { NativeBridge.log(String(msg)); },
                            error: function(msg) { NativeBridge.error(String(msg)); }
                        };
                        var btoa = function(s) { return NativeBridge.btoa(String(s)); };
                        var atob = function(s) { return NativeBridge.atob(String(s)); };
                        
                        function fetch(url, options) {
                            try {
                                var resStr = NativeBridge.fetchSync(url, JSON.stringify(options || {}));
                                var res = JSON.parse(resStr);
                                if (res.internal_error) {
                                    throw new Error(res.internal_error);
                                }
                                return {
                                    ok: res.ok,
                                    status: res.status,
                                    headers: res.headers,
                                    text: function() { return res.body || ""; },
                                    json: function() { return JSON.parse(res.body || "{}"); }
                                };
                            } catch (e) {
                                throw e;
                            }
                        }
                        
                        // Expose streamBD just in case for backward compatibility
                        var streamBD = {
                            send: function(res) { NativeBridge.send(res); },
                            log: function(msg) { NativeBridge.log(msg); }
                        };
                    """.trimIndent()

                    quickJs.evaluate(polyfill)
                    
                    // QuickJS 0.9.2 does not execute microtasks (no event loop).
                    // We must convert the user's async code to synchronous by stripping async/await keywords.
                    // Because our fetch polyfill is synchronous, this works perfectly.
                    val syncJsCode = jsCode
                        .replace(Regex("\\basync\\s+function\\b"), "function")
                        .replace(Regex("\\bawait\\b\\s*"), "")
                    
                    val scriptWrapper = """
                        // Evaluate user's code in global scope
                        $syncJsCode
                        
                        // Automatically call generatePlaylist if it exists
                        ;(function() {
                            try {
                                if (typeof generatePlaylist === 'function') {
                                    var res = generatePlaylist();
                                    if (res !== undefined && res !== null) {
                                        NativeBridge.send(String(res));
                                    } else {
                                        NativeBridge.send(""); // Empty
                                    }
                                } else {
                                    // Fallback for older scripts using streamBD.send directly
                                }
                            } catch (e) {
                                NativeBridge.error(e.toString());
                                NativeBridge.send(""); // Complete with empty/error
                            }
                        })();
                    """.trimIndent()

                    quickJs.evaluate(scriptWrapper)
                    
                } catch (e: Exception) {
                    if (scriptResult == null) {
                        scriptResult = JsPlaylistResult.Error(e.message ?: "Unknown JS error", e)
                    }
                } finally {
                    quickJs?.close()
                }
                
                scriptResult ?: JsPlaylistResult.Empty
            }
            
            result ?: JsPlaylistResult.Error("Timeout: JS script execution exceeded 45 seconds")
        }
    }
}
