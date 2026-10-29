package app.traceday

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.traceday.data.local.AppDatabase
import app.traceday.data.local.TrackDao
import app.traceday.data.local.TrackEntity
import app.traceday.data.local.TrackKind
import app.traceday.data.local.TrackPointEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TrackDao

    private val day = "2026-10-29"
    private val trackId = "self-$day"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.trackDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedDay(base: Long, count: Int) {
        dao.insertTrack(TrackEntity(id = trackId, title = day, kind = TrackKind.SELF))
        dao.insertPoints(
            (0 until count).map {
                TrackPointEntity(
                    trackId = trackId,
                    timestamp = base + it * 60_000L,
                    lat = 55.0 + it * 0.001,
                    lon = 37.0
                )
            }
        )
    }

    @Test
    fun pointsOnce_returnsPointsInRange() = runTest {
        seedDay(base = 1000L, count = 5)
        dao.insertTrack(TrackEntity(id = "self-2026-10-28", title = "x", kind = TrackKind.SELF))
        dao.insertPoints(
            listOf(
                TrackPointEntity(trackId = "self-2026-10-28", timestamp = 500L, lat = 1.0, lon = 1.0)
            )
        )
        val points = dao.pointsOnce(listOf(trackId), 0L, 1000L + 4 * 60_000L)
        assertEquals(5, points.size)
        assertTrue(points.all { it.trackId == trackId })
    }

    @Test
    fun unsyncedTrackIds_thenMarkUploaded() = runTest {
        seedDay(base = 1000L, count = 3)
        assertEquals(listOf(trackId), dao.trackIdsWithUnsynced())
        dao.markUploaded(trackId)
        assertTrue(dao.trackIdsWithUnsynced().isEmpty())
    }

    @Test
    fun summaries_aggregatePerTrack() = runTest {
        seedDay(base = 1000L, count = 4)
        val summaries = dao.summariesFlow().first()
        assertEquals(1, summaries.size)
        assertEquals(trackId, summaries[0].trackId)
        assertEquals(4, summaries[0].pointCount)
        assertEquals(1000L, summaries[0].firstTs)
        assertEquals(1000L + 3 * 60_000L, summaries[0].lastTs)
    }

    @Test
    fun deleteOlderThan_removesTrackAndCascadesPoints() = runTest {
        seedDay(base = 1000L, count = 2)
        dao.insertTrack(TrackEntity(id = "self-2026-10-30", title = "y", kind = TrackKind.SELF))
        dao.insertPoints(
            listOf(
                TrackPointEntity(trackId = "self-2026-10-30", timestamp = 10_000_000L, lat = 2.0, lon = 2.0)
            )
        )
        val cutoff = 1_000_000L
        assertEquals(listOf(trackId), dao.trackIdsOlderThan(cutoff))
        val ids = dao.trackIdsOlderThan(cutoff)
        dao.deletePoints(ids)
        dao.deleteTracks(ids)
        assertTrue(dao.pointsOnce(listOf(trackId), 0L, Long.MAX_VALUE).isEmpty())
        assertEquals(null, dao.track(trackId))
        assertEquals(1, dao.pointsOnce(listOf("self-2026-10-30"), 0L, Long.MAX_VALUE).size)
    }

    @Test
    fun deleteTrack_cascadesPoints() = runTest {
        seedDay(base = 1000L, count = 2)
        dao.deleteTrack(trackId)
        assertTrue(dao.pointsOnce(listOf(trackId), 0L, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun setEnabled_updatesImportedToggle() = runTest {
        dao.insertTrack(
            TrackEntity(id = "imp1", title = "Импорт", kind = TrackKind.IMPORTED, enabled = true)
        )
        dao.setEnabled("imp1", false)
        assertEquals(false, dao.track("imp1")?.enabled)
    }
}
