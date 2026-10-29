package app.traceday.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.traceday.cloud.CloudRepository
import app.traceday.core.Clock
import app.traceday.core.retention.RetentionPolicy
import app.traceday.data.local.TrackDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TrackDao,
    private val cloudRepository: CloudRepository,
    private val policy: RetentionPolicy,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cutoff = policy.cutoffTimestamp(clock.now())
        val expired = dao.trackIdsOlderThan(cutoff)
        if (expired.isNotEmpty()) {
            dao.deletePoints(expired)
            dao.deleteTracks(expired)
        }
        runCatching { cloudRepository.deleteRemoteOlderThan(cutoff) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "traceday-cleanup"
    }
}
