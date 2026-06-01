import java.util.regex.Pattern

fun main() {
    val text = """
        const decryptionKey = "SecureKey123!";

        function xorDecrypt(data, key) {
            let decoded = atob(data);
            let output = '';
            for (let i = 0; i < decoded.length; i++) {
                output += String.fromCharCode(decoded.charCodeAt(i) ^ key.charCodeAt(i % key.length));
            }
            return output;
        }

        (function() {
            let encrypted = "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";
    """
    var decrypted = ""
    val xorPattern = Pattern.compile("function\\s+xorDecrypt\\(data,\\s*key\\)", Pattern.CASE_INSENSITIVE)
    val encryptedPattern = Pattern.compile("(?:let|const|var)\\s+encrypted\\s*=\\s*\"(.*?)\";", Pattern.CASE_INSENSITIVE)
    val keyPattern = Pattern.compile("(?:let|const|var)\\s+decryptionKey\\s*=\\s*\"(.*?)\";", Pattern.CASE_INSENSITIVE)

    val xorMatcher = xorPattern.matcher(text)
    val encryptedMatcher = encryptedPattern.matcher(text)
    val keyMatcher = keyPattern.matcher(text)

    if (xorMatcher.find() && encryptedMatcher.find() && keyMatcher.find()) {
        val encrypted = encryptedMatcher.group(1)
        val key = keyMatcher.group(1)
        
        println("Key: " + key)

        if (encrypted != null && key != null) {
            val decodedBytes = java.util.Base64.getDecoder().decode(encrypted)
            val sb = StringBuilder()
            for (i in decodedBytes.indices) {
                val byteVal = decodedBytes[i].toInt() and 0xFF
                val keyChar = key[i % key.length].code
                val charCode = byteVal xor keyChar
                sb.append(charCode.toChar())
            }
            decrypted = sb.toString()
            println("Decrypted: " + decrypted)
        }
    } else {
        println("No match found")
        println("xor: ${xorMatcher.find()}, enc: ${encryptedMatcher.find()}, key: ${keyMatcher.find()}")
    }
}
