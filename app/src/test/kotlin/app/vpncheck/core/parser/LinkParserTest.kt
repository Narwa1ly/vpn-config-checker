package app.vpncheck.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LinkParserTest {

    @Test
    fun `vless reality tcp vision`() {
        val link = "vless://0325e30e-7505-477e-aa32-7f73a3cc0153@cdn7-02.cdn-vkvideo.com:443?type=tcp&security=reality&flow=xtls-rprx-vision&fp=edge&pbk=MpuwI3YH2g3YWHKLuQwwgD_qLo-UmUtZe8L1KXOKxhA&sid=b0d8f964b7ac8ac0&sni=cdn7-02.cdn-vkvideo.com#%F0%9F%87%AB%F0%9F%87%AE%20Finland%2C%20Helsinki%20%7C%20%F0%9F%8C%90%20%7C%20%5BBL%5D"
        val c = LinkParser.parseOrThrow(link) as ProxyConfig.Vless
        assertEquals("cdn7-02.cdn-vkvideo.com", c.host)
        assertEquals(443, c.port)
        assertEquals("0325e30e-7505-477e-aa32-7f73a3cc0153", c.uuid)
        assertEquals("xtls-rprx-vision", c.flow)
        assertEquals("reality", c.stream.security)
        assertEquals("tcp", c.stream.network)
        assertEquals("edge", c.stream.fingerprint)
        assertEquals("MpuwI3YH2g3YWHKLuQwwgD_qLo-UmUtZe8L1KXOKxhA", c.stream.publicKey)
        assertEquals("b0d8f964b7ac8ac0", c.stream.shortId)
        assertEquals("🇫🇮 Finland, Helsinki | 🌐 | [BL]", c.name)
        assertEquals(CoreType.XRAY, c.core)
        assertEquals(Protocol.VLESS, c.protocol)
    }

    @Test
    fun `vless xhttp with encoded path and alpn`() {
        val link = "vless://cf1d2ded-2472-483a-f2f2-12c83d1826dd@chatgpt.com:2087?encryption=none&type=xhttp&mode=auto&path=%2F%40v2_line&security=tls&sni=cdn-edge.example.com&alpn=h2%2Chttp%2F1.1&fp=qq#DE"
        val c = LinkParser.parseOrThrow(link) as ProxyConfig.Vless
        assertEquals("xhttp", c.stream.network)
        assertEquals("/@v2_line", c.stream.path)
        assertEquals("auto", c.stream.mode)
        assertEquals(listOf("h2", "http/1.1"), c.stream.alpn)
        assertEquals("tls", c.stream.security)
        assertEquals("cdn-edge.example.com", c.stream.sni)
        assertEquals("DE", c.name)
    }

    @Test
    fun `vless plus sign in path is preserved`() {
        val link = "vless://u@h.com:443?type=ws&path=/a+b#n"
        val c = LinkParser.parseOrThrow(link) as ProxyConfig.Vless
        assertEquals("/a+b", c.stream.path)
    }

    @Test
    fun `name falls back to host and port`() {
        val c = LinkParser.parseOrThrow("vless://u@h.com:443?type=tcp")
        assertEquals("h.com:443", c.name)
    }

    @Test
    fun `vmess base64 json ws tls`() {
        val json = """{"add":"47.243.92.155","aid":"0","host":"mTCK.v.3dns.vip","id":"f23bb427-c1f9-4373-876c-2f43e9f790f3","net":"ws","path":"/ws-vmess","port":"443","ps":"🇭🇰 Hong Kong | [BL]","skip-cert-verify":true,"tls":"tls","type":"none","v":"2","scy":"auto","sni":"","alpn":"","fp":""}"""
        val link = "vmess://" + Base64.getEncoder().encodeToString(json.toByteArray())
        val c = LinkParser.parseOrThrow(link) as ProxyConfig.Vmess
        assertEquals("47.243.92.155", c.host)
        assertEquals(443, c.port)
        assertEquals("f23bb427-c1f9-4373-876c-2f43e9f790f3", c.uuid)
        assertEquals(0, c.alterId)
        assertEquals("auto", c.cipher)
        assertEquals("ws", c.stream.network)
        assertEquals("/ws-vmess", c.stream.path)
        assertEquals("mTCK.v.3dns.vip", c.stream.host)
        assertEquals("tls", c.stream.security)
        assertTrue(c.stream.allowInsecure)
        assertNull(c.stream.sni)
        assertNull(c.stream.headerType)
        assertEquals("🇭🇰 Hong Kong | [BL]", c.name)
    }

    @Test
    fun `vmess base64 without padding and with dashes type`() {
        val json = """{"add":"118.193.57.167","aid":"0","id":"d46bf492-45f4-4c33-82d1-ea2a1cb54eb6","net":"ws","path":"/d46bf492","port":"49667","ps":"TH","tls":"","type":"---","v":"2"}"""
        val b64 = Base64.getEncoder().withoutPadding().encodeToString(json.toByteArray())
        val c = LinkParser.parseOrThrow("vmess://$b64") as ProxyConfig.Vmess
        assertEquals(49667, c.port)
        assertEquals("none", c.stream.security)
        assertNull(c.stream.headerType)
        assertFalse(c.stream.allowInsecure)
    }

    @Test
    fun `trojan with insecure flags defaults to tls`() {
        val link = "trojan://kakashka13222@194.9.172.236:1105?allowInsecure=1&insecure=1&type=tcp&security=tls#%F0%9F%87%AB%F0%9F%87%B7%20France%2C%20Lyon%20%7C%20%5BBL%5D"
        val c = LinkParser.parseOrThrow(link) as ProxyConfig.Trojan
        assertEquals("kakashka13222", c.password)
        assertEquals("194.9.172.236", c.host)
        assertEquals(1105, c.port)
        assertEquals("tls", c.stream.security)
        assertTrue(c.stream.allowInsecure)
        assertEquals("🇫🇷 France, Lyon | [BL]", c.name)
    }

    @Test
    fun `hysteria2 with sni insecure and bandwidth`() {
        val link = "hysteria2://7302ad33229de166d393cd56143a1e4c@131.186.19.174:50160?insecure=1&sni=speedtest.tinkoff.ru&type=tcp&allowInsecure=1&upmbps=10&downmbps=50#name"
        val c = LinkParser.parseOrThrow(link) as ProxyConfig.Hysteria2
        assertEquals("7302ad33229de166d393cd56143a1e4c", c.password)
        assertEquals("131.186.19.174", c.host)
        assertEquals(50160, c.port)
        assertEquals("speedtest.tinkoff.ru", c.sni)
        assertTrue(c.insecure)
        assertEquals(10, c.upMbps)
        assertEquals(50, c.downMbps)
        assertEquals(CoreType.SINGBOX, c.core)
    }

    @Test
    fun `hy2 alias with port hopping and obfs`() {
        val link = "hy2://pw@example.com:443,20000-30000?obfs=salamander&obfs-password=secret&sni=a.b#x"
        val c = LinkParser.parseOrThrow(link) as ProxyConfig.Hysteria2
        assertEquals(443, c.port)
        assertEquals("salamander", c.obfs)
        assertEquals("secret", c.obfsPassword)
        assertEquals(Protocol.HYSTERIA2, c.protocol)
    }

    @Test
    fun `ss sip002 base64 userinfo`() {
        val creds = Base64.getUrlEncoder().withoutPadding().encodeToString("aes-256-gcm:pass123".toByteArray())
        val c = LinkParser.parseOrThrow("ss://$creds@1.2.3.4:8388#SS%20Test") as ProxyConfig.Shadowsocks
        assertEquals("aes-256-gcm", c.method)
        assertEquals("pass123", c.password)
        assertEquals("1.2.3.4", c.host)
        assertEquals(8388, c.port)
        assertEquals("SS Test", c.name)
        assertNull(c.plugin)
        assertNull(c.unsupportedReason())
    }

    @Test
    fun `ss sip002 plain userinfo with 2022 cipher`() {
        val c = LinkParser.parseOrThrow("ss://2022-blake3-aes-256-gcm:YctPZ6U7xPPcU%2Bgp3u%2B0tx%2FteEP7bPn4mJ4tT7jHd0A%3D@h.com:443/#x") as ProxyConfig.Shadowsocks
        assertEquals("2022-blake3-aes-256-gcm", c.method)
        assertEquals("YctPZ6U7xPPcU+gp3u+0tx/teEP7bPn4mJ4tT7jHd0A=", c.password)
        assertEquals("h.com", c.host)
    }

    @Test
    fun `ss legacy fully base64`() {
        val b = Base64.getEncoder().encodeToString("chacha20-ietf-poly1305:secret@5.6.7.8:9000".toByteArray())
        val c = LinkParser.parseOrThrow("ss://$b#legacy") as ProxyConfig.Shadowsocks
        assertEquals("chacha20-ietf-poly1305", c.method)
        assertEquals("secret", c.password)
        assertEquals("5.6.7.8", c.host)
        assertEquals(9000, c.port)
    }

    @Test
    fun `ss with plugin is unsupported`() {
        val creds = Base64.getEncoder().encodeToString("aes-128-gcm:p".toByteArray())
        val c = LinkParser.parseOrThrow("ss://$creds@h:1?plugin=obfs-local%3Bobfs%3Dhttp#x") as ProxyConfig.Shadowsocks
        assertEquals("obfs-local;obfs=http", c.plugin)
        assertTrue(c.unsupportedReason()!!.contains("plugin"))
    }

    @Test
    fun `ipv6 host`() {
        val c = LinkParser.parseOrThrow("vless://u@[2001:db8::1]:443?type=tcp#v6") as ProxyConfig.Vless
        assertEquals("2001:db8::1", c.host)
        assertEquals(443, c.port)
    }

    @Test
    fun `unknown scheme and garbage fail gracefully`() {
        assertTrue(LinkParser.parse("wireguard://x").isFailure)
        assertTrue(LinkParser.parse("not a link").isFailure)
        assertTrue(LinkParser.parse("vless://@h:443").isFailure)
        assertTrue(LinkParser.parse("vless://u@h:99999").isFailure)
    }

    @Test
    fun `id is stable sha256 of raw link`() {
        val a = LinkParser.parseOrThrow("vless://u@h.com:443?type=tcp#a")
        val b = LinkParser.parseOrThrow("vless://u@h.com:443?type=tcp#a")
        assertEquals(a.id, b.id)
        assertEquals(64, a.id.length)
    }
}
