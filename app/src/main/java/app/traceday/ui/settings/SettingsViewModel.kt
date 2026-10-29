package app.traceday.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traceday.cloud.CloudAuthState
import app.traceday.cloud.CloudRepository
import app.traceday.data.local.TrackDao
import app.traceday.data.prefs.AccuracyMode
import app.traceday.data.prefs.AppSettings
import app.traceday.data.prefs.CloudProviderOption
import app.traceday.data.prefs.SettingsRepository
import app.traceday.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val auth: CloudAuthState = CloudAuthState(CloudProviderOption.NONE, false, null, false),
    val yandexConfigured: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cloudRepository: CloudRepository,
    private val dao: TrackDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        cloudRepository.authState
    ) { settings, auth ->
        SettingsUiState(settings, auth, cloudRepository.isYandexConfigured())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun googleSignInIntent(): Intent = cloudRepository.googleSignInIntent()

    fun beginYandexAuth(): String? = cloudRepository.beginYandexAuth()

    fun onGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            if (cloudRepository.handleGoogleSignIn(data)) {
                settingsRepository.setCloud(CloudProviderOption.GOOGLE)
            }
        }
    }

    fun selectCloud(option: CloudProviderOption) {
        viewModelScope.launch {
            settingsRepository.setCloud(option)
            if (option == CloudProviderOption.GOOGLE) {
                cloudRepository.tryGoogleSilentSignIn()
            }
        }
    }

    fun signOutCurrent() {
        viewModelScope.launch {
            val current = uiState.value.settings.cloud
            if (current != CloudProviderOption.NONE) {
                cloudRepository.signOut(current)
                settingsRepository.setCloud(CloudProviderOption.NONE)
            }
        }
    }

    fun setInterval(seconds: Int) {
        viewModelScope.launch { settingsRepository.setInterval(seconds) }
    }

    fun setAccuracy(mode: AccuracyMode) {
        viewModelScope.launch { settingsRepository.setAccuracy(mode) }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoStart(enabled) }
    }

    fun setCloudFolder(folder: String) {
        viewModelScope.launch { settingsRepository.setCloudFolder(folder) }
    }

    fun syncNow() {
        SyncScheduler.syncNow(appContext)
    }

    fun clearAllData() {
        viewModelScope.launch {
            dao.clearPoints()
            dao.clearTracks()
        }
    }
}
