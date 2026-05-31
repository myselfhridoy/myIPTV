package com.myselfhridoy.streambdplayer.utils

import android.content.Context
import com.myselfhridoy.streambdplayer.data.models.DrmConfig
import kotlin.coroutines.resume
import java.util.regex.Pattern

data class JsDecodeResult(
    val url: String,
    val drm: DrmConfig? = null
)

object JSDecoder {

    suspend fun decode(context: Context, text: String): JsDecodeResult? {
        try {
            var decrypted = ""

            // Check if it's the XOR encrypted payload
            val xorPattern = Pattern.compile("function\\s+xorDecrypt\\(data,\\s*key\\)", Pattern.CASE_INSENSITIVE)
            val encryptedPattern = Pattern.compile("(?:let|const|var)\\s+encrypted\\s*=\\s*\"(.*?)\";", Pattern.CASE_INSENSITIVE)
            val keyPattern = Pattern.compile("(?:let|const|var)\\s+decryptionKey\\s*=\\s*\"(.*?)\";", Pattern.CASE_INSENSITIVE)

            val xorMatcher = xorPattern.matcher(text)
            val encryptedMatcher = encryptedPattern.matcher(text)
            val keyMatcher = keyPattern.matcher(text)

            if (xorMatcher.find() && encryptedMatcher.find() && keyMatcher.find()) {
                val encrypted = encryptedMatcher.group(1)
                val key = keyMatcher.group(1)

                if (encrypted != null && key != null) {
                    val decodedBytes = android.util.Base64.decode(encrypted, android.util.Base64.DEFAULT)
                    val sb = StringBuilder()
                    for (i in decodedBytes.indices) {
                        val byteVal = decodedBytes[i].toInt() and 0xFF
                        val keyChar = key[i % key.length].code
                        val charCode = byteVal xor keyChar
                        sb.append(charCode.toChar())
                    }
                    decrypted = sb.toString()
                }
            } else {
                // Check for generic eval packer
                val evalMatch = Pattern.compile("<script>(var\\s+_[0-9a-zA-Z]+=\\[.*?\\];\\s*function\\s+_[0-9a-zA-Z]+\\(.*?\\)\\{.*?\\}\\s*)eval\\((function\\(.*?\\)\\{.*?\\}\\(.*?\\))\\)\\s*</script>").matcher(text)
                if (evalMatch.find()) {
                    val setupCode = evalMatch.group(1) ?: ""
                    val evalBody = evalMatch.group(2) ?: ""
                    val script = """
                        (function() {
                            $setupCode
                            return $evalBody;
                        })();
                    """.trimIndent()
                    
                    decrypted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                            try {
                                val webView = android.webkit.WebView(context)
                                webView.settings.javaScriptEnabled = true
                                webView.evaluateJavascript(script) { result ->
                                    webView.destroy()
                                    if (continuation.isActive) {
                                        var finalResult = result
                                        if (finalResult != null && finalResult.length >= 2 && finalResult.startsWith("\"") && finalResult.endsWith("\"")) {
                                            finalResult = finalResult.substring(1, finalResult.length - 1)
                                            // Handle escaped characters returned by evaluateJavascript
                                            finalResult = finalResult.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
                                        }
                                        continuation.resume(if (finalResult == "null") "" else finalResult ?: "")
                                    }
                                }
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resume("")
                            }
                        }
                    }
                }
            }

            if (decrypted.isNotEmpty()) {
                val urlPattern = Pattern.compile("(?:const|let|var)\\s+(?:mpdUrl|url)\\s*=\\s*'(.*?)';", Pattern.CASE_INSENSITIVE)
                val kidPattern = Pattern.compile("(?:const|let)\\s+kid\\s*=\\s*'(.*?)';", Pattern.CASE_INSENSITIVE)
                val keyStrPattern = Pattern.compile("(?:const|let)\\s+key\\s*=\\s*'(.*?)';", Pattern.CASE_INSENSITIVE)

                val urlMatcher = urlPattern.matcher(decrypted)
                val kidMatcher = kidPattern.matcher(decrypted)
                val keyStrMatcher = keyStrPattern.matcher(decrypted)

                var finalUrl = ""
                if (urlMatcher.find()) {
                    finalUrl = urlMatcher.group(1) ?: ""
                } else {
                    val genericUrlPattern = Pattern.compile("https?://[^\\s'\"]+")
                    val genericMatcher = genericUrlPattern.matcher(decrypted)
                    if (genericMatcher.find()) {
                        finalUrl = genericMatcher.group(0) ?: ""
                    }
                }

                if (finalUrl.isNotEmpty()) {
                    var drm: DrmConfig? = null

                    val widevinePattern = Pattern.compile("(?:widevine|licenseUrl|licenseServer)[a-zA-Z0-9_]*\\s*[:=]\\s*['\"](https?://[^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
                    val widevineAlphaPattern = Pattern.compile("['\"]com\\.widevine\\.alpha['\"]\\s*:\\s*['\"](https?://[^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
                    val playreadyPattern = Pattern.compile("['\"]com\\.microsoft\\.playready['\"]\\s*:\\s*['\"](https?://[^'\"]+)['\"]", Pattern.CASE_INSENSITIVE)
                    
                    val w1 = widevinePattern.matcher(decrypted)
                    val w2 = widevineAlphaPattern.matcher(decrypted)
                    val p1 = playreadyPattern.matcher(decrypted)

                    if (w1.find()) {
                        drm = DrmConfig(type = "widevine", licenseServer = w1.group(1))
                    } else if (w2.find()) {
                        drm = DrmConfig(type = "widevine", licenseServer = w2.group(1))
                    } else if (p1.find()) {
                        drm = DrmConfig(type = "playready", licenseServer = p1.group(1))
                    } else if (kidMatcher.find() && keyStrMatcher.find()) {
                        drm = DrmConfig(type = "clearkey", rawKeyPair = "${kidMatcher.group(1)}:${keyStrMatcher.group(1)}")
                    } else {
                        val rawPairPattern = Pattern.compile("([a-fA-F0-9]{32}):([a-fA-F0-9]{32})")
                        val rawPairMatcher = rawPairPattern.matcher(decrypted)
                        if (rawPairMatcher.find()) {
                            drm = DrmConfig(type = "clearkey", rawKeyPair = "${rawPairMatcher.group(1)}:${rawPairMatcher.group(2)}")
                        }
                    }

                    return JsDecodeResult(url = finalUrl, drm = drm)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
