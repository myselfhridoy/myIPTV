package com.myselfhridoy.streambdplayer.utils

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    /**
     * Decrypts AES-CBC with NoPadding using Hex encoded strings.
     * Equivalent to decrypt_slowAES in JS.
     */
    fun decryptSlowAES(encryptedHex: String, keyHex: String, ivHex: String): String {
        return try {
            val encryptedBytes = hexStringToByteArray(encryptedHex)
            val keyBytes = hexStringToByteArray(keyHex)
            val ivBytes = hexStringToByteArray(ivHex)

            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")
            val ivParameterSpec = IvParameterSpec(ivBytes)
            
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            
            byteArrayToHexString(decryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val sb = java.lang.StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
