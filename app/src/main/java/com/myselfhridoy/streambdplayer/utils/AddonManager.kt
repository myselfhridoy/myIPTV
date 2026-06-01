package com.myselfhridoy.streambdplayer.utils

import android.content.Context
import android.util.Log
import app.cash.quickjs.QuickJs
import com.myselfhridoy.streambdplayer.data.models.Addon
import com.myselfhridoy.streambdplayer.data.models.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.concurrent.TimeUnit

interface AndroidBridge {
    fun fetchSync(url: String, optionsJson: String): String
    fun onResult(resultJson: String)
    fun onError(error: String)
}

object AddonManager {
    private const val DEFAULT_REGISTRY_URL = "https://streambd-iptv.netlify.app/addons/StreamBD_IPTV_Addon.json"
    private var cachedAddons: List<Addon>? = null
    private val addonScriptsCache = mutableMapOf<String, String>()
    
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getAddons(forceSync: Boolean = false): List<Addon> = withContext(Dispatchers.IO) {
        if (!forceSync && cachedAddons != null) {
            return@withContext cachedAddons!!
        }
        
        try {
            val response = URL(DEFAULT_REGISTRY_URL).readText()
            val addons: List<Addon> = json.decodeFromString(response)
            cachedAddons = addons
            return@withContext addons
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    suspend fun resolveFromAddons(
        context: Context,
        type: String, // "movie" or "tv"
        tmdbId: Int,
        season: Int? = null,
        episode: Int? = null
    ): List<StreamSource> = withContext(Dispatchers.IO) {
        val addons = getAddons().filter { it.enabled }
        val allSources = mutableListOf<StreamSource>()

        // Load CryptoJS once
        val cryptoJsCode = try {
            context.assets.open("crypto-js.min.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }

        for (addon in addons) {
            try {
                var jsCode = addonScriptsCache[addon.id]
                if (jsCode == null) {
                    jsCode = URL(addon.url).readText()
                    addonScriptsCache[addon.id] = jsCode
                }

                val sources = executeJsInQuickJs(cryptoJsCode, jsCode, type, tmdbId, season, episode)
                val mappedSources = sources.map { s -> 
                    s.copy(provider = s.provider.ifEmpty { addon.name }) 
                }
                allSources.addAll(mappedSources)
            } catch (e: Exception) {
                Log.e("AddonManager", "Error executing addon ${addon.name}: ${e.message}")
            }
        }
        return@withContext allSources
    }

    private fun executeJsInQuickJs(
        cryptoJsCode: String,
        addonJsCode: String,
        type: String,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): List<StreamSource> {
        val quickJs = QuickJs.create()
        var resultList = emptyList<StreamSource>()
        var errorMsg: String? = null

        try {
            val bridge = object : AndroidBridge {
                override fun fetchSync(url: String, optionsJson: String): String {
                    try {
                        val requestBuilder = Request.Builder().url(url)
                        val opts = json.parseToJsonElement(optionsJson).jsonObject
                        
                        // Parse method
                        val method = opts["method"]?.jsonPrimitive?.content?.uppercase() ?: "GET"
                        
                        // Parse headers
                        opts["headers"]?.jsonObject?.forEach { (key, value) ->
                            requestBuilder.header(key, value.jsonPrimitive.content)
                        }

                        // Parse body
                        var reqBody: okhttp3.RequestBody? = null
                        if (opts.containsKey("body")) {
                            val bodyContent = opts["body"]?.jsonPrimitive?.content ?: ""
                            val contentType = opts["headers"]?.jsonObject?.get("Content-Type")?.jsonPrimitive?.content ?: "application/x-www-form-urlencoded"
                            reqBody = bodyContent.toRequestBody(contentType.toMediaTypeOrNull())
                        }
                        
                        // Ensure body exists for POST/PUT if empty
                        if ((method == "POST" || method == "PUT") && reqBody == null) {
                            reqBody = "".toRequestBody(null)
                        }

                        requestBuilder.method(method, reqBody)
                        
                        val response = httpClient.newCall(requestBuilder.build()).execute()
                        val resBody = response.body?.string() ?: ""
                        
                        // Return JSON string representing the fetch response
                        val resObj = mapOf(
                            "code" to response.code.toString(),
                            "body" to resBody,
                            "url" to response.request.url.toString()
                        )
                        return json.encodeToString(resObj)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val errObj = mapOf("code" to "500", "body" to "", "error" to (e.message ?: "Network error"))
                        return json.encodeToString(errObj)
                    }
                }

                override fun onResult(resultJson: String) {
                    try {
                        resultList = json.decodeFromString(resultJson)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onError(error: String) {
                    errorMsg = error
                }
            }

            quickJs.set("AndroidBridge", AndroidBridge::class.java, bridge)

            // Polyfill environment for CryptoJS and Fetch
            val envPolyfill = """
                var window = globalThis;
                var global = globalThis;
                
                // Fetch Polyfill
                globalThis.fetch = function(url, options) {
                    try {
                        var optsStr = options ? JSON.stringify(options) : "{}";
                        var resStr = AndroidBridge.fetchSync(url, optsStr);
                        var res = JSON.parse(resStr);
                        
                        var ok = parseInt(res.code) >= 200 && parseInt(res.code) < 300;
                        return Promise.resolve({
                            ok: ok,
                            status: parseInt(res.code),
                            url: res.url,
                            json: function() { 
                                return Promise.resolve(JSON.parse(res.body)); 
                            },
                            text: function() { 
                                return Promise.resolve(res.body); 
                            }
                        });
                    } catch(e) {
                        return Promise.reject(e);
                    }
                };
                
                // Helper to execute Promises
                function executeAddon(addonCodeStr, type, tmdbId, season, episode) {
                    try {
                        var parserFunc = new Function("CryptoJS", addonCodeStr)(globalThis.CryptoJS);
                        var p = parserFunc(type, String(tmdbId), season, episode);
                        if (p && typeof p.then === 'function') {
                            p.then(function(res) {
                                AndroidBridge.onResult(JSON.stringify(res || []));
                            }).catch(function(err) {
                                AndroidBridge.onError(err.message);
                            });
                        } else {
                            AndroidBridge.onResult(JSON.stringify(p || []));
                        }
                    } catch(e) {
                        AndroidBridge.onError(e.message);
                    }
                }
            """.trimIndent()
            
            quickJs.evaluate(envPolyfill)
            
            if (cryptoJsCode.isNotEmpty()) {
                quickJs.evaluate(cryptoJsCode)
            }

            // Execute addon logic
            val escapeCode = addonJsCode.replace("\"", "\\\"").replace("\n", "\\n")
            val runScript = """
                executeAddon("$escapeCode", "$type", "$tmdbId", ${season ?: "undefined"}, ${episode ?: "undefined"});
            """.trimIndent()
            
            quickJs.evaluate(runScript)
            
            // Note: Since fetch is synchronous in our polyfill, Promise callbacks (microtasks) 
            // usually execute immediately or pending tasks can be forced. QuickJS executes pending jobs when evaluating.

            errorMsg?.let { throw Exception("Addon JS Error: $it") }
            
        } finally {
            quickJs.close()
        }
        
        return resultList
    }
}
