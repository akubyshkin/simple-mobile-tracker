package app.traceday.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.traceday.R
import app.traceday.TraceDayApp
import app.traceday.core.Clock
import app.traceday.core.day.DayKeys
import app.traceday.data.local.TrackDao
import app.traceday.data.local.TrackEntity
import app.traceday.data.local.TrackKind
import app.traceday.data.local.TrackPointEntity
import app.traceday.data.prefs.AccuracyMode
import app.traceday.data.prefs.SettingsRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : android.app.Service() {

    @Inject lateinit var dao: TrackDao
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var clock: Clock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fused: FusedLocationProviderClient? = null
    private var running = false
    private var currentDayKey: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            else -> {
                if (!running) {
                    serviceScope.launch { startTracking() }
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startTracking() {
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }
        val appSettings = settingsRepository.settings.first()
        startAsForeground()
        settingsRepository.setTrackingRunning(true)
        running = true
        currentDayKey = null
        val client = fused
            ?: LocationServices.getFusedLocationProviderClient(this).also { fused = it }
        val priority = if (appSettings.accuracy == AccuracyMode.HIGH) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val interval = appSettings.intervalSeconds * 1000L
        val request = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(interval / 2)
            .setMaxUpdateDelayMillis(interval * 3)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopTracking()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
            val location = result.lastLocation ?: return
            val ts = if (location.time > 0) location.time else clock.now()
            val dayKey = DayKeys.keyFor(ts)
            serviceScope.launch {
                if (currentDayKey != dayKey) {
                    currentDayKey = dayKey
                    dao.insertTrack(
                        TrackEntity(
                            id = DayKeys.selfTrackId(dayKey),
                            title = dayKey,
                            kind = TrackKind.SELF,
                            addedAt = clock.now()
                        )
                    )
                }
                dao.insertPoints(
                    listOf(
                        TrackPointEntity(
                            trackId = DayKeys.selfTrackId(dayKey),
                            timestamp = ts,
                            lat = location.latitude,
                            lon = location.longitude,
                            accuracy = if (location.hasAccuracy()) location.accuracy else null,
                            provider = location.provider
                        )
                    )
                )
            }
        }
    }

    private fun stopTracking() {
        runBlocking { runCatching { settingsRepository.setTrackingRunning(false) } }
        runCatching { fused?.removeLocationUpdates(locationCallback) }
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(this, TraceDayApp.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle(getString(R.string.notif_tracking_title))
            .setContentText(getString(R.string.notif_tracking_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        runCatching { fused?.removeLocationUpdates(locationCallback) }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.traceday.action.START"
        const val ACTION_STOP = "app.traceday.action.STOP"
        private const val NOTIFICATION_ID = 42
        private const val REQUEST_STOP = 1
        private const val REQUEST_OPEN = 2

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TrackingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
