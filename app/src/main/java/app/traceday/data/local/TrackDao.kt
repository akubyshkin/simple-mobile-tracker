package app.traceday.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun track(id: String): TrackEntity?

    @Query("SELECT * FROM tracks")
    fun tracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE kind = 'IMPORTED' ORDER BY addedAt DESC")
    fun importedTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM points WHERE trackId IN (:trackIds) AND timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun pointsFlow(trackIds: List<String>, from: Long, to: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM points WHERE trackId = :trackId ORDER BY timestamp")
    suspend fun pointsOnce(trackId: String): List<TrackPointEntity>

    @Query("SELECT * FROM points WHERE trackId IN (:trackIds) AND timestamp BETWEEN :from AND :to ORDER BY timestamp")
    suspend fun pointsOnce(trackIds: List<String>, from: Long, to: Long): List<TrackPointEntity>

    @Query(
        "SELECT trackId AS trackId, MIN(timestamp) AS firstTs, MAX(timestamp) AS lastTs, COUNT(*) AS pointCount " +
            "FROM points GROUP BY trackId"
    )
    fun summariesFlow(): Flow<List<TrackAgg>>

    @Query("SELECT DISTINCT trackId FROM points WHERE uploaded = 0")
    suspend fun trackIdsWithUnsynced(): List<String>

    @Query("SELECT COUNT(*) FROM points WHERE uploaded = 0")
    fun unsyncedCountFlow(): Flow<Int>

    @Query("UPDATE points SET uploaded = 1 WHERE trackId = :trackId AND uploaded = 0")
    suspend fun markUploaded(trackId: String)

    @Query("UPDATE tracks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE tracks SET publicUrl = :publicUrl, remoteFileId = :fileId WHERE id = :id")
    suspend fun updateRemoteInfo(id: String, publicUrl: String?, fileId: String?)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)

    @Query("SELECT trackId FROM points GROUP BY trackId HAVING MAX(timestamp) < :cutoff")
    suspend fun trackIdsOlderThan(cutoff: Long): List<String>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracks(ids: List<String>)

    @Query("DELETE FROM points WHERE trackId IN (:ids)")
    suspend fun deletePoints(ids: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun clearTracks()

    @Query("DELETE FROM points")
    suspend fun clearPoints()
}
