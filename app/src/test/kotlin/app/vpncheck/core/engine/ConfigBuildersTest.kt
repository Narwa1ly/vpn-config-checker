package app.vpncheck.core.engine

import app.vpncheck.core.parser.CoreType
import app.vpncheck.core.parser.LinkParser
import app.vpncheck.core.parser.ProxyConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ConfigBuildersTest {

    @Test
    fun `xray vless reality uses resolved ip but keeps sni`() {
        val c = LinkParser.parseOrThrow("vless://uuid-1@cdn.example.com:443?type=tcp&security=reality&flow=xtls-rprx-vision&fp=edge&pbk=PBK&sid=SID&sni=cdn.example.com#x")
        val j = XrayConfigBuilder.build(c, 10808, serverAddress = "1.2.3.4")
        val inbound = j.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("http", inbound.getString("protocol"))
        assertEquals(10808, inbound.getInt("port"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        val out = j.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", out.getString("protocol"))
        val vnext = out.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("1.2.3.4", vnext.getString("address"))
        assertEquals(443, vnext.getInt("port"))
        val user = vnext.getJSONArray("users").getJSONObject(0)
        assertEquals("uuid-1", user.getString("id"))
        assertEquals("xtls-rprx-vision", user.getString("flow"))
        assertEquals("none", user.getString("encryption"))
        val ss = out.getJSONObject("streamSettings")
        assertEquals("tcp", ss.getString("network"))
        assertEquals("reality", ss.getString("security"))
        val r = ss.getJSONObject("realitySettings")
        assertEquals("cdn.example.com", r.getString("serverName"))
        assertEquals("edge", r.getString("fingerprint"))
        assertEquals("PBK", r.getString("publicKey"))
        assertEquals("SID", r.getString("shortId"))
        assertEquals("proxy", out.getString("tag"))
        assertEquals("freedom", j.getJSONArray("outbounds").getJSONObject(1).getString("protocol"))
    }

    @Test
    fun `xray vless xhttp tls with alpn`() {
        val c = LinkParser.parseOrThrow("vless://u@chatgpt.com:2087?encryption=none&type=xhttp&mode=auto&path=%2F%40v2_line&security=tls&sni=edge.example.com&alpn=h2%2Chttp%2F1.1&fp=qq#DE")
        val ss = XrayConfigBuilder.build(c, 1).getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
        assertEquals("xhttp", ss.getString("network"))
        val x = ss.getJSONObject("xhttpSettings")
        assertEquals("/@v2_line", x.getString("path"))
        assertEquals("auto", x.getString("mode"))
        assertEquals("chatgpt.com", x.getString("host"))
        val tls = ss.getJSONObject("tlsSettings")
        assertEquals("edge.example.com", tls.getString("serverName"))
        assertEquals("qq", tls.getString("fingerprint"))
        assertEquals(listOf("h2", "http/1.1"), (0 until tls.getJSONArray("alpn").length()).map { tls.getJSONArray("alpn").getString(it) })
        assertFalse(tls.has("allowInsecure"))
    }

    @Test
    fun `xray vmess ws uses host header and insecure`() {
        val json = """{"add":"47.243.92.155","aid":"0","host":"mtck.v.3dns.vip","id":"ID","net":"ws","path":"/ws-vmess","port":"443","ps":"HK","skip-cert-verify":true,"tls":"tls","type":"none","v":"2","scy":"auto"}"""
        val c = LinkParser.parseOrThrow("vmess://" + Base64.getEncoder().encodeToString(json.toByteArray()))
        val out = XrayConfigBuilder.build(c, 2).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vmess", out.getString("protocol"))
        val user = out.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).getJSONArray("users").getJSONObject(0)
        assertEquals("ID", user.getString("id"))
        assertEquals(0, user.getInt("alterId"))
        assertEquals("auto", user.getString("security"))
        val ss = out.getJSONObject("streamSettings")
        assertEquals("ws", ss.getString("network"))
        assertEquals("/ws-vmess", ss.getJSONObject("wsSettings").getString("path"))
        assertEquals("mtck.v.3dns.vip", ss.getJSONObject("wsSettings").getString("host"))
        assertEquals("mtck.v.3dns.vip", ss.getJSONObject("wsSettings").getJSONObject("headers").getString("Host"))
        val tls = ss.getJSONObject("tlsSettings")
        assertFalse(tls.has("allowInsecure"))
        // sni falls back to ws host header when link has no sni
        assertEquals("mtck.v.3dns.vip", tls.getString("serverName"))
        // insecure TLS configs are routed to sing-box because Xray v26.2.6+ rejects allowInsecure
        assertEquals(CoreType.SINGBOX, c.core)
    }

    @Test
    fun `xray trojan and shadowsocks`() {
        val t = LinkParser.parseOrThrow("trojan://pw@194.9.172.236:1105?allowInsecure=1&type=tcp&security=tls#fr")
        val to = XrayConfigBuilder.build(t, 3).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("trojan", to.getString("protocol"))
        val srv = to.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("pw", srv.getString("password"))
        assertEquals(1105, srv.getInt("port"))
        assertFalse(to.getJSONObject("streamSettings").getJSONObject("tlsSettings").has("allowInsecure"))
        assertEquals("194.9.172.236", to.getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("serverName"))
        assertEquals(CoreType.SINGBOX, t.core)

        val creds = Base64.getEncoder().encodeToString("aes-256-gcm:secret".toByteArray())
        val s = LinkParser.parseOrThrow("ss://$creds@5.6.7.8:8388#ss")
        val so = XrayConfigBuilder.build(s, 4).getJSONArray("outbounds").getJSONObject(0)
        assertEquals("shadowsocks", so.getString("protocol"))
        val ssrv = so.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("aes-256-gcm", ssrv.getString("method"))
        assertEquals("secret", ssrv.getString("password"))
    }

    @Test
    fun `xray grpc service name`() {
        val c = LinkParser.parseOrThrow("vless://u@h.com:443?type=grpc&serviceName=svc&security=tls&mode=multi#g")
        val ss = XrayConfigBuilder.build(c, 5).getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings")
        assertEquals("grpc", ss.getString("network"))
        assertEquals("svc", ss.getJSONObject("grpcSettings").getString("serviceName"))
        assertTrue(ss.getJSONObject("grpcSettings").getBoolean("multiMode"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `xray rejects hysteria2`() {
        val c = LinkParser.parseOrThrow("hysteria2://pw@1.1.1.1:443#h")
        XrayConfigBuilder.build(c, 6)
    }

    @Test
    fun `singbox hysteria2 full`() {
        val c = LinkParser.parseOrThrow("hysteria2://7302ad@131.186.19.174:50160?insecure=1&sni=speedtest.tinkoff.ru&upmbps=10&downmbps=50&obfs=salamander&obfs-password=op#n") as ProxyConfig.Hysteria2
        val j = SingBoxConfigBuilder.build(c, 20000)
        val inb = j.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("mixed", inb.getString("type"))
        assertEquals(20000, inb.getInt("listen_port"))
        val out = j.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("hysteria2", out.getString("type"))
        assertEquals("131.186.19.174", out.getString("server"))
        assertEquals(50160, out.getInt("server_port"))
        assertEquals("7302ad", out.getString("password"))
        assertEquals(10, out.getInt("up_mbps"))
        assertEquals(50, out.getInt("down_mbps"))
        assertEquals("salamander", out.getJSONObject("obfs").getString("type"))
        assertEquals("op", out.getJSONObject("obfs").getString("password"))
        val tls = out.getJSONObject("tls")
        assertTrue(tls.getBoolean("enabled"))
        assertTrue(tls.getBoolean("insecure"))
        assertEquals("speedtest.tinkoff.ru", tls.getString("server_name"))
        assertEquals("direct", j.getJSONArray("outbounds").getJSONObject(1).getString("type"))
    }

    @Test
    fun `singbox hysteria2 minimal omits optional fields`() {
        val c = LinkParser.parseOrThrow("hysteria2://pw@example.org:443#m")
        val out = SingBoxConfigBuilder.build(c, 1).getJSONArray("outbounds").getJSONObject(0)
        assertFalse(out.has("up_mbps"))
        assertFalse(out.has("obfs"))
        assertEquals("example.org", out.getJSONObject("tls").getString("server_name"))
        assertFalse(out.getJSONObject("tls").getBoolean("insecure"))
    }
}
