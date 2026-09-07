package app.vpncheck.core.engine

import app.vpncheck.core.parser.ProxyConfig
import app.vpncheck.core.parser.StreamSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a sing-box JSON config with a local mixed (HTTP+SOCKS) inbound and one proxy outbound.
 * Handles hysteria2 (Xray has no support) and TLS configs with `allowInsecure`, which Xray-core
 * v26.2.6+ refuses to load. Supports vless / vmess / trojan / shadowsocks over tcp, ws, grpc, h2, httpupgrade.
 */
object SingBoxConfigBuilder {

    fun build(config: ProxyConfig, localPort: Int, serverAddress: String = config.host): JSONObject {
        val outbound = when (config) {
            is ProxyConfig.Hysteria2 -> hysteria2(config, serverAddress)
            is ProxyConfig.Vless -> vless(config, serverAddress)
            is ProxyConfig.Vmess -> vmess(config, serverAddress)
            is ProxyConfig.Trojan -> trojan(config, serverAddress)
            is ProxyConfig.Shadowsocks -> shadowsocks(config, serverAddress)
        }
        return JSONObject()
            .put("log", JSONObject().put("level", "warn").put("timestamp", false))
            .put(
                "inbounds", JSONArray().put(
                    JSONObject()
                        .put("type", "mixed")
                        .put("tag", "mixed-in")
                        .put("listen", "127.0.0.1")
                        .put("listen_port", localPort)
                )
            )
            .put(
                "outbounds", JSONArray()
                    .put(outbound.put("tag", "proxy"))
                    .put(JSONObject().put("type", "direct").put("tag", "direct"))
            )
    }

    private fun hysteria2(c: ProxyConfig.Hysteria2, address: String): JSONObject {
        val o = JSONObject()
            .put("type", "hysteria2")
            .put("server", address)
            .put("server_port", c.port)
            .put("password", c.password)
        c.upMbps?.takeIf { it > 0 }?.let { o.put("up_mbps", it) }
        c.downMbps?.takeIf { it > 0 }?.let { o.put("down_mbps", it) }
        if (!c.obfs.isNullOrBlank()) {
            o.put("obfs", JSONObject().put("type", c.obfs).put("password", c.obfsPassword ?: ""))
        }
        val tls = JSONObject()
            .put("enabled", true)
            .put("server_name", c.sni?.takeIf { it.isNotBlank() } ?: c.host)
            .put("insecure", c.insecure)
        if (c.alpn.isNotEmpty()) tls.put("alpn", JSONArray(c.alpn))
        o.put("tls", tls)
        return o
    }

    private fun vless(c: ProxyConfig.Vless, address: String): JSONObject {
        val o = JSONObject()
            .put("type", "vless")
            .put("server", address)
            .put("server_port", c.port)
            .put("uuid", c.uuid)
        c.flow?.takeIf { it.isNotBlank() }?.let { o.put("flow", it) }
        applyStream(o, c.stream, c.host)
        return o
    }

    private fun vmess(c: ProxyConfig.Vmess, address: String): JSONObject {
        val o = JSONObject()
            .put("type", "vmess")
            .put("server", address)
            .put("server_port", c.port)
            .put("uuid", c.uuid)
            .put("security", c.cipher.ifEmpty { "auto" })
            .put("alter_id", c.alterId)
        applyStream(o, c.stream, c.host)
        return o
    }

    private fun trojan(c: ProxyConfig.Trojan, address: String): JSONObject {
        val o = JSONObject()
            .put("type", "trojan")
            .put("server", address)
            .put("server_port", c.port)
            .put("password", c.password)
        // Trojan in sing-box has no flow; TLS is implied by protocol but we still emit settings.
        applyStream(o, c.stream.copy(security = if (c.stream.security == "none") "tls" else c.stream.security), c.host)
        return o
    }

    private fun shadowsocks(c: ProxyConfig.Shadowsocks, address: String): JSONObject = JSONObject()
        .put("type", "shadowsocks")
        .put("server", address)
        .put("server_port", c.port)
        .put("method", c.method)
        .put("password", c.password)

    internal fun applyStream(o: JSONObject, s: StreamSettings, originalHost: String) {
        val hostHeader = s.host?.takeIf { it.isNotBlank() } ?: originalHost
        val sni = s.sni?.takeIf { it.isNotBlank() } ?: s.host?.takeIf { it.isNotBlank() } ?: originalHost

        when (s.security) {
            "tls", "reality" -> {
                val tls = JSONObject()
                    .put("enabled", true)
                    .put("server_name", sni)
                    .put("insecure", s.allowInsecure && s.security == "tls")
                if (s.alpn.isNotEmpty() && s.security == "tls") tls.put("alpn", JSONArray(s.alpn))
                tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", s.fingerprint?.takeIf { it.isNotBlank() } ?: "chrome"))
                if (s.security == "reality") {
                    tls.put("reality", JSONObject().put("enabled", true).put("public_key", s.publicKey ?: "").put("short_id", s.shortId ?: ""))
                }
                o.put("tls", tls)
            }
        }

        val transport: JSONObject? = when (s.network) {
            "ws" -> JSONObject()
                .put("type", "ws")
                .put("path", s.path ?: "/")
                .put("headers", JSONObject().put("Host", hostHeader))
            "grpc" -> JSONObject()
                .put("type", "grpc")
                .put("service_name", s.serviceName ?: s.path?.trimStart('/') ?: "")
            "h2" -> JSONObject()
                .put("type", "http")
                .put("host", JSONArray().put(hostHeader))
                .put("path", s.path ?: "/")
            "httpupgrade" -> JSONObject()
                .put("type", "httpupgrade")
                .put("host", hostHeader)
                .put("path", s.path ?: "/")
            "xhttp", "splithttp" -> throw IllegalArgumentException("xhttp is only supported by Xray")
            else -> null
        }
        if (transport != null) o.put("transport", transport)
    }
}
