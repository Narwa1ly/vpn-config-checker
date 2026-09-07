package app.vpncheck.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Base64UtilTest {
    @Test
    fun `decodes standard urlsafe and unpadded`() {
        assertEquals("hello", Base64Util.decodeToString("aGVsbG8="))
        assertEquals("hello", Base64Util.decodeToString("aGVsbG8"))
        assertEquals("hello\n", Base64Util.decodeToString("aGVsbG8K"))
        assertEquals("??>", Base64Util.decodeToString("Pz8-"))
        assertEquals("??>", Base64Util.decodeToString("Pz8+"))
    }

    @Test
    fun `rejects invalid`() {
        assertNull(Base64Util.decodeLenient("!!!"))
        assertNull(Base64Util.decodeLenient(""))
    }

    @Test
    fun `looksLikeBase64`() {
        assertTrue(Base64Util.looksLikeBase64("dmxlc3M6Ly9hYmM=\n"))
        assertFalse(Base64Util.looksLikeBase64("vless://abc"))
    }
}
