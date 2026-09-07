package app.vpncheck.data.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMembershipTest {

    @Test
    fun `encode sorts and dedups, decode round-trips`() {
        assertEquals("a,b", SourceMembership.encode(listOf("b", "a", "b", "")))
        assertEquals(setOf("a", "b"), SourceMembership.decode("a,b"))
        assertTrue(SourceMembership.decode(null).isEmpty())
        assertTrue(SourceMembership.decode("").isEmpty())
    }

    @Test
    fun `merge keeps membership only for sources that were not refreshed`() {
        val previous = setOf("black_vless", "white_cidr_all", "black_ss_all")
        val seenNow = setOf("black_vless_mobile", "white_cidr_all")
        // black_ss_all failed to download this time -> keep; black_vless refreshed fine and no longer lists it -> drop
        val merged = SourceMembership.merge(previous, seenNow, notRefreshed = setOf("black_ss_all"))
        assertEquals(setOf("black_vless_mobile", "white_cidr_all", "black_ss_all"), merged)
    }

    @Test
    fun `all sources have a kind and white and black are both present`() {
        assertEquals(8, SubscriptionSource.ALL.size)
        assertEquals(4, SubscriptionSource.byKind(ListKind.BLACK).size)
        assertEquals(4, SubscriptionSource.byKind(ListKind.WHITE).size)
        assertEquals(SubscriptionSource.ALL.size, SubscriptionSource.ALL.map { it.id }.distinct().size)
        assertTrue(SubscriptionSource.byId("white_cidr_all")!!.fileName == "WHITE-CIDR-RU-all.txt")
        assertTrue(SubscriptionSource.byId("white_cidr_checked")!!.fileName == "WHITE-CIDR-RU-checked.txt")
        assertTrue(SubscriptionSource.byId("white_sni_all")!!.fileName == "WHITE-SNI-RU-all.txt")
    }

    @Test
    fun `kindsOf maps ids to list kinds`() {
        assertEquals(setOf(ListKind.BLACK, ListKind.WHITE), SubscriptionSource.kindsOf(listOf("black_vless", "white_cidr_all")))
        assertEquals(setOf(ListKind.WHITE), SubscriptionSource.kindsOf(listOf("white_vless_mobile", "unknown")))
        assertTrue(SubscriptionSource.kindsOf(emptyList()).isEmpty())
    }
}
