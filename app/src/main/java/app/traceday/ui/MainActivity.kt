package app.traceday.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.traceday.cloud.yandex.YandexAuthManager
import app.traceday.cloud.yandex.YandexAuthResult
import app.traceday.data.prefs.CloudProviderOption
import app.traceday.data.prefs.SettingsRepository
import app.traceday.ui.theme.TraceDayTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var yandexAuthManager: YandexAuthManager
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        setContent {
            TraceDayTheme {
                TraceDayRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (!yandexAuthManager.matchesRedirect(uri)) return
        lifecycleScope.launch(Dispatchers.IO) {
            when (yandexAuthManager.handleRedirect(uri)) {
                is YandexAuthResult.Success ->
                    settingsRepository.setCloud(CloudProviderOption.YANDEX)
                is YandexAuthResult.Error -> Unit
            }
        }
    }
}
