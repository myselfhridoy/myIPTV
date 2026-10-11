package com.streambd.iptv.utils

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.GzipSource
import okio.InflaterSource
import okio.buffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object UnsafeOkHttpClient {

    /** Default whitelisted media-player UA (VLC + OTT Navigator hybrid). */
    const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20 (OTT Navigator; TiviMate)"

    // ─────────────────────────────────────────────────────────────────────────
    // SAFE client — for GitHub / Firebase / any trusted host.
    // Uses the platform TLS stack + standard certificate validation.
    // ─────────────────────────────────────────────────────────────────────────
    val safe: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UNSAFE client — trust-all TLS + custom UA injection + cookie jar.
    // Only use for IPTV streams from misconfigured servers.
    // ─────────────────────────────────────────────────────────────────────────
    private val unsafe: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        buildUnsafeClient()
    }

    fun getUnsafeOkHttpClient(): OkHttpClient = unsafe

    private fun buildUnsafeClient(): OkHttpClient {
        val trustAll = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )

        // "TLS" (not "SSL") — Android's TLS provider is a superset and avoids
        // legacy TLS 1.0/1.1-only handshake paths.
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)      // raised from 30s for slow live feeds
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .pingInterval(10, TimeUnit.SECONDS)     // keeps live TCP sessions warm
            .cookieJar(InMemoryCookieJar)
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val encoding = response.header("Content-Encoding")
                if (encoding.equals("gzip", ignoreCase = true) || encoding.equals("deflate", ignoreCase = true)) {
                    val body = response.body
                    if (body != null) {
                        val source = if (encoding.equals("gzip", ignoreCase = true)) {
                            GzipSource(body.source())
                        } else {
                            InflaterSource(body.source(), Inflater())
                        }
                        val strippedHeaders = response.headers.newBuilder()
                            .removeAll("Content-Encoding")
                            .removeAll("Content-Length")
                            .build()
                        val bufferedSource = source.buffer()
                        val newBody = object : ResponseBody() {
                            override fun contentType() = body.contentType()
                            override fun contentLength() = -1L
                            override fun source() = bufferedSource
                        }
                        return@addInterceptor response.newBuilder()
                            .headers(strippedHeaders)
                            .body(newBody)
                            .build()
                    }
                }
                response
            }
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val hasUserAgent = req.header("User-Agent") != null ||
                    req.header("user-agent") != null
                val next = if (hasUserAgent) req
                else req.newBuilder().header("User-Agent", DEFAULT_USER_AGENT).build()
                chain.proceed(next)
            }
            .build()
    }

    /** Simple thread-safe in-memory cookie jar scoped to host. */
    private object InMemoryCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { Collections.synchronizedList(mutableListOf()) }
            synchronized(list) {
                cookies.forEach { newCookie ->
                    list.removeAll { it.name == newCookie.name && it.path == newCookie.path }
                    list.add(newCookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = store[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            synchronized(list) {
                return list.filter { it.expiresAt > now && it.matches(url) }
            }
        }
    }
}
