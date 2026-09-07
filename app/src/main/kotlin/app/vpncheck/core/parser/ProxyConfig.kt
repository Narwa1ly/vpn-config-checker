package app.vpncheck.core.parser

import java.security.MessageDigest

enum class Protocol(val scheme: String, val label: String) {
    VLESS("vless", "VLESS"),
    VMESS("vmess", "VMess"),
    TROJAN("trojan", "Trojan"),
    SHADOWSOCKS("ss", "Shadowsocks"),
    HYSTERIA2("hysteria2", "Hysteria2");

    companion object {
        fun fromScheme(scheme: String): Protocol? = when (scheme.lowercase()) {
            "vless" -> VLESS
            "vmess" -> VMESS
            "trojan" -> TROJAN
            "ss" -> SHADOWSOCKS
            "hysteria2", "hy2" -> HYSTERIA2
            else -> null
        }
    }
}

enum class CoreType { XRAY, SINGBOX }

/** Transport / TLS settings shared by Xray-family protocols. */
data class StreamSettings(
    val network: String = "tcp",
    val security: String = "none",
    val sni: String? = null,
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList(),
    val allowInsecure: Boolean = false,
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val path: String? = null,
    val host: String? = null,
    val serviceName: String? = null,
    val mode: String? = null,
    val headerType: String? = null,
)

sealed class ProxyConfig {
    abstract val rawLink: String
    abstract val name: String
    abstract val host: String
    abstract val port: Int
    abstract val protocol: Protocol

    /** Stable identifier: SHA-256 of the raw link. */
    val id: String by lazy { sha256(rawLink) }

    /**
     * Which core runs this config.
     * - hysteria2: only sing-box.
     * - shadowsocks: sing-box (Xray dropped legacy stream ciphers such as aes-256-cfb / chacha20-ietf).
     * - xhttp / splithttp transport: only Xray.
     * - TLS with allowInsecure: sing-box (Xray-core v26.2.6+ refuses to load `allowInsecure`).
     * - everything else: Xray.
     */
    val core: CoreType
        get() {
            val stream = streamOrNull()
            return when {
                protocol == Protocol.HYSTERIA2 -> CoreType.SINGBOX
                protocol == Protocol.SHADOWSOCKS -> CoreType.SINGBOX
                stream != null && stream.network in XHTTP_NETWORKS -> CoreType.XRAY
                stream != null && stream.allowInsecure && stream.security == "tls" -> CoreType.SINGBOX
                else -> CoreType.XRAY
            }
        }

    fun streamOrNull(): StreamSettings? = when (this) {
        is Vless -> stream
        is Vmess -> stream
        is Trojan -> stream
        else -> null
    }

    open val transport: String get() = "tcp"
    open val security: String get() = "none"

    data class Vless(
        override val rawLink: String,
        override val name: String,
        override val host: String,
        override val port: Int,
        val uuid: String,
        val flow: String?,
        val encryption: String,
        val stream: StreamSettings,
    ) : ProxyConfig() {
        override val protocol get() = Protocol.VLESS
        override val transport get() = stream.network
        override val security get() = stream.security
    }

    data class Vmess(
        override val rawLink: String,
        override val name: String,
        override val host: String,
        override val port: Int,
        val uuid: String,
        val alterId: Int,
        val cipher: String,
        val stream: StreamSettings,
    ) : ProxyConfig() {
        override val protocol get() = Protocol.VMESS
        override val transport get() = stream.network
        override val security get() = stream.security
    }

    data class Trojan(
        override val rawLink: String,
        override val name: String,
        override val host: String,
        override val port: Int,
        val password: String,
        val flow: String?,
        val stream: StreamSettings,
    ) : ProxyConfig() {
        override val protocol get() = Protocol.TROJAN
        override val transport get() = stream.network
        override val security get() = stream.security
    }

    data class Shadowsocks(
        override val rawLink: String,
        override val name: String,
        override val host: String,
        override val port: Int,
        val method: String,
        val password: String,
        val plugin: String?,
    ) : ProxyConfig() {
        override val protocol get() = Protocol.SHADOWSOCKS
    }

    data class Hysteria2(
        override val rawLink: String,
        override val name: String,
        override val host: String,
        override val port: Int,
        val password: String,
        val sni: String?,
        val insecure: Boolean,
        val obfs: String?,
        val obfsPassword: String?,
        val upMbps: Int?,
        val downMbps: Int?,
        val alpn: List<String>,
    ) : ProxyConfig() {
        override val protocol get() = Protocol.HYSTERIA2
        override val transport get() = "udp"
        override val security get() = "tls"
    }

    /** Null when both cores can run the config; otherwise a short reason. */
    fun unsupportedReason(): String? = when (this) {
        is Shadowsocks -> if (!plugin.isNullOrBlank()) "SS plugin ($plugin) не поддерживается" else null
        else -> null
    }

    companion object {
        val XHTTP_NETWORKS = setOf("xhttp", "splithttp")

        fun sha256(s: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            return d.joinToString("") { "%02x".format(it) }
        }
    }
}
