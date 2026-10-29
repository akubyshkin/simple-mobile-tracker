package app.traceday

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.traceday.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TraceDayApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
        createNotificationChannels()
        SyncScheduler.scheduleAll(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING,
                getString(R.string.notif_channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYNC,
                getString(R.string.notif_channel_sync),
                NotificationManager.IMPORTANCE_MIN
            )
        )
    }

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_SYNC = "sync"
    }
}
