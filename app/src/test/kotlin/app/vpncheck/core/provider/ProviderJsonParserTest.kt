package app.vpncheck.core.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderJsonParserTest {

    @Test
    fun `ipwho_is response`() {
        val json = """{"ip":"8.8.8.8","success":true,"country":"United States","country_code":"US","city":"San Jose","connection":{"asn":15169,"org":"Google LLC","isp":"Google LLC","domain":"google.com"}}"""
        val p = ProviderJsonParser.fromIpWhoIs("8.8.8.8", json)!!
        assertEquals(15169, p.asn)
        assertEquals("Google LLC", p.org)
        assertEquals("google.com", p.domain)
        assertEquals("https://google.com", p.website)
        assertEquals("US", p.countryCode)
        assertEquals("Google LLC", p.displayName)
        assertEquals("ipwho.is", p.source)
    }

    @Test
    fun `ipwho_is failure returns null`() {
        assertNull(ProviderJsonParser.fromIpWhoIs("x", """{"success":false,"message":"Invalid IP address"}"""))
    }

    @Test
    fun `ipwho_is without domain falls back to asn table`() {
        val json = """{"ip":"1.1.1.1","success":true,"connection":{"asn":24940,"org":"Hetzner Online GmbH","isp":"Hetzner","domain":""}}"""
        val p = ProviderJsonParser.fromIpWhoIs("1.1.1.1", json)!!
        assertEquals("https://www.hetzner.com", p.website)
    }

    @Test
    fun `ip-api response`() {
        val json = """{"status":"success","country":"Germany","countryCode":"DE","city":"Falkenstein","isp":"Hetzner Online GmbH","org":"Hetzner","as":"AS24940 Hetzner Online GmbH","asname":"HETZNER-AS","query":"5.9.1.1"}"""
        val p = ProviderJsonParser.fromIpApi("5.9.1.1", json)!!
        assertEquals(24940, p.asn)
        assertEquals("Hetzner", p.org)
        assertEquals("https://www.hetzner.com", p.website)
        assertEquals("DE", p.countryCode)
    }

    @Test
    fun `ip-api failure`() {
        assertNull(ProviderJsonParser.fromIpApi("x", """{"status":"fail","message":"reserved range"}"""))
    }

    @Test
    fun `asn-only fallback uses table name and site`() {
        val p = ProviderJsonParser.fromAsnOnly("1.2.3.4", 14061, "DIGITALOCEAN-ASN", "Netherlands", "NL", "Amsterdam")
        assertEquals("DigitalOcean", p.displayName)
        assertEquals("https://www.digitalocean.com", p.website)
        val unknown = ProviderJsonParser.fromAsnOnly("1.2.3.4", 999999, "SOME-AS", null, null, null)
        assertEquals("SOME-AS", unknown.displayName)
        assertNull(unknown.website)
    }

    @Test
    fun `websiteFor normalises domains`() {
        assertEquals("https://hetzner.com", ProviderJsonParser.websiteFor("https://hetzner.com/", null))
        assertEquals("https://www.ovhcloud.com", ProviderJsonParser.websiteFor(null, 16276))
        assertNull(ProviderJsonParser.websiteFor("nodomain", null))
    }
}
