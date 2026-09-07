package app.vpncheck.data.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SubscriptionParserTest {

    private val plain = """
        # profile-title: 🏴 ЧЕРНЫЕ СПИСКИ 🏴 BLACK LISTS | Mobile-150
        # profile-update-interval: 1
        # Количество: 2

        vless://u1@h1.com:443?type=tcp#a
        vmess://eyJhZGQiOiJ4In0=
        # comment in the middle
        hy2://pw@h2.com:443#b
        vless://u1@h1.com:443?type=tcp#a
        garbage line
    """.trimIndent()

    @Test
    fun `plain text extracts links skips comments and dedups`() {
        val links = SubscriptionParser.extractLinks(plain)
        assertEquals(3, links.size)
        assertEquals("vless://u1@h1.com:443?type=tcp#a", links[0])
        assertEquals("vmess://eyJhZGQiOiJ4In0=", links[1])
        assertEquals("hy2://pw@h2.com:443#b", links[2])
    }

    @Test
    fun `base64 body is decoded`() {
        val body = Base64.getEncoder().encodeToString("vless://a@b:1#x\r\ntrojan://p@c:2#y\n".toByteArray())
        val links = SubscriptionParser.extractLinks(body)
        assertEquals(listOf("vless://a@b:1#x", "trojan://p@c:2#y"), links)
    }

    @Test
    fun `empty and junk bodies give nothing`() {
        assertTrue(SubscriptionParser.extractLinks("").isEmpty())
        assertTrue(SubscriptionParser.extractLinks("<html>404</html>").isEmpty())
    }

    @Test
    fun `legitimately empty subscription still has a profile title`() {
        val body = "# profile-title: 🏳️ БЕЛЫЕ СПИСКИ 🏳️ WHITE LISTS | SNI-RU\n# profile-update-interval: 1\n# Количество: 0\n"
        assertTrue(SubscriptionParser.extractLinks(body).isEmpty())
        assertEquals("🏳️ БЕЛЫЕ СПИСКИ 🏳️ WHITE LISTS | SNI-RU", SubscriptionParser.profileTitle(body))
    }

    @Test
    fun `profile title parsed`() {
        assertEquals("🏴 ЧЕРНЫЕ СПИСКИ 🏴 BLACK LISTS | Mobile-150", SubscriptionParser.profileTitle(plain))
    }

    @Test
    fun `mirror urls substitute file name`() {
        val s = SubscriptionSource.byId("black_ss_all")!!
        val urls = s.urls()
        assertEquals(SubscriptionSource.MIRRORS.size, urls.size)
        assertTrue(urls[0].endsWith("/BLACK_SS+All_RUS.txt"))
        assertTrue(urls.all { it.contains("igareck/vpn-configs-for-russia") })
    }
}
