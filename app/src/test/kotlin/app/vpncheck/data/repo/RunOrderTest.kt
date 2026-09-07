package app.vpncheck.data.repo

import app.vpncheck.core.network.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class RunOrderTest {
    private data class C(val name: String, val white: Boolean, val ok: Boolean?)

    private val items = listOf(
        C("black-failed", false, false),
        C("white-unchecked", true, null),
        C("black-ok", false, true),
        C("white-failed", true, false),
        C("black-unchecked", false, null),
        C("white-ok", true, true),
    )

    private fun order(mode: NetworkType) =
        RunOrder.sort(items, mode) { RunOrder.Info(it.white, it.ok) }.map { it.name }

    @Test
    fun `mobile checks white lists first, then by previous status`() {
        assertEquals(
            listOf("white-ok", "white-unchecked", "white-failed", "black-ok", "black-unchecked", "black-failed"),
            order(NetworkType.MOBILE),
        )
    }

    @Test
    fun `wifi ignores list kind and orders by previous status, stable within groups`() {
        assertEquals(
            listOf("black-ok", "white-ok", "white-unchecked", "black-unchecked", "black-failed", "white-failed"),
            order(NetworkType.WIFI),
        )
    }
}
