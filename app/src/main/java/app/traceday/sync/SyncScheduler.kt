package app.traceday.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    fun scheduleAll(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val sync = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            sync
        )

        val cleanup = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        workManager.enqueueUniquePeriodicWork(
            CleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanup
        )
    }

    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("${SyncWorker.UNIQUE_NAME}-now", ExistingWorkPolicy.REPLACE, request)
    }
}
