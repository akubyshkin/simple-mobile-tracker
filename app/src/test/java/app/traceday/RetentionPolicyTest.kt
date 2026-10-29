package app.traceday

import app.traceday.core.retention.RetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {

    private val policy = RetentionPolicy()
    private val now = 1_800_000_000_000L

    @Test
    fun defaultRetention_sevenDays() {
        assertEquals(7, policy.days)
    }

    @Test
    fun cutoff_isSevenDaysBeforeNow() {
        assertEquals(now - 7 * RetentionPolicy.MILLIS_PER_DAY, policy.cutoffTimestamp(now))
    }

    @Test
    fun pointOlderThanCutoff_expired() {
        val oldPoint = policy.cutoffTimestamp(now) - 1
        assertTrue(policy.isExpired(oldPoint, now))
    }

    @Test
    fun freshPoint_notExpired() {
        val freshPoint = now - 1000L
        assertFalse(policy.isExpired(freshPoint, now))
    }
}
