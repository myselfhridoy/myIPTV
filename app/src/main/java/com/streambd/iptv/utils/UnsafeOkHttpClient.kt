package com.streambd.iptv.utils

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object UnsafeOkHttpClient {
    // Default Whitelisted Media Player User-Agent (VLC & OTT Navigator hybrid)
    const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20 (OTT Navigator; TiviMate)"

    private var instance: OkHttpClient? = null

    fun getUnsafeOkHttpClient(): OkHttpClient {
        if (instance != null) return instance!!
        return try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            // Install the all-trusting trust manager
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())

            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory

            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
            builder.followRedirects(true)
            builder.followSslRedirects(true)
            builder.connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            builder.readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            builder.writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            builder.retryOnConnectionFailure(true)
            builder.pingInterval(10, java.util.concurrent.TimeUnit.SECONDS) // Keeps live stream TCP connections alive

            builder.cookieJar(object : okhttp3.CookieJar {
                private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()

                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                    val host = url.host
                    val list = cookieStore.getOrPut(host) { java.util.concurrent.CopyOnWriteArrayList() }
                    cookies.forEach { newCookie ->
                        list.removeAll { it.name == newCookie.name && it.path == newCookie.path }
                        list.add(newCookie)
                    }
                }

                override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                    val list = cookieStore[url.host] ?: return emptyList()
                    val validCookies = mutableListOf<okhttp3.Cookie>()
                    val now = System.currentTimeMillis()
                    list.forEach { c ->
                        if (c.expiresAt > now && c.matches(url)) {
                            validCookies.add(c)
                        }
                    }
                    return validCookies
                }
            })

            // Automatically inject Whitelisted Media Player User-Agent across all network requests including redirects
            builder.addNetworkInterceptor { chain ->
                val originalRequest = chain.request()
                val hasUserAgent = originalRequest.header("User-Agent") != null || originalRequest.header("user-agent") != null
                val newRequest = if (!hasUserAgent) {
                    originalRequest.newBuilder()
                        .header("User-Agent", DEFAULT_USER_AGENT)
                        .build()
                } else {
                    originalRequest
                }
                chain.proceed(newRequest)
            }

            instance = builder.build()
            instance!!
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
