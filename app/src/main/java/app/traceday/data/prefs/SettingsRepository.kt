package app.traceday.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore("traceday_settings")

enum class CloudProviderOption { NONE, GOOGLE, YANDEX }
enum class AccuracyMode { HIGH, BALANCED }

data class AppSettings(
    val intervalSeconds: Int = 60,
    val accuracy: AccuracyMode = AccuracyMode.HIGH,
    val cloud: CloudProviderOption = CloudProviderOption.NONE,
    val cloudFolder: String = "",
    val autoStartOnBoot: Boolean = false,
    val trackingRunning: Boolean = false,
    val lastSyncAt: Long = 0,
    val authIssue: Boolean = false,
    val lastSyncMessage: String? = null
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val INTERVAL = intPreferencesKey("interval_seconds")
        val ACCURACY = stringPreferencesKey("accuracy_mode")
        val CLOUD = stringPreferencesKey("cloud_provider")
        val CLOUD_FOLDER = stringPreferencesKey("cloud_folder")
        val AUTO_START = booleanPreferencesKey("auto_start_on_boot")
        val TRACKING_RUNNING = booleanPreferencesKey("tracking_running")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val AUTH_ISSUE = booleanPreferencesKey("auth_issue")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            intervalSeconds = (p[Keys.INTERVAL] ?: 60).coerceIn(10, 900),
            accuracy = runCatching { AccuracyMode.valueOf(p[Keys.ACCURACY] ?: "") }.getOrDefault(AccuracyMode.HIGH),
            cloud = runCatching { CloudProviderOption.valueOf(p[Keys.CLOUD] ?: "") }.getOrDefault(CloudProviderOption.NONE),
            cloudFolder = app.traceday.cloud.CloudFolderPath.normalizeParent(p[Keys.CLOUD_FOLDER]),
            autoStartOnBoot = p[Keys.AUTO_START] ?: false,
            trackingRunning = p[Keys.TRACKING_RUNNING] ?: false,
            lastSyncAt = p[Keys.LAST_SYNC_AT] ?: 0L,
            authIssue = p[Keys.AUTH_ISSUE] ?: false,
            lastSyncMessage = p[Keys.LAST_SYNC_MESSAGE]?.takeIf { it.isNotEmpty() }
        )
    }

    suspend fun setInterval(seconds: Int) = edit { it[Keys.INTERVAL] = seconds.coerceIn(10, 900) }

    suspend fun setAccuracy(mode: AccuracyMode) = edit { it[Keys.ACCURACY] = mode.name }

    suspend fun setCloud(provider: CloudProviderOption) = edit { it[Keys.CLOUD] = provider.name }

    suspend fun setCloudFolder(folder: String) = edit {
        it[Keys.CLOUD_FOLDER] = app.traceday.cloud.CloudFolderPath.normalizeParent(folder)
    }

    suspend fun setAutoStart(enabled: Boolean) = edit { it[Keys.AUTO_START] = enabled }

    suspend fun setTrackingRunning(running: Boolean) = edit { it[Keys.TRACKING_RUNNING] = running }

    suspend fun setSyncResult(ok: Boolean, message: String?) = edit {
        it[Keys.LAST_SYNC_AT] = System.currentTimeMillis()
        it[Keys.AUTH_ISSUE] = !ok && message == MESSAGE_AUTH
        it[Keys.LAST_SYNC_MESSAGE] = message ?: ""
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }

    companion object {
        const val MESSAGE_AUTH = "auth"
        const val MESSAGE_NETWORK = "network"
    }
}
