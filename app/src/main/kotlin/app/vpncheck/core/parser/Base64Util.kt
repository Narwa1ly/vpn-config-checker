package app.vpncheck.core.parser

import java.util.Base64

object Base64Util {
    /** Decodes standard or URL-safe base64, tolerating missing padding and whitespace. Returns null if invalid. */
    fun decodeLenient(input: String): ByteArray? {
        var s = input.trim().replace(Regex("\\s+"), "")
        if (s.isEmpty()) return null
        s = s.replace('-', '+').replace('_', '/')
        val pad = (4 - s.length % 4) % 4
        if (pad == 3) return null
        s += "=".repeat(pad)
        return try {
            Base64.getDecoder().decode(s)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun decodeToString(input: String): String? = decodeLenient(input)?.toString(Charsets.UTF_8)

    fun looksLikeBase64(input: String): Boolean {
        val s = input.trim()
        return s.isNotEmpty() && Regex("^[A-Za-z0-9+/=_\\-\\s]+$").matches(s)
    }
}
