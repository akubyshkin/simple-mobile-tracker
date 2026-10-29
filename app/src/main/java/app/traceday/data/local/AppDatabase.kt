package app.traceday.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        const val NAME = "traceday.db"
    }
}
