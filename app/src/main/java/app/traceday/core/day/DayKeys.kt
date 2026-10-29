package app.traceday.core.day

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object DayKeys {
    fun keyFor(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toString()

    fun bounds(key: String, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val date = LocalDate.parse(key)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start..end
    }

    fun fileName(key: String): String = "track-$key.jsonl"

    fun selfTrackId(key: String): String = "self-$key"
}
