package app.traceday.core.retention

class RetentionPolicy(private val retentionDays: Int = DEFAULT_DAYS) {

    val days: Int get() = retentionDays

    fun cutoffTimestamp(nowMillis: Long): Long = nowMillis - retentionDays * MILLIS_PER_DAY

    fun isExpired(lastPointTimestamp: Long, nowMillis: Long): Boolean =
        lastPointTimestamp < cutoffTimestamp(nowMillis)

    companion object {
        const val DEFAULT_DAYS = 7
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
