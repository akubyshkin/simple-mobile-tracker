package app.traceday.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.traceday.cloud.AuthRequiredException
import app.traceday.cloud.CloudRepository
import app.traceday.cloud.format.PointDto
import app.traceday.cloud.format.TrackFileCodec
import app.traceday.core.Clock
import app.traceday.data.local.TrackDao
import app.traceday.data.prefs.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: TrackDao,
    private val cloudRepository: CloudRepository,
    private val codec: TrackFileCodec,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val client = cloudRepository.currentClient() ?: run {
            settingsRepository.setSyncResult(ok = true, message = null)
            return Result.success()
        }
        return try {
            client.ensureStorage()
            val trackIds = dao.trackIdsWithUnsynced()
            for (trackId in trackIds) {
                val dayKey = trackId.removePrefix(SELF_PREFIX)
                if (dayKey == trackId) continue
                val points = dao.pointsOnce(trackId)
                if (points.isEmpty()) {
                    dao.markUploaded(trackId)
                    continue
                }
                val content = codec.encode(
                    points.map { PointDto(it.timestamp, it.lat, it.lon, it.accuracy, it.provider) }
                )
                client.uploadDayFile(dayKey, content)
                dao.markUploaded(trackId)
            }
            settingsRepository.setSyncResult(ok = true, message = null)
            Result.success()
        } catch (e: AuthRequiredException) {
            settingsRepository.setSyncResult(ok = false, message = SettingsRepository.MESSAGE_AUTH)
            Result.failure()
        } catch (e: IOException) {
            settingsRepository.setSyncResult(ok = false, message = SettingsRepository.MESSAGE_NETWORK)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: Exception) {
            settingsRepository.setSyncResult(ok = false, message = e.message)
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "traceday-sync"
        private const val SELF_PREFIX = "self-"
        private const val MAX_RETRIES = 5
    }
}
