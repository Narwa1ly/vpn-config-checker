package app.vpncheck.core.check

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoIpParserTest {
    @Test
    fun `parses api response`() {
        val json = """{"ip":"8.8.8.8","city":"New York","region":"New York","country":"United States","code":"US","asn":{"id":"15169","name":"GOOGLE","hosting":true},"signup":"x"}"""
        val o = TwoIpParser.parse(json)!!
        assertTrue(o.ok)
        assertEquals("8.8.8.8", o.exitIp)
        assertEquals("United States", o.country)
        assertEquals("US", o.countryCode)
        assertEquals("New York", o.city)
        assertEquals(15169, o.asnId)
        assertEquals("GOOGLE", o.asnName)
    }

    @Test
    fun `rejects non json and missing ip`() {
        assertNull(TwoIpParser.parse("<html>2ip loading...</html>"))
        assertNull(TwoIpParser.parse("""{"error":"x"}"""))
    }

    @Test
    fun `page detection`() {
        assertTrue(TwoIpParser.looksLike2ipPage("<title>2ip loading...</title>"))
        assertFalse(TwoIpParser.looksLike2ipPage("<html>Access denied</html>"))
    }
}
