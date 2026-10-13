package com.streambd.iptv.utils

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
        // legacy TLS 1.0/1.1-only handshake paths. When Conscrypt is installed
        // as the top JSSE provider in MainActivity.onCreate(), this call
        // returns Conscrypt's SSLContext — giving the app the same JA3
        // fingerprint as Chrome / Android System WebView.
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }

            // ── HTTP/1.1 ONLY ─────────────────────────────────────────────
            // Some ISP-level Deep Packet Inspection (DPI) systems build a
            // client fingerprint by inspecting the HTTP/2 SETTINGS frame
            // structure (window sizes, header table size, initial stream
            // IDs, etc.). OkHttp's HTTP/2 fingerprint does NOT match a
            // browser's, so those DPI systems flag the request as an
            // "unknown automated client" and block it — even when the
            // headers look perfectly browser-like.
            //
            // HTTP/1.1 has no equivalent frame-level fingerprint. Forcing
            // HTTP/1.1 eliminates that entire class of fingerprint-based
            // blocking, at the cost of a tiny amount of protocol overhead
            // (irrelevant for IPTV workloads which are dominated by media
            // segment downloads over already-established connections).
            .protocols(listOf(Protocol.HTTP_1_1))

            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)      // raised from 30s for slow live feeds
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cookieJar(InMemoryCookieJar)

            // ── Response decompression ────────────────────────────────────
            // OkHttp normally handles gzip transparently by adding
            // `Accept-Encoding: gzip` itself and stripping it after
            // decompression. But we frequently override Accept-Encoding
            // (e.g. to advertise `gzip, deflate` when the playlist says so),
            // and OkHttp's transparent handling stops the moment the app
            // declares the header. This interceptor fills that gap.
            //
            // NOTE ON CONNECTIONS: `body.source()` returns a Closeable
            // source. When we wrap it in GzipSource/InflaterSource and
            // return a new ResponseBody, the OkHttp caller will close the
            // NEW body — which cascades into closing the wrapped source
            // and releasing the underlying connection back to the pool.
            // We must NOT call body.close() ourselves, since the new body
            // now owns the source.
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val encoding = response.header("Content-Encoding")
                val isGzip = encoding.equals("gzip", ignoreCase = true)
                val isDeflate = encoding.equals("deflate", ignoreCase = true)

                if (!isGzip && !isDeflate) {
                    return@addInterceptor response
                }

                val body = response.body ?: return@addInterceptor response

                // Strip Content-Encoding and Content-Length so the caller
                // doesn't double-decode or truncate to the wrong size.
                val strippedHeaders = response.headers.newBuilder()
                    .removeAll("Content-Encoding")
                    .removeAll("Content-Length")
                    .build()

                val decompressed = if (isGzip) {
                    GzipSource(body.source())
                } else {
                    InflaterSource(body.source(), Inflater())
                }

                // Buffer the decompressed stream so reads stay efficient.
                val bufferedSource = decompressed.buffer()

                val newBody = object : ResponseBody() {
                    override fun contentType() = body.contentType()
                    // -1 = unknown length; required because the decoded
                    // body's length is not the same as the wire length.
                    override fun contentLength() = -1L
                    override fun source() = bufferedSource
                }

                response.newBuilder()
                    .headers(strippedHeaders)
                    .body(newBody)
                    .build()
            }

            // ── Default User-Agent fallback ───────────────────────────────
            // Only applied if the caller did not already provide one. Using
            // a network interceptor (not an application interceptor) ensures
            // this runs at the transport layer — after any redirects have
            // already been resolved — so every hop gets a UA.
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val hasUserAgent = req.header("User-Agent") != null
                val next = if (hasUserAgent) {
                    req
                } else {
                    req.newBuilder()
                        .header("User-Agent", DEFAULT_USER_AGENT)
                        .build()
                }
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
                    list.removeAll {
                        it.name == newCookie.name && it.path == newCookie.path
                    }
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
