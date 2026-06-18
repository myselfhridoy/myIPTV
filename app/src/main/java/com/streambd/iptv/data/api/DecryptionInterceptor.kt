package com.streambd.iptv.data.api

import com.streambd.iptv.utils.CryptoUtils
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.nio.charset.Charset

class DecryptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val responseBody = response.body ?: return response
        
        // Buffer the entire body
        val source = responseBody.source()
        source.request(Long.MAX_VALUE)
        val buffer = source.buffer

        val charset = responseBody.contentType()?.charset(Charset.forName("UTF-8")) ?: Charset.forName("UTF-8")
        val responseString = buffer.clone().readString(charset).trim()

        // Plain Text Check: If it looks like JSON, return the original response
        if (responseString.startsWith("{") || responseString.startsWith("[")) {
            return response
        }

        // Encrypted Check: Doesn't look like JSON, try to decrypt
        try {
            val decryptedString = CryptoUtils.decryptJson(responseString)
            
            // Re-check if the decrypted string is actually JSON
            if (decryptedString.trim().startsWith("{") || decryptedString.trim().startsWith("[")) {
                val newResponseBody = decryptedString.toResponseBody(responseBody.contentType())
                return response.newBuilder().body(newResponseBody).build()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If decryption fails or decrypted data is still not JSON, return original response
        return response
    }
}
