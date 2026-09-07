package app.vpncheck.data.subscription

import app.vpncheck.core.parser.Base64Util

/** Turns a subscription body (plain text or base64) into a list of share links. */
object SubscriptionParser {

    private val SCHEMES = listOf("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://", "ssr://", "tuic://", "wireguard://")

    fun extractLinks(body: String): List<String> {
        val text = body.trim()
        if (text.isEmpty()) return emptyList()
        val plain = linesToLinks(text)
        if (plain.isNotEmpty()) return plain
        if (Base64Util.looksLikeBase64(text)) {
            val decoded = Base64Util.decodeToString(text) ?: return emptyList()
            return linesToLinks(decoded)
        }
        return emptyList()
    }

    private fun linesToLinks(text: String): List<String> {
        val out = LinkedHashSet<String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim().trimEnd('\r')
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            if (SCHEMES.any { line.startsWith(it, ignoreCase = true) }) out.add(line)
        }
        return out.toList()
    }

    /** Returns the "# profile-title: ..." header if present. */
    fun profileTitle(body: String): String? =
        body.lineSequence().take(10)
            .firstOrNull { it.trimStart().startsWith("# profile-title:") }
            ?.substringAfter(":")?.trim()?.ifEmpty { null }
}
