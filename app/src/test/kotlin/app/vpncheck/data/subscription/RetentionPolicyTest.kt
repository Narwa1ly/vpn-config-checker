package app.vpncheck.data.subscription

import app.vpncheck.data.subscription.RetentionPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionPolicyTest {
    private val refreshedAndAbsent = setOf("black_vless")

    @Test
    fun `vanished from refreshed subscriptions and never worked is deleted`() {
        assertEquals(Decision.Delete, RetentionPolicy.forUnseen(refreshedAndAbsent, disabledIds = emptySet(), failedIds = emptySet(), worksSomewhere = false))
    }

    @Test
    fun `vanished but working somewhere is kept`() {
        assertEquals(Decision.Keep, RetentionPolicy.forUnseen(refreshedAndAbsent, disabledIds = emptySet(), failedIds = emptySet(), worksSomewhere = true))
    }

    @Test
    fun `source download failed keeps membership untouched`() {
        assertEquals(Decision.Keep, RetentionPolicy.forUnseen(setOf("black_vless"), disabledIds = emptySet(), failedIds = setOf("black_vless"), worksSomewhere = false))
    }

    @Test
    fun `mixed sources drop refreshed ones and keep failed ones`() {
        val d = RetentionPolicy.forUnseen(setOf("black_vless", "white_cidr_all"), disabledIds = emptySet(), failedIds = setOf("white_cidr_all"), worksSomewhere = false)
        assertEquals(Decision.UpdateSources(setOf("white_cidr_all")), d)
    }

    @Test
    fun `configs only from disabled sources are deleted even if working`() {
        assertEquals(Decision.Delete, RetentionPolicy.forUnseen(setOf("black_ss_weak_dpi"), disabledIds = setOf("black_ss_weak_dpi"), failedIds = emptySet(), worksSomewhere = true))
    }

    @Test
    fun `offline refresh deletes nothing`() {
        val all = setOf("black_vless", "white_cidr_all")
        assertEquals(Decision.Keep, RetentionPolicy.forUnseen(all, disabledIds = emptySet(), failedIds = all, worksSomewhere = false))
    }
}
