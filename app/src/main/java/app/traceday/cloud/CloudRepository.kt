package app.traceday.cloud

import android.content.Intent
import app.traceday.cloud.google.GoogleAuthManager
import app.traceday.cloud.google.GoogleDriveClient
import app.traceday.cloud.yandex.YandexAuthManager
import app.traceday.cloud.yandex.YandexDiskClient
import app.traceday.core.day.DayKeys
import app.traceday.data.local.TrackDao
import app.traceday.data.prefs.CloudProviderOption
import app.traceday.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val dao: TrackDao,
    private val googleDriveClient: GoogleDriveClient,
    private val yandexDiskClient: YandexDiskClient,
    private val googleAuthManager: GoogleAuthManager,
    private val yandexAuthManager: YandexAuthManager,
    private val tokenStore: TokenStore
) {

    val authState: Flow<CloudAuthState> =
        combine(settingsRepository.settings, tokenStore.authFlow) { s, tokens ->
            val signedIn = when (s.cloud) {
                CloudProviderOption.GOOGLE -> tokens[CloudProvider.GOOGLE]?.account != null
                CloudProviderOption.YANDEX -> tokens[CloudProvider.YANDEX]?.accessToken != null
                CloudProviderOption.NONE -> false
            }
            val account = when (s.cloud) {
                CloudProviderOption.GOOGLE -> tokens[CloudProvider.GOOGLE]?.account
                else -> null
            }
            CloudAuthState(s.cloud, signedIn, account, s.authIssue)
        }

    suspend fun currentClient(): CloudClient? =
        when (settingsRepository.settings.first().cloud) {
            CloudProviderOption.GOOGLE -> googleDriveClient
            CloudProviderOption.YANDEX -> yandexDiskClient
            CloudProviderOption.NONE -> null
        }

    suspend fun publishDay(dayKey: String): String {
        val client = currentClient() ?: throw CloudException("Облако не выбрано в настройках")
        val url = client.publishDayFile(dayKey)
        dao.updateRemoteInfo(DayKeys.selfTrackId(dayKey), url, null)
        return url
    }

    suspend fun deleteRemoteOlderThan(cutoffMillis: Long): Int =
        currentClient()?.deleteFilesOlderThan(cutoffMillis) ?: 0

    fun googleSignInIntent(): Intent = googleAuthManager.signInIntent()

    suspend fun handleGoogleSignIn(data: Intent?): Boolean =
        googleAuthManager.handleSignInResult(data)

    suspend fun tryGoogleSilentSignIn(): Boolean = googleAuthManager.trySilentSignIn()

    fun beginYandexAuth(): String? = yandexAuthManager.beginAuthSession()

    fun isYandexConfigured(): Boolean = yandexAuthManager.isConfigured

    suspend fun signOut(option: CloudProviderOption) {
        when (option) {
            CloudProviderOption.GOOGLE -> googleAuthManager.signOut()
            CloudProviderOption.YANDEX -> yandexAuthManager.signOut()
            CloudProviderOption.NONE -> Unit
        }
    }
}
