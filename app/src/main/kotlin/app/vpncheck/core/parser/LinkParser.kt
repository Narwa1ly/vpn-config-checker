package app.vpncheck.core.parser

import org.json.JSONObject
import java.net.URLDecoder

class ParseException(message: String) : Exception(message)

/**
 * Parses share links (vless://, vmess://, trojan://, ss://, hysteria2://, hy2://) into [ProxyConfig].
 * Does not use java.net.URI because share links routinely violate RFC 3986.
 */
object LinkParser {

    fun parse(link: String): Result<ProxyConfig> = runCatching { parseOrThrow(link.trim()) }

    fun parseOrThrow(link: String): ProxyConfig {
        val schemeEnd = link.indexOf("://")
        if (schemeEnd <= 0) throw ParseException("no scheme")
        val scheme = link.substring(0, schemeEnd).lowercase()
        val protocol = Protocol.fromScheme(scheme) ?: throw ParseException("unsupported scheme: $scheme")
        val rest = link.substring(schemeEnd + 3)
        return when (protocol) {
            Protocol.VMESS -> parseVmess(link, rest)
            Protocol.VLESS -> parseVless(link, Uri.parse(rest))
            Protocol.TROJAN -> parseTrojan(link, Uri.parse(rest))
            Protocol.SHADOWSOCKS -> parseShadowsocks(link, rest)
            Protocol.HYSTERIA2 -> parseHysteria2(link, Uri.parse(rest))
        }
    }

    // ---- URI pieces -------------------------------------------------------------------------

    internal data class Uri(
        val userInfo: String,
        val host: String,
        val port: Int,
        val path: String,
        val query: Map<String, String>,
        val fragment: String,
    ) {
        fun q(vararg keys: String): String? {
            for (k in keys) query[k]?.takeIf { it.isNotEmpty() }?.let { return it }
            return null
        }

        companion object {
            fun parse(rest: String): Uri {
                var s = rest
                var fragment = ""
                val hash = s.indexOf('#')
                if (hash >= 0) {
                    fragment = decode(s.substring(hash + 1))
                    s = s.substring(0, hash)
                }
                var query = emptyMap<String, String>()
                val qm = s.indexOf('?')
                if (qm >= 0) {
                    query = parseQuery(s.substring(qm + 1))
                    s = s.substring(0, qm)
                }
                var path = ""
                val at = s.lastIndexOf('@')
                val userInfo = if (at >= 0) decode(s.substring(0, at)) else ""
                var hostPort = if (at >= 0) s.substring(at + 1) else s
                val slash = hostPort.indexOf('/')
                if (slash >= 0) {
                    path = hostPort.substring(slash)
                    hostPort = hostPort.substring(0, slash)
                }
                val (host, port) = splitHostPort(hostPort)
                return Uri(userInfo, host, port, path, query, fragment)
            }

            fun splitHostPort(hostPort: String): Pair<String, Int> {
                if (hostPort.startsWith("[")) {
                    val close = hostPort.indexOf(']')
                    if (close < 0) throw ParseException("bad ipv6 host")
                    val host = hostPort.substring(1, close)
                    val portStr = hostPort.substring(close + 1).removePrefix(":")
                    return host to parsePort(portStr)
                }
                val colon = hostPort.lastIndexOf(':')
                if (colon < 0) throw ParseException("no port in '$hostPort'")
                return hostPort.substring(0, colon) to parsePort(hostPort.substring(colon + 1))
            }

            /** Accepts "443", "443,8443", "20000-30000" (port hopping) -> first port. */
            fun parsePort(raw: String): Int {
                val first = raw.split(',', '-', '/').firstOrNull { it.isNotBlank() } ?: throw ParseException("no port")
                val p = first.trim().toIntOrNull() ?: throw ParseException("bad port '$raw'")
                if (p !in 1..65535) throw ParseException("port out of range: $p")
                return p
            }

            fun parseQuery(q: String): Map<String, String> {
                if (q.isEmpty()) return emptyMap()
                val map = LinkedHashMap<String, String>()
                for (pair in q.split('&')) {
                    if (pair.isEmpty()) continue
                    val eq = pair.indexOf('=')
                    val k = if (eq >= 0) pair.substring(0, eq) else pair
                    val v = if (eq >= 0) pair.substring(eq + 1) else ""
                    map[decode(k)] = decode(v)
                }
                return map
            }

            fun decode(s: String): String = try {
                // Share links do not use '+' for space; preserve it.
                URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                s
            }
        }
    }

    // ---- protocols --------------------------------------------------------------------------

    private fun streamFromQuery(u: Uri, defaultSecurity: String = "none"): StreamSettings {
        val network = (u.q("type", "net") ?: "tcp").lowercase().let { if (it == "http") "h2" else it }
        var security = (u.q("security") ?: defaultSecurity).lowercase()
        if (security == "xtls") security = "tls"
        val insecure = u.q("allowInsecure", "insecure", "skip-cert-verify")?.let { it == "1" || it == "true" } ?: false
        return StreamSettings(
            network = network,
            security = security,
            sni = u.q("sni", "peer", "serverName"),
            fingerprint = u.q("fp"),
            alpn = u.q("alpn")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            allowInsecure = insecure,
            publicKey = u.q("pbk"),
            shortId = u.q("sid"),
            spiderX = u.q("spx"),
            path = u.q("path"),
            host = u.q("host"),
            serviceName = u.q("serviceName"),
            mode = u.q("mode"),
            headerType = u.q("headerType"),
        )
    }

    private fun nameOr(fragment: String, host: String, port: Int) =
        fragment.trim().ifEmpty { "$host:$port" }

    private fun parseVless(link: String, u: Uri): ProxyConfig {
        if (u.userInfo.isEmpty()) throw ParseException("vless: no uuid")
        return ProxyConfig.Vless(
            rawLink = link,
            name = nameOr(u.fragment, u.host, u.port),
            host = u.host,
            port = u.port,
            uuid = u.userInfo,
            flow = u.q("flow"),
            encryption = u.q("encryption") ?: "none",
            stream = streamFromQuery(u),
        )
    }

    private fun parseTrojan(link: String, u: Uri): ProxyConfig {
        if (u.userInfo.isEmpty()) throw ParseException("trojan: no password")
        return ProxyConfig.Trojan(
            rawLink = link,
            name = nameOr(u.fragment, u.host, u.port),
            host = u.host,
            port = u.port,
            password = u.userInfo,
            flow = u.q("flow"),
            stream = streamFromQuery(u, defaultSecurity = "tls"),
        )
    }

    private fun parseHysteria2(link: String, u: Uri): ProxyConfig {
        val insecure = u.q("insecure", "allowInsecure")?.let { it == "1" || it == "true" } ?: false
        return ProxyConfig.Hysteria2(
            rawLink = link,
            name = nameOr(u.fragment, u.host, u.port),
            host = u.host,
            port = u.port,
            password = u.userInfo,
            sni = u.q("sni", "peer"),
            insecure = insecure,
            obfs = u.q("obfs"),
            obfsPassword = u.q("obfs-password", "obfsParam"),
            upMbps = u.q("upmbps", "up")?.filter { it.isDigit() }?.toIntOrNull(),
            downMbps = u.q("downmbps", "down")?.filter { it.isDigit() }?.toIntOrNull(),
            alpn = u.q("alpn")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        )
    }

    private fun parseVmess(link: String, rest: String): ProxyConfig {
        // Base64 JSON (v2rayN format) is by far the most common.
        val hash = rest.indexOf('#')
        val body = if (hash >= 0) rest.substring(0, hash) else rest
        val json = if (!body.contains('@')) Base64Util.decodeToString(body)?.trim() else null
        if (json != null && json.startsWith("{")) {
            val o = JSONObject(json)
            val host = o.optString("add").trim()
            val port = o.opt("port")?.toString()?.trim()?.toIntOrNull() ?: throw ParseException("vmess: bad port")
            if (host.isEmpty()) throw ParseException("vmess: no address")
            val net = o.optString("net", "tcp").ifEmpty { "tcp" }.lowercase().let { if (it == "http") "h2" else it }
            val tls = o.optString("tls").lowercase()
            val security = when (tls) {
                "tls", "xtls" -> "tls"
                "reality" -> "reality"
                else -> "none"
            }
            val insecure = when (val v = o.opt("skip-cert-verify") ?: o.opt("allowInsecure") ?: o.opt("insecure")) {
                is Boolean -> v
                is String -> v == "1" || v.equals("true", true)
                is Number -> v.toInt() == 1
                else -> false
            }
            val headerType = o.optString("type").takeIf { it.isNotEmpty() && it != "none" && it != "---" }
            val name = o.optString("ps").ifEmpty { "$host:$port" }
            return ProxyConfig.Vmess(
                rawLink = link,
                name = name,
                host = host,
                port = port,
                uuid = o.optString("id"),
                alterId = o.opt("aid")?.toString()?.toIntOrNull() ?: 0,
                cipher = o.optString("scy").ifEmpty { "auto" },
                stream = StreamSettings(
                    network = net,
                    security = security,
                    sni = o.optString("sni").ifEmpty { null },
                    fingerprint = o.optString("fp").ifEmpty { null },
                    alpn = o.optString("alpn").split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    allowInsecure = insecure,
                    path = o.optString("path").ifEmpty { null },
                    host = o.optString("host").ifEmpty { null },
                    serviceName = o.optString("path").ifEmpty { null }.takeIf { net == "grpc" },
                    mode = o.optString("mode").ifEmpty { null },
                    headerType = headerType,
                ),
            )
        }
        // URI style: vmess://uuid@host:port?encryption=auto&type=ws...#name
        val u = Uri.parse(rest)
        if (u.userInfo.isEmpty()) throw ParseException("vmess: unparseable")
        return ProxyConfig.Vmess(
            rawLink = link,
            name = nameOr(u.fragment, u.host, u.port),
            host = u.host,
            port = u.port,
            uuid = u.userInfo,
            alterId = u.q("alterId", "aid")?.toIntOrNull() ?: 0,
            cipher = u.q("encryption", "scy") ?: "auto",
            stream = streamFromQuery(u),
        )
    }

    private fun parseShadowsocks(link: String, rest: String): ProxyConfig {
        var s = rest
        var fragment = ""
        val hash = s.indexOf('#')
        if (hash >= 0) {
            fragment = Uri.decode(s.substring(hash + 1))
            s = s.substring(0, hash)
        }
        var plugin: String? = null
        val qm = s.indexOf('?')
        if (qm >= 0) {
            plugin = Uri.parseQuery(s.substring(qm + 1))["plugin"]?.ifEmpty { null }
            s = s.substring(0, qm)
        }
        s = s.trimEnd('/')
        val method: String
        val password: String
        val host: String
        val port: Int
        val at = s.lastIndexOf('@')
        if (at < 0) {
            // Legacy: ss://base64(method:password@host:port)
            val decoded = Base64Util.decodeToString(s) ?: throw ParseException("ss: bad legacy base64")
            val at2 = decoded.lastIndexOf('@')
            if (at2 < 0) throw ParseException("ss: legacy without @")
            val (m, p) = splitMethodPassword(decoded.substring(0, at2))
            method = m; password = p
            val hp = Uri.splitHostPort(decoded.substring(at2 + 1))
            host = hp.first; port = hp.second
        } else {
            val userRaw = s.substring(0, at)
            val userDecoded = Uri.decode(userRaw)
            val creds = if (userDecoded.contains(':')) userDecoded
            else Base64Util.decodeToString(userRaw) ?: throw ParseException("ss: bad userinfo")
            val (m, p) = splitMethodPassword(creds)
            method = m; password = p
            val hp = Uri.splitHostPort(s.substring(at + 1))
            host = hp.first; port = hp.second
        }
        return ProxyConfig.Shadowsocks(
            rawLink = link,
            name = fragment.trim().ifEmpty { "$host:$port" },
            host = host,
            port = port,
            method = method,
            password = password,
            plugin = plugin,
        )
    }

    private fun splitMethodPassword(creds: String): Pair<String, String> {
        val i = creds.indexOf(':')
        if (i < 0) throw ParseException("ss: no method:password")
        return creds.substring(0, i) to creds.substring(i + 1)
    }
}
