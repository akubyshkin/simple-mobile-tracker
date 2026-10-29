package app.traceday

import app.traceday.core.day.DayKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DayKeysTest {

    private val utc = ZoneOffset.UTC

    @Test
    fun keyFor_beforeMidnight_staysSameDay() {
        val ts = ZonedDateTime.of(2026, 10, 29, 23, 59, 59, 0, utc).toInstant().toEpochMilli()
        assertEquals("2026-10-29", DayKeys.keyFor(ts, utc))
    }

    @Test
    fun keyFor_afterMidnight_movesToNextDay() {
        val ts = ZonedDateTime.of(2026, 10, 30, 0, 0, 1, 0, utc).toInstant().toEpochMilli()
        assertEquals("2026-10-30", DayKeys.keyFor(ts, utc))
    }

    @Test
    fun keyFor_respectsZone() {
        val ts = ZonedDateTime.of(2026, 10, 29, 23, 30, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals("2026-10-30", DayKeys.keyFor(ts, ZoneOffset.of("+03:00")))
    }

    @Test
    fun bounds_coverWholeDay() {
        val bounds = DayKeys.bounds("2026-10-29", utc)
        assertEquals(
            Instant.parse("2026-10-29T00:00:00Z").toEpochMilli(),
            bounds.first
        )
        assertEquals(
            Instant.parse("2026-10-30T00:00:00Z").toEpochMilli() - 1,
            bounds.last
        )
        assertEquals(86_400_000L, bounds.last - bounds.first + 1)
    }

    @Test
    fun fileName_containsDayKey() {
        assertEquals("track-2026-10-29.jsonl", DayKeys.fileName("2026-10-29"))
    }

    @Test
    fun selfTrackId_prefixed() {
        assertEquals("self-2026-10-29", DayKeys.selfTrackId("2026-10-29"))
    }
}
