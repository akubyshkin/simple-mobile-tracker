package app.traceday.sync

import android.content.Context
import android.content.Intent
import app.traceday.data.prefs.SettingsRepository
import app.traceday.service.TrackingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : android.content.BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.settings.first()
                if (settings.autoStartOnBoot && settings.trackingRunning) {
                    TrackingService.start(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
