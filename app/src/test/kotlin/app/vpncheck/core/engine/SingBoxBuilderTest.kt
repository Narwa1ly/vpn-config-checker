package app.vpncheck.core.engine

import app.vpncheck.core.parser.CoreType
import app.vpncheck.core.parser.LinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SingBoxBuilderTest {

    private fun out(link: String, addr: String? = null) =
        SingBoxConfigBuilder.build(LinkParser.parseOrThrow(link), 1080, addr ?: LinkParser.parseOrThrow(link).host)
            .getJSONArray("outbounds").getJSONObject(0)

    @Test
    fun `vmess ws tls insecure`() {
        val json = """{"add":"47.243.92.155","aid":"0","host":"mtck.v.3dns.vip","id":"ID","net":"ws","path":"/ws-vmess","port":"443","ps":"HK","skip-cert-verify":true,"tls":"tls","type":"none","v":"2","scy":"auto"}"""
        val link = "vmess://" + Base64.getEncoder().encodeToString(json.toByteArray())
        val o = out(link)
        assertEquals("vmess", o.getString("type"))
        assertEquals("47.243.92.155", o.getString("server"))
        assertEquals(443, o.getInt("server_port"))
        assertEquals("ID", o.getString("uuid"))
        assertEquals("auto", o.getString("security"))
        assertEquals(0, o.getInt("alter_id"))
        val tls = o.getJSONObject("tls")
        assertTrue(tls.getBoolean("enabled"))
        assertTrue(tls.getBoolean("insecure"))
        assertEquals("mtck.v.3dns.vip", tls.getString("server_name"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        val tr = o.getJSONObject("transport")
        assertEquals("ws", tr.getString("type"))
        assertEquals("/ws-vmess", tr.getString("path"))
        assertEquals("mtck.v.3dns.vip", tr.getJSONObject("headers").getString("Host"))
    }

    @Test
    fun `trojan insecure with resolved address`() {
        val o = out("trojan://pw@example.com:1105?allowInsecure=1&type=tcp&security=tls&sni=example.com#fr", "1.2.3.4")
        assertEquals("trojan", o.getString("type"))
        assertEquals("1.2.3.4", o.getString("server"))
        assertEquals("pw", o.getString("password"))
        assertTrue(o.getJSONObject("tls").getBoolean("insecure"))
        assertEquals("example.com", o.getJSONObject("tls").getString("server_name"))
        assertFalse(o.has("transport"))
    }

    @Test
    fun `vless reality vision`() {
        val o = out("vless://u@cdn.example.com:443?type=tcp&security=reality&flow=xtls-rprx-vision&fp=android&pbk=PBK&sid=SID&sni=cdn.example.com#x")
        assertEquals("vless", o.getString("type"))
        assertEquals("xtls-rprx-vision", o.getString("flow"))
        val tls = o.getJSONObject("tls")
        assertFalse(tls.getBoolean("insecure"))
        assertEquals("android", tls.getJSONObject("utls").getString("fingerprint"))
        val r = tls.getJSONObject("reality")
        assertTrue(r.getBoolean("enabled"))
        assertEquals("PBK", r.getString("public_key"))
        assertEquals("SID", r.getString("short_id"))
    }

    @Test
    fun `grpc and shadowsocks`() {
        val g = out("vless://u@h.com:443?type=grpc&serviceName=svc&security=tls#g")
        assertEquals("grpc", g.getJSONObject("transport").getString("type"))
        assertEquals("svc", g.getJSONObject("transport").getString("service_name"))
        val creds = Base64.getEncoder().encodeToString("aes-256-gcm:secret".toByteArray())
        val s = out("ss://$creds@5.6.7.8:8388#ss")
        assertEquals("shadowsocks", s.getString("type"))
        assertEquals("aes-256-gcm", s.getString("method"))
        assertEquals("secret", s.getString("password"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `xhttp is rejected`() {
        out("vless://u@h.com:443?type=xhttp&security=tls&allowInsecure=1#x")
    }

    @Test
    fun `core selection`() {
        assertEquals(CoreType.SINGBOX, LinkParser.parseOrThrow("hysteria2://p@h:1#a").core)
        assertEquals(CoreType.XRAY, LinkParser.parseOrThrow("vless://u@h:1?type=xhttp&security=tls&allowInsecure=1#a").core)
        assertEquals(CoreType.SINGBOX, LinkParser.parseOrThrow("vless://u@h:1?type=ws&security=tls&allowInsecure=1#a").core)
        assertEquals(CoreType.XRAY, LinkParser.parseOrThrow("vless://u@h:1?type=ws&security=tls#a").core)
        assertEquals(CoreType.XRAY, LinkParser.parseOrThrow("vless://u@h:1?type=tcp&security=reality&pbk=x#a").core)
        assertEquals(CoreType.XRAY, LinkParser.parseOrThrow("vless://u@h:1?type=tcp&security=none&allowInsecure=1#a").core)
        val creds = Base64.getEncoder().encodeToString("aes-256-gcm:secret".toByteArray())
        assertEquals(CoreType.SINGBOX, LinkParser.parseOrThrow("ss://$creds@5.6.7.8:8388#ss").core)
    }
}
