package com.streambd.iptv.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.dokar.quickjs.quickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

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
                
                // Explicitly check for IPv4-mapped IPv6
                val isIPv4Mapped = hostAddr.startsWith("::ffff:") || hostAddr.startsWith("0:0:0:0:0:ffff:")
                val effectiveAddrStr = if (isIPv4Mapped) hostAddr.substringAfterLast(":") else hostAddr
                
                if (addr.isLoopbackAddress || 
                    addr.isSiteLocalAddress || 
                    addr.isAnyLocalAddress || 
                    addr.isLinkLocalAddress ||
                    effectiveAddrStr.startsWith("10.") ||
                    effectiveAddrStr.startsWith("192.168.") ||
                    effectiveAddrStr.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) ||
                    effectiveAddrStr.matches(Regex("^100\\.(6[4-9]|[7-9][0-9]|1[0-1][0-9]|12[0-7])\\..*")) // CGNAT 100.64.0.0/10
                ) {
                    throw UnknownHostException("Blocked local/private/CGNAT IP: $hostAddr for host $hostname")
                }
            }
            return addresses
        }
    }
    
    private val memoryCookieJar = object : okhttp3.CookieJar {
        private val store = CopyOnWriteArrayList<okhttp3.Cookie>()

        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            cookies.forEach { newCookie ->
                store.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
                store.add(newCookie)
            }
            store.removeAll { it.expiresAt < System.currentTimeMillis() }
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            return store.filter { it.matches(url) && it.expiresAt >= System.currentTimeMillis() }
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

    suspend fun executeJsPlaylist(context: Context, jsCode: String): JsPlaylistResult = withContext(Dispatchers.IO) {
        jsExecutionSemaphore.withPermit {
            // Overall timeout for execution (45s). Because quickJs block is suspendable, this natively interrupts JS!
            val result = withTimeoutOrNull(45000L) {
                var scriptResult: JsPlaylistResult? = null
                
                try {
                    quickJs {
                        // 10MB memory limit to prevent OOM
                        memoryLimit = 10L * 1024L * 1024L
                        
                        // Register simple synchronous functions
                        function("nativeLog") { args -> 
                            Log.d(TAG, "JS Log: ${args.joinToString(" ")}")
                            null
                        }
                        function("nativeError") { args -> 
                            Log.e(TAG, "JS Error: ${args.joinToString(" ")}") 
                            null
                        }
                        function("nativeBtoa") { args -> 
                            Base64.encodeToString((args[0] as String).toByteArray(Charsets.ISO_8859_1), Base64.NO_WRAP) 
                        }
                        function("nativeAtob") { args -> 
                            String(Base64.decode((args[0] as String), Base64.DEFAULT), Charsets.ISO_8859_1) 
                        }
                        function("nativeGetCookies") { args -> 
                            val httpUrl = (args[0] as String).toHttpUrlOrNull() ?: return@function ""
                            memoryCookieJar.loadForRequest(httpUrl).joinToString("; ") { "${it.name}=${it.value}" }
                        }
                        function("nativeSend") { args ->
                            val res = args.getOrNull(0) as? String
                            if (scriptResult == null) {
                                scriptResult = if (res.isNullOrBlank()) {
                                    JsPlaylistResult.Empty
                                } else {
                                    JsPlaylistResult.Success(res)
                                }
                            }
                            null
                        }

                        // Register async fetch
                        asyncFunction("nativeFetchStr") { args ->
                            val url = args[0] as String
                            val optionsJson = args.getOrNull(1) as? String
                            
                            try {
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

                                // We wrap OkHttp execution in IO context. Suspension point inside JS!
                                val response = safeFetchClient.newCall(reqBuilder.build()).await()
                                
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
                        
                        val polyfill = """
                            var console = {
                                log: function(...msg) { 
                                    nativeLog(msg.map(function(m) { return (typeof m === 'object' && m !== null) ? JSON.stringify(m) : String(m); }).join(' ')); 
                                },
                                error: function(...msg) { 
                                    nativeError(msg.map(function(m) { return (typeof m === 'object' && m !== null) ? JSON.stringify(m) : String(m); }).join(' ')); 
                                }
                            };
                            var btoa = function(s) { return nativeBtoa(String(s)); };
                            var atob = function(s) { return nativeAtob(String(s)); };
                            var document = {
                                _cookieUrl: '',
                                get cookie() { return nativeGetCookies(this._cookieUrl); },
                                set cookie(val) { }
                            };
                            
                            // Real async fetch returning Promise!
                            async function fetch(url, options) {
                                document._cookieUrl = url;
                                var resStr = await nativeFetchStr(url, JSON.stringify(options || {}));
                                var res = JSON.parse(resStr);
                                if (res.internal_error) {
                                    throw new Error(res.internal_error);
                                }
                                
                                // Create a Promise that resolves to the response object itself
                                // to mimic .then() behavior if needed, or just return it directly
                                var responseObj = {
                                    ok: res.ok,
                                    status: res.status,
                                    headers: res.headers,
                                    text: async function() { return res.body || ""; },
                                    json: async function() { return JSON.parse(res.body || "{}"); }
                                };
                                return responseObj;
                            }
                            
                            var streamBD = {
                                send: function(res) { nativeSend(res); },
                                log: function(msg) { nativeLog(msg); },
                                getCookies: function(url) { return nativeGetCookies(url); }
                            };
                        """.trimIndent()
                        
                        evaluate<String>("$polyfill\n; 'ok';")
                        
                        val scriptWrapper = """
                            // Define wrapper logic in async IIFE
                            (async function() {
                                try {
                                    // Evaluate user's code inside this async scope
                                    // Because evaluate is called directly, we just inject their code
                                    $jsCode
                                    
                                    if (typeof generatePlaylist === 'function') {
                                        var res = generatePlaylist();
                                        if (res instanceof Promise) {
                                            res = await res;
                                        }
                                        if (res !== undefined && res !== null) {
                                            nativeSend(String(res));
                                        } else {
                                            nativeSend(""); 
                                        }
                                    }
                                } catch (e) {
                                    nativeError(e.toString());
                                    nativeSend(""); 
                                }
                            })();
                        """.trimIndent()
                        
                        evaluate<String>("$scriptWrapper\n; 'ok';")
                    }
                } catch (e: Exception) {
                    if (scriptResult == null) {
                        scriptResult = JsPlaylistResult.Error(e.message ?: "Unknown JS error", e)
                    }
                }
                
                scriptResult ?: JsPlaylistResult.Empty
            }
            
            result ?: JsPlaylistResult.Error("Timeout: JS script execution exceeded 45 seconds")
        }
    }
}

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }
}
