package app.traceday

import app.traceday.domain.GeoPointData
import app.traceday.domain.TrackStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackStatsCalculatorTest {

    @Test
    fun emptyPoints_zeroStats() {
        val stats = TrackStatsCalculator.compute(emptyList())
        assertEquals(0.0, stats.distanceMeters, 0.0)
        assertEquals(0, stats.pointCount)
        assertEquals(null, stats.startAt)
    }

    @Test
    fun singlePoint_zeroDistance() {
        val stats = TrackStatsCalculator.compute(
            listOf(GeoPointData(1000L, 55.75, 37.61))
        )
        assertEquals(0.0, stats.distanceMeters, 0.001)
        assertEquals(1, stats.pointCount)
        assertEquals(0L, stats.durationMillis)
    }

    @Test
    fun twoKnownPoints_moscowToSpb_about630km() {
        val moscow = GeoPointData(0L, 55.7558, 37.6173)
        val spb = GeoPointData(3_600_000L, 59.9311, 30.3609)
        val stats = TrackStatsCalculator.compute(listOf(moscow, spb))
        assertTrue("distance ${stats.distanceMeters}", stats.distanceMeters in 600_000.0..660_000.0)
        assertEquals(3_600_000L, stats.durationMillis)
    }

    @Test
    fun teleportJumps_ignored() {
        val nyc = GeoPointData(0L, 40.7128, -74.0060)
        val moscow = GeoPointData(1000L, 55.7558, 37.6173)
        val stats = TrackStatsCalculator.compute(listOf(nyc, moscow))
        assertEquals(0.0, stats.distanceMeters, 0.001)
    }

    @Test
    fun pointsSorted_byTimestamp() {
        val a = GeoPointData(2000L, 55.0, 37.0)
        val b = GeoPointData(1000L, 55.001, 37.001)
        val stats = TrackStatsCalculator.compute(listOf(a, b))
        assertEquals(1000L, stats.startAt)
        assertEquals(2000L, stats.endAt)
        assertTrue(stats.distanceMeters > 100)
    }
}
