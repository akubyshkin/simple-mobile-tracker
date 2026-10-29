package app.traceday.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TrackKind {
    SELF,
    IMPORTED
}

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: TrackKind,
    val sourceUrl: String? = null,
    val remoteFileId: String? = null,
    val publicUrl: String? = null,
    val colorArgb: Long = 0xFF4F8CFF,
    val enabled: Boolean = true,
    val addedAt: Long = 0
)

@Entity(
    tableName = "points",
    primaryKeys = ["trackId", "timestamp"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId"), Index("timestamp")]
)
data class TrackPointEntity(
    val trackId: String,
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float? = null,
    val provider: String? = null,
    val uploaded: Boolean = false
)

data class TrackAgg(
    val trackId: String,
    val firstTs: Long,
    val lastTs: Long,
    val pointCount: Int
)
