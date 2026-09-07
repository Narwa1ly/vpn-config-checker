package app.vpncheck.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FreshnessTest {
    private val hour = 60 * 60 * 1000L

    @Test
    fun `classifies download age`() {
        val now = 1_000_000_000_000L
        assertEquals(Freshness.NONE, Freshness.of(null, now))
        assertEquals(Freshness.FRESH, Freshness.of(now - 1 * hour, now))
        assertEquals(Freshness.FRESH, Freshness.of(now - 11 * hour, now))
        assertEquals(Freshness.AGING, Freshness.of(now - 13 * hour, now))
        assertEquals(Freshness.STALE, Freshness.of(now - 49 * hour, now))
    }
}
