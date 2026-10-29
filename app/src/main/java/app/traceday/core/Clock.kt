package app.traceday.core

interface Clock {
    fun now(): Long
}

class WallClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
