package com.streambd.iptv.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.dokar.quickjs.quickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streambd.iptv.data.models.Addon
import com.streambd.iptv.data.models.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

object QuickJsAddonManager {
    // Limit concurrent executions to prevent native memory exhaustion or JNI crashes
    private val jsExecutionSemaphore = Semaphore(2)
    private val DEFAULT_REGISTRY_URL get() = com.streambd.iptv.utils.RemoteConfigManager.getAddonRegistryUrl()
    private const val ADDONS_PREF_NAME = "prysm_addons_prefs"
    private const val ADDONS_KEY = "addons_list"

    private var cachedAddons: List<Addon>? = null
    private val addonScriptsCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    // Cookie cache similar to QuickJsPlaylistParser
    private val cookieCache = CopyOnWriteArrayList<okhttp3.Cookie>()

    private val safeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            for (addr in addresses) {
                if (addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress) {
                    throw UnknownHostException("SSRF Blocked: Local network access not allowed for $hostname")
                }
                
                val hostAddress = addr.hostAddress ?: ""
                val isIPv4Mapped = hostAddress.startsWith("::ffff:") || hostAddress.startsWith("0:0:0:0:0:ffff:", ignoreCase = true)
                val effectiveAddrStr = if (isIPv4Mapped) {
                    hostAddress.substringAfterLast(":")
                } else {
                    hostAddress
                }
                
                if (effectiveAddrStr.startsWith("127.") || effectiveAddrStr.startsWith("10.") ||
                    effectiveAddrStr.startsWith("192.168.")
                ) {
                    throw UnknownHostException("SSRF Blocked: Local network access not allowed for $hostname")
                }
                
                if (effectiveAddrStr.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..+"))) {
                    throw UnknownHostException("SSRF Blocked: Local network access not allowed for $hostname")
                }
                
                if (effectiveAddrStr.matches(Regex("^100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\..+"))) {
                    throw UnknownHostException("SSRF Blocked: CGNAT access not allowed for $hostname")
                }
            }
            return addresses
        }
    }

    private val safeFetchClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(safeDns)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : okhttp3.CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                cookies.forEach { newCookie ->
                    cookieCache.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
                    cookieCache.add(newCookie)
                }
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                val host = url.host
                val now = System.currentTimeMillis()
                cookieCache.removeAll { it.expiresAt < now }
                return cookieCache.filter {
                    (host == it.domain || host.endsWith(it.domain)) && it.expiresAt >= now
                }
            }
        })
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .dns(safeDns)
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
                val res = client.newCall(req).await()
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
                        val res = client.newCall(req).await()
                        if (res.isSuccessful) {
                            jsCode = res.body?.string()
                            if (jsCode != null) {
                                addonScriptsCache[addon.id] = jsCode
                            }
                        }
                    }

                    if (jsCode != null) {
                        // 60 seconds timeout for each addon to prevent hanging on complex addons
                        val sourcesJson = jsExecutionSemaphore.withPermit {
                            executeAddonInQuickJs(context, cryptoJsCode, jsCode, addon.name, addon.url, null, type, tmdbId, season, episode, null)
                        }
                        
                        if (sourcesJson != null && sourcesJson.isNotEmpty() && sourcesJson != "null" && sourcesJson != "undefined") {
                            try {
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
                            } catch (e: Exception) {
                                Log.e("QuickJsAddonManager", "Failed to parse sources for ${addon.name}: ${e.message}")
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

    suspend fun testAddon(
        context: Context,
        jsCode: String,
        type: String,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): Pair<List<StreamSource>, String> {
        val cryptoJsCode = try {
            context.assets.open("crypto-js.min.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
        val logs = StringBuilder()
        val resultStr = executeAddonInQuickJs(context, cryptoJsCode, jsCode, "Test Addon", "http://test", null, type, tmdbId, season, episode, logs)
        return try {
            val listType = object : com.google.gson.reflect.TypeToken<List<StreamSource>>() {}.type
            Pair(gson.fromJson(resultStr, listType) ?: emptyList(), logs.toString())
        } catch (e: Exception) {
            Pair(emptyList(), logs.toString() + "\nException parsing result: \${e.message}")
        }
    }

    private suspend fun executeAddonInQuickJs(
        context: Context,
        cryptoJsCode: String,
        addonCode: String,
        addonName: String,
        addonUrl: String,
        functionName: String?,
        type: String,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        logBuilder: StringBuilder? = null
    ): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(60000L) {
            var scriptResult: String? = null
            
            try {
                quickJs {
                    memoryLimit = 10L * 1024L * 1024L // 10MB memory limit
                    
                    function("nativeLog") { args ->
                        val msg = args.joinToString(" ") { it.toString() }
                        Log.d("AddonManagerJS", msg)
                        logBuilder?.append("LOG: ")?.append(msg)?.append("\n")
                        null
                    }
                    function("nativeError") { args ->
                        val msg = args.joinToString(" ") { it.toString() }
                        Log.e("AddonManagerJS", msg)
                        logBuilder?.append("ERROR: ")?.append(msg)?.append("\n")
                        null
                    }
                    function("nativeSend") { args ->
                        if (args.isNotEmpty()) {
                            scriptResult = args[0] as String
                        }
                        null
                    }
                    function("nativeBtoa") { args ->
                        if (args.isNotEmpty()) {
                            Base64.encodeToString((args[0] as String).toByteArray(Charsets.ISO_8859_1), Base64.NO_WRAP)
                        } else ""
                    }
                    function("nativeAtob") { args ->
                        if (args.isNotEmpty()) {
                            String(Base64.decode((args[0] as String), Base64.DEFAULT), Charsets.ISO_8859_1)
                        } else ""
                    }
                    
                    asyncFunction("nativeBypass") { _ ->
                        // Dummy bypass for now, resolves to false
                        false
                    }

                    asyncFunction("nativeFetchStr") { args ->
                        val url = args[0] as String
                        val optionsJson = if (args.size > 1) args[1] as String else ""
                        
                        val reqBuilder = Request.Builder().url(url)
                        var method = "GET"
                        var bodyString: String? = null
                        var contentType = "application/x-www-form-urlencoded"

                        if (optionsJson.isNotBlank() && optionsJson != "null" && optionsJson != "undefined") {
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

                        safeFetchClient.newCall(reqBuilder.build()).await().use { response ->
                            val resHeaders = JSONObject()
                            response.headers.names().forEach { name ->
                                resHeaders.put(name, response.headers.values(name).joinToString(", "))
                            }
                            
                            val result = JSONObject()
                            result.put("status", response.code)
                            result.put("ok", response.isSuccessful)
                            result.put("headers", resHeaders)
                            result.put("body", response.body?.string() ?: "")
                            
                            result.toString()
                        }
                    }

                    val polyfill = """
                        globalThis.window = globalThis;
                        globalThis.console = {
                            log: function(...msg) { 
                                nativeLog(msg.map(function(m) { return (typeof m === 'object' && m !== null) ? JSON.stringify(m) : String(m); }).join(' ')); 
                            },
                            error: function(...msg) { 
                                nativeError(msg.map(function(m) { return (typeof m === 'object' && m !== null) ? JSON.stringify(m) : String(m); }).join(' ')); 
                            },
                            warn: function(...msg) { 
                                nativeLog(msg.map(function(m) { return (typeof m === 'object' && m !== null) ? JSON.stringify(m) : String(m); }).join(' ')); 
                            }
                        };
                        globalThis.atob = function(s) { return nativeAtob(String(s)); };
                        globalThis.btoa = function(s) { return nativeBtoa(String(s)); };
                        
                        globalThis.bypassCloudflare = async function(url) {
                            return await nativeBypass(url);
                        };
                        
                        globalThis.fetch = async function(url, options) {
                            var resStr = await nativeFetchStr(url, options ? JSON.stringify(options) : "null");
                            var res = JSON.parse(resStr);
                            
                            var headersObj = res.headers || {};
                            var responseObj = {
                                ok: res.ok,
                                status: res.status,
                                headers: {
                                    get: function(name) {
                                        var key = Object.keys(headersObj).find(function(k) { 
                                            return k.toLowerCase() === name.toLowerCase(); 
                                        });
                                        return key ? headersObj[key] : null;
                                    }
                                },
                                text: async function() { return res.body; },
                                json: async function() { return JSON.parse(res.body); }
                            };
                            return responseObj;
                        };
                    """.trimIndent()
                    
                    evaluate<String>("$polyfill\n; 'ok';")
                    evaluate<String>("$cryptoJsCode\n; 'ok';")
                    
                    val seasonArg = season?.toString() ?: "null"
                    val episodeArg = episode?.toString() ?: "null"

                    evaluate<String>("var module = { exports: {} }; var exports = module.exports;\n; 'ok';")
                    evaluate<String>("$addonCode\n; 'ok';")
                    
                    val scriptWrapper = """
                        (async function() {
                            try {
                                var parserFunc;
                                // Addon scripts usually set a function as module.exports or define a global
                                if (typeof module.exports === 'function') {
                                    parserFunc = module.exports;
                                } else {
                                    // Snapshot existing window keys to find newly added functions
                                    // if it was added globally instead of module.exports
                                    for (var k in globalThis) {
                                        if (typeof globalThis[k] === 'function' && k !== 'console' && k !== 'atob' && k !== 'btoa' && k !== 'fetch' && k !== 'bypassCloudflare' && k !== 'CryptoJS' && k !== 'window' && k !== 'globalThis' && k !== 'module' && k !== 'exports' && k !== 'nativeLog' && k !== 'nativeError' && k !== 'nativeSend' && k !== 'nativeBtoa' && k !== 'nativeAtob' && k !== 'nativeBypass' && k !== 'nativeFetchStr') {
                                            parserFunc = globalThis[k];
                                            break;
                                        }
                                    }
                                }
                                
                                if (typeof parserFunc !== 'function' && typeof resolveFromAddon === 'function') {
                                    parserFunc = resolveFromAddon;
                                }
                                
                                if (typeof parserFunc === 'function') {
                                    var result = parserFunc("$type", "$tmdbId", $seasonArg, $episodeArg);
                                    if (result instanceof Promise) {
                                        result = await result;
                                    }
                                    nativeSend(JSON.stringify(result || []));
                                } else {
                                    nativeError("Parser is not a function");
                                    nativeSend("[]");
                                }
                            } catch (e) {
                                nativeError(e.toString());
                                nativeSend("[]");
                            }
                        })();
                    """.trimIndent()
                    
                    evaluate<String>("$scriptWrapper\n; 'ok';")
                }
            } catch (e: Exception) {
                Log.e("QuickJsAddonManager", "Error executing addon JS: ${e.message}", e)
                logBuilder?.append("NATIVE EXCEPTION: ")?.append(e.message)?.append("\n")
                if (scriptResult == null) {
                    scriptResult = "[]"
                }
            }
            
            scriptResult
        }
    }
}
