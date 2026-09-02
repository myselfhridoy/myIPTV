package com.streambd.iptv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.streambd.iptv.data.models.Addon
import com.streambd.iptv.data.models.StreamSource
import com.streambd.iptv.data.models.Subtitle

object AddonManager {
    private val DEFAULT_REGISTRY_URL get() = com.streambd.iptv.utils.RemoteConfigManager.getAddonRegistryUrl()
    private const val ADDONS_PREF_NAME = "prysm_addons_prefs"
    private const val ADDONS_KEY = "addons_list"

    private var cachedAddons: List<Addon>? = null
    private val addonScriptsCache = mutableMapOf<String, String>()
    private val hostUserAgents = mutableMapOf<String, String>()
    
    private class MyCookieJar : okhttp3.CookieJar {
        private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()

        fun injectCookies(url: String, cookieString: String) {
            val httpUrl = url.toHttpUrlOrNull() ?: return
            val cookies = cookieString.split(";").mapNotNull {
                okhttp3.Cookie.parse(httpUrl, it.trim())
            }
            saveFromResponse(httpUrl, cookies)
        }

        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            val host = url.host
            val existingCookies = cookieStore.getOrPut(host) { java.util.Collections.synchronizedList(mutableListOf()) }
            
            synchronized(existingCookies) {
                // Overwrite existing cookies or add new ones
                for (cookie in cookies) {
                    val index = existingCookies.indexOfFirst { it.name == cookie.name }
                    if (index != -1) {
                        existingCookies[index] = cookie
                    } else {
                        existingCookies.add(cookie)
                    }
                }
            }
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            val host = url.host
            val cookies = mutableListOf<okhttp3.Cookie>()
            
            // Add cookies for exact host
            cookieStore[host]?.let { list ->
                synchronized(list) { cookies.addAll(list) }
            }
            
            // Add cookies for parent domains (e.g., .domain.com)
            cookieStore.keys.filter { host.endsWith(it) && host != it }.forEach { key ->
                cookieStore[key]?.let { list ->
                    synchronized(list) { cookies.addAll(list) }
                }
            }
            
            return cookies
        }
    }

    private val cookieJar = MyCookieJar()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(cookieJar)
        .build()
    private val gson = Gson()

    suspend fun getAddons(context: Context, forceSync: Boolean = false): List<Addon> = withContext(Dispatchers.IO) {
        if (!forceSync && cachedAddons != null) {
            return@withContext cachedAddons!!
        }

        val prefs = context.getSharedPreferences(ADDONS_PREF_NAME, Context.MODE_PRIVATE)
        val savedAddonsJson = prefs.getString(ADDONS_KEY, null)
        
        var addons = mutableListOf<Addon>()
        if (savedAddonsJson != null) {
            val type = object : TypeToken<List<Addon>>() {}.type
            addons = gson.fromJson(savedAddonsJson, type)
        }

        if (forceSync || addons.isEmpty()) {
            try {
                val req = Request.Builder().url(DEFAULT_REGISTRY_URL).build()
                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (body != null) {
                        val type = object : TypeToken<List<Addon>>() {}.type
                        val registryAddons: List<Addon> = gson.fromJson(body, type)
                        
                        var changed = false
                        for (rAddon in registryAddons) {
                            val existingIndex = addons.indexOfFirst { it.id == rAddon.id }
                            if (existingIndex >= 0) {
                                if (addons[existingIndex].url != rAddon.url || addons[existingIndex].name != rAddon.name) {
                                    addons[existingIndex] = rAddon
                                    changed = true
                                }
                            } else {
                                addons.add(rAddon)
                                changed = true
                            }
                        }
                        
                        if (changed) {
                            prefs.edit().putString(ADDONS_KEY, gson.toJson(addons)).apply()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
                } catch (ce: Exception) {}
            }
        }

        cachedAddons = addons
        return@withContext addons
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    suspend fun resolveFromAddons(
        context: Context,
        type: String, // "movie" or "tv"
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null,
        onSourceFound: (List<StreamSource>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val addons = getAddons(context)
        val activeAddons = addons.filter { it.enabled }
        
        // Load crypto-js from assets
        val cryptoJsCode = try {
            context.assets.open("crypto-js.min.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

        val deferredResults = activeAddons.map { addon ->
            async {
                try {
                    var jsCode = addonScriptsCache[addon.id]
                    if (jsCode == null) {
                        val req = Request.Builder().url(addon.url).build()
                        val res = client.newCall(req).execute()
                        if (res.isSuccessful) {
                            jsCode = res.body?.string()
                            if (jsCode != null) {
                                addonScriptsCache[addon.id] = jsCode
                            }
                        }
                    }

                    if (jsCode != null) {
                        // 60 seconds timeout for each addon to prevent hanging on complex addons
                        val sourcesJson = withTimeoutOrNull(60000L) {
                            executeAddonInWebView(context, cryptoJsCode, jsCode, addon.name, addon.url, null, type, tmdbId, season, episode)
                        }
                        
                        if (sourcesJson != null && sourcesJson.isNotEmpty() && sourcesJson != "null" && sourcesJson != "undefined") {
                            val sourceType = object : TypeToken<List<StreamSource>>() {}.type
                            val parsedSources: List<StreamSource> = gson.fromJson(sourcesJson, sourceType)
                            val mapped = parsedSources.map { s ->
                                s.copy(provider = if (s.provider.isBlank()) addon.name else s.provider)
                            }
                            if (mapped.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    onSourceFound(mapped)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        // Wait for all addons to finish (or timeout)
        deferredResults.awaitAll()
    }

    private suspend fun executeAddonInWebView(
        context: Context,
        cryptoJsCode: String,
        addonCode: String,
        addonName: String,
        addonUrl: String,
        functionName: String?,
        type: String,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            webView.settings.allowUniversalAccessFromFileURLs = true
            webView.settings.allowFileAccessFromFileURLs = true

            val interfaceName = "AndroidBridge"
            
            webView.addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun onResult(result: String) {
                    if (continuation.isActive) {
                        continuation.resume(result)
                        webView.post { webView.destroy() }
                    }
                }

                @JavascriptInterface
                fun onError(error: String) {
                    if (continuation.isActive) {
                        continuation.resume("[]")
                        webView.post { webView.destroy() }
                    }
                }

                @JavascriptInterface
                fun log(msg: String) {
                    android.util.Log.d("AddonManagerJS", msg)
                }

                @JavascriptInterface
                fun error(msg: String) {
                    android.util.Log.e("AddonManagerJS", msg)
                }

                @JavascriptInterface
                fun getCookie(host: String, name: String): String {
                    val url = "https://$host".toHttpUrlOrNull() ?: return ""
                    val cookies = cookieJar.loadForRequest(url)
                    return cookies.firstOrNull { it.name == name }?.value ?: ""
                }

                @JavascriptInterface
                fun doBypass(reqId: String, url: String) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = CloudflareBypasser.bypass(context, url)
                        if (result != null) {
                            val host = try { java.net.URI(url).host } catch (e: Exception) { url }
                            hostUserAgents[host] = result.userAgent
                            cookieJar.injectCookies(url, result.cookies)
                            withContext(Dispatchers.Main) {
                                webView.evaluateJavascript("javascript:window.onAndroidBypassResponse('" + reqId + "', true);", null)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                webView.evaluateJavascript("javascript:window.onAndroidBypassResponse('" + reqId + "', false);", null)
                            }
                        }
                    }
                }

                @JavascriptInterface
                fun doFetch(reqId: String, url: String, optionsJson: String?) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val requestBuilder = okhttp3.Request.Builder().url(url)
                            if (optionsJson != null && optionsJson != "null" && optionsJson != "undefined") {
                                val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                val options: Map<String, Any>? = gson.fromJson(optionsJson, type)
                                if (options != null) {
                                    val method = (options["method"] as? String)?.uppercase() ?: "GET"
                                    var contentTypeStr = "text/plain"
                                    val reqHost = try { java.net.URI(url).host } catch(e: Exception) { "" }
                                    val userAgent = hostUserAgents[reqHost] ?: hostUserAgents.entries.firstOrNull { reqHost.endsWith(it.key) }?.value
                                    
                                    var hasUserAgent = false
                                    val headersMap = options["headers"] as? Map<String, Any>
                                    headersMap?.forEach { (k, v) ->
                                        val valStr = v.toString()
                                        if (k.equals("content-type", ignoreCase = true)) {
                                            contentTypeStr = valStr
                                        }
                                        if (k.equals("User-Agent", ignoreCase = true)) {
                                            hasUserAgent = true
                                            requestBuilder.addHeader(k, userAgent ?: valStr)
                                        } else {
                                            requestBuilder.addHeader(k, valStr)
                                        }
                                    }
                                    if (!hasUserAgent && userAgent != null) {
                                        requestBuilder.addHeader("User-Agent", userAgent)
                                    }
                                    
                                    val bodyStr = options["body"]?.toString()
                                    if (method == "POST" || method == "PUT" || method == "PATCH") {
                                        val mediaType = contentTypeStr.toMediaTypeOrNull()
                                        val reqBody = (bodyStr ?: "").toRequestBody(mediaType)
                                        requestBuilder.method(method, reqBody)
                                    } else {
                                        requestBuilder.method(method, null)
                                    }
                                }
                            }
                            
                            val response = client.newCall(requestBuilder.build()).execute()
                            
                            val inputStream = response.body?.byteStream()
                            val buffer = java.io.ByteArrayOutputStream()
                            val data = ByteArray(8192)
                            var totalRead = 0
                            if (inputStream != null) {
                                var read = inputStream.read(data)
                                while (read != -1 && totalRead < 5 * 1024 * 1024) {
                                    buffer.write(data, 0, read)
                                    totalRead += read
                                    read = inputStream.read(data)
                                }
                                inputStream.close()
                            }
                            val bodyBytes = buffer.toByteArray()
                            
                            val status = response.code
                            
                            val responseHeadersMap = mutableMapOf<String, String>()
                            response.headers.names().forEach { name ->
                                responseHeadersMap[name] = response.headers.values(name).joinToString(", ")
                            }
                            val headersJsonStr = gson.toJson(responseHeadersMap)
                            
                            withContext(Dispatchers.Main) {
                                val base64Body = android.util.Base64.encodeToString(bodyBytes, android.util.Base64.NO_WRAP)
                                val safeHeadersJson = android.util.Base64.encodeToString(headersJsonStr.toByteArray(), android.util.Base64.NO_WRAP)
                                webView.evaluateJavascript("javascript:window.onAndroidFetchResponse('" + reqId + "', " + status + ", '" + safeHeadersJson + "', '" + base64Body + "');", null)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                webView.evaluateJavascript("javascript:window.onAndroidFetchError('" + reqId + "', '" + (e.message?.replace("'", "\\'") ?: "Error") + "');", null)
                            }
                        }
                    }
                }
            }, interfaceName)

            val seasonArg = season?.toString() ?: "null"
            val episodeArg = episode?.toString() ?: "null"

            val addonCodeBase64 = android.util.Base64.encodeToString(addonCode.toByteArray(), android.util.Base64.NO_WRAP)

            val html = """
                <html>
                <head>
                    <script>
                        $cryptoJsCode
                    </script>
                    <script>
                        window.console = {
                            log: function() { AndroidBridge.log(Array.prototype.slice.call(arguments).join(' ')); },
                            error: function() { AndroidBridge.error(Array.prototype.slice.call(arguments).join(' ')); },
                            warn: function() { AndroidBridge.log(Array.prototype.slice.call(arguments).join(' ')); }
                        };
                        window.originalFetch = window.fetch;
                        window.fetchPromises = {};
                        window.onAndroidFetchResponse = function(reqId, status, headersBase64, base64Body) {
                            var p = window.fetchPromises[reqId];
                            if (p) {
                                var bodyText;
                                try {
                                    bodyText = decodeURIComponent(escape(window.atob(base64Body)));
                                } catch (e) {
                                    bodyText = window.atob(base64Body);
                                }
                                var headersJson = decodeURIComponent(escape(window.atob(headersBase64)));
                                var headersObj = JSON.parse(headersJson);
                                var res = {
                                    status: status,
                                    ok: status >= 200 && status < 300,
                                    headers: {
                                        get: function(name) {
                                            var key = Object.keys(headersObj).find(function(k) { return k.toLowerCase() === name.toLowerCase(); });
                                            return key ? headersObj[key] : null;
                                        }
                                    },
                                    text: function() { return Promise.resolve(bodyText); },
                                    json: function() { return Promise.resolve(JSON.parse(bodyText)); }
                                };
                                p.resolve(res);
                                delete window.fetchPromises[reqId];
                            }
                        };
                        window.onAndroidFetchError = function(reqId, error) {
                            var p = window.fetchPromises[reqId];
                            if (p) {
                                p.reject(new Error(error));
                                delete window.fetchPromises[reqId];
                            }
                        };
                        window.fetch = function(url, options) {
                            return new Promise(function(resolve, reject) {
                                var reqId = Math.random().toString(36).substring(7);
                                window.fetchPromises[reqId] = { resolve: resolve, reject: reject };
                                var optionsJson = options ? JSON.stringify(options) : "null";
                                AndroidBridge.doFetch(reqId, url, optionsJson);
                            });
                        };
                        window.bypassPromises = {};
                        window.onAndroidBypassResponse = function(reqId, success) {
                            var p = window.bypassPromises[reqId];
                            if (p) {
                                p.resolve(success);
                                delete window.bypassPromises[reqId];
                            }
                        };
                        window.bypassCloudflare = function(url) {
                            return new Promise(function(resolve, reject) {
                                var reqId = Math.random().toString(36).substring(7);
                                window.bypassPromises[reqId] = { resolve: resolve, reject: reject };
                                AndroidBridge.doBypass(reqId, url);
                            });
                        };

                        try {
                            var decodedCode = decodeURIComponent(escape(window.atob("$addonCodeBase64")));
                            var parserFunc;
                            try {
                                parserFunc = new Function("CryptoJS", decodedCode)(CryptoJS);
                            } catch(e) {
                                AndroidBridge.log("new Function failed: " + e.toString());
                            }
                            
                            if (typeof parserFunc !== 'function') {
                                // Snapshot existing window keys before eval
                                var keysBefore = {};
                                for (var k in window) { keysBefore[k] = true; }
                                
                                eval(decodedCode);
                                
                                // Find newly added functions after eval
                                if (typeof parserFunc !== 'function') {
                                    for (var k in window) {
                                        if (!keysBefore[k] && typeof window[k] === 'function') {
                                            parserFunc = window[k];
                                            AndroidBridge.log("Auto-discovered function: " + k);
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (typeof parserFunc === 'function') {
                                var result;
                                try {
                                    result = parserFunc("$type", "$tmdbId", $seasonArg, $episodeArg);
                                } catch(callErr) {
                                    AndroidBridge.error("[AddonManager] parserFunc call threw: " + callErr.toString());
                                    AndroidBridge.onError(callErr.toString());
                                    return;
                                }
                                // Handle both Promise and non-Promise returns
                                if (result && typeof result.then === 'function') {
                                    result.then(function(sources) {
                                        AndroidBridge.log("[AddonManager] resolved sources count: " + (sources ? sources.length : 0));
                                        AndroidBridge.onResult(JSON.stringify(sources || []));
                                    }).catch(function(err) {
                                        AndroidBridge.error("[AddonManager] promise rejected: " + err.toString());
                                        AndroidBridge.onError(err.toString());
                                    });
                                } else {
                                    AndroidBridge.onResult(JSON.stringify(result || []));
                                }
                            } else {
                                AndroidBridge.error("[AddonManager] parserFunc is not a function (type=" + typeof parserFunc + ")");
                                AndroidBridge.onError("Parser is not a function");
                            }
                        } catch(e) {
                            AndroidBridge.error("[AddonManager] top-level error: " + e.toString());
                            AndroidBridge.onError(e.toString());
                        }
                    </script>
                </head>
                <body></body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL("file:///android_asset/dummy.html", html, "text/html", "UTF-8", null)

            continuation.invokeOnCancellation {
                webView.post { webView.destroy() }
            }
        }
    }
}
