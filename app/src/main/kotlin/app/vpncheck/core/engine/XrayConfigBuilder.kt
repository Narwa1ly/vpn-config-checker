package app.vpncheck.core.engine

import app.vpncheck.core.parser.ProxyConfig
import app.vpncheck.core.parser.StreamSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds an Xray-core JSON config with a local HTTP inbound and a single proxy outbound.
 *
 * @param serverAddress address the core should dial (may be a pre-resolved IP; the original
 *        host name is still used for SNI / Host headers when the link did not set them).
 */
object XrayConfigBuilder {

    fun build(config: ProxyConfig, localPort: Int, serverAddress: String = config.host): JSONObject {
        val outbound = when (config) {
            is ProxyConfig.Vless -> vless(config, serverAddress)
            is ProxyConfig.Vmess -> vmess(config, serverAddress)
            is ProxyConfig.Trojan -> trojan(config, serverAddress)
            is ProxyConfig.Shadowsocks -> shadowsocks(config, serverAddress)
            is ProxyConfig.Hysteria2 -> throw IllegalArgumentException("hysteria2 is handled by sing-box")
        }
        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("servers", JSONArray(listOf("77.88.8.8", "8.8.8.8", "1.1.1.1"))))
            .put(
                "inbounds", JSONArray().put(
                    JSONObject()
                        .put("tag", "http-in")
                        .put("listen", "127.0.0.1")
                        .put("port", localPort)
                        .put("protocol", "http")
                        .put("settings", JSONObject().put("allowTransparent", false))
                )
            )
            .put(
                "outbounds", JSONArray()
                    .put(outbound.put("tag", "proxy"))
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            )
    }

    private fun vless(c: ProxyConfig.Vless, address: String): JSONObject {
        val user = JSONObject().put("id", c.uuid).put("encryption", c.encryption.ifEmpty { "none" })
        c.flow?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
        return JSONObject()
            .put("protocol", "vless")
            .put(
                "settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject().put("address", address).put("port", c.port).put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", stream(c.stream, c.host))
    }

    private fun vmess(c: ProxyConfig.Vmess, address: String): JSONObject {
        val user = JSONObject().put("id", c.uuid).put("alterId", c.alterId).put("security", c.cipher.ifEmpty { "auto" })
        return JSONObject()
            .put("protocol", "vmess")
            .put(
                "settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject().put("address", address).put("port", c.port).put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", stream(c.stream, c.host))
    }

    private fun trojan(c: ProxyConfig.Trojan, address: String): JSONObject {
        val server = JSONObject().put("address", address).put("port", c.port).put("password", c.password)
        c.flow?.takeIf { it.isNotBlank() }?.let { server.put("flow", it) }
        return JSONObject()
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", stream(c.stream, c.host))
    }

    private fun shadowsocks(c: ProxyConfig.Shadowsocks, address: String): JSONObject {
        val server = JSONObject()
            .put("address", address)
            .put("port", c.port)
            .put("method", c.method)
            .put("password", c.password)
        return JSONObject()
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", JSONObject().put("network", "tcp").put("security", "none"))
    }

    /** [originalHost] is the host from the share link; used for SNI / Host when the link has none. */
    internal fun stream(s: StreamSettings, originalHost: String): JSONObject {
        val network = when (s.network) {
            "", "tcp", "raw" -> "tcp"
            else -> s.network
        }
        val o = JSONObject().put("network", network).put("security", s.security)
        val hostHeader = s.host?.takeIf { it.isNotBlank() } ?: originalHost
        val sni = s.sni?.takeIf { it.isNotBlank() } ?: s.host?.takeIf { it.isNotBlank() } ?: originalHost

        when (s.security) {
            "tls" -> {
                // Xray-core v26.2.6+ rejects `allowInsecure`; such configs are routed to sing-box instead.
                val tls = JSONObject().put("serverName", sni)
                tls.put("fingerprint", s.fingerprint?.takeIf { it.isNotBlank() } ?: "chrome")
                if (s.alpn.isNotEmpty()) tls.put("alpn", JSONArray(s.alpn))
                o.put("tlsSettings", tls)
            }
            "reality" -> {
                val r = JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", s.fingerprint?.takeIf { it.isNotBlank() } ?: "chrome")
                    .put("publicKey", s.publicKey ?: "")
                    .put("shortId", s.shortId ?: "")
                    .put("spiderX", s.spiderX ?: "")
                o.put("realitySettings", r)
            }
        }

        when (network) {
            "ws" -> {
                val ws = JSONObject().put("path", s.path ?: "/").put("host", hostHeader)
                ws.put("headers", JSONObject().put("Host", hostHeader))
                o.put("wsSettings", ws)
            }
            "xhttp", "splithttp" -> {
                o.put("network", "xhttp")
                val x = JSONObject().put("path", s.path ?: "/").put("host", hostHeader)
                x.put("mode", s.mode?.takeIf { it.isNotBlank() } ?: "auto")
                o.put("xhttpSettings", x)
            }
            "httpupgrade" -> {
                o.put("httpupgradeSettings", JSONObject().put("path", s.path ?: "/").put("host", hostHeader))
            }
            "grpc" -> {
                val g = JSONObject().put("serviceName", s.serviceName ?: s.path?.trimStart('/') ?: "")
                g.put("multiMode", s.mode == "multi")
                o.put("grpcSettings", g)
            }
            "h2" -> {
                o.put("httpSettings", JSONObject().put("path", s.path ?: "/").put("host", JSONArray().put(hostHeader)))
            }
            "tcp" -> {
                if (s.headerType == "http") {
                    val req = JSONObject()
                        .put("path", JSONArray().put(s.path ?: "/"))
                        .put("headers", JSONObject().put("Host", JSONArray().put(hostHeader)))
                    o.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http").put("request", req)))
                }
            }
            "kcp" -> {
                val k = JSONObject()
                if (s.headerType != null) k.put("header", JSONObject().put("type", s.headerType))
                s.path?.let { k.put("seed", it) }
                o.put("kcpSettings", k)
            }
        }
        return o
    }
}
