package com.myselfhridoy.streambdplayer.utils

import android.util.Base64

object Base64Utils {
    fun decode(base64String: String): String {
        return try {
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    fun encode(plainText: String): String {
        return try {
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
