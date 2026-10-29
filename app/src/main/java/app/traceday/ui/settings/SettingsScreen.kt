package app.traceday.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.traceday.R
import app.traceday.data.prefs.AccuracyMode
import app.traceday.data.prefs.CloudProviderOption
import app.traceday.ui.components.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val intervalOptions = listOf(15, 30, 60, 120, 300)

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vm.onGoogleSignInResult(result.data)
    }

    LaunchedEffect(state.auth.authIssue) {
        if (state.auth.authIssue) {
            snackbarHost.showSnackbar("Требуется повторная авторизация облака")
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Удалить все локальные данные?") },
            text = { Text("Треки и точки будут удалены с устройства. Файлы в облаке останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllData()
                    showClearDialog = false
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SnackbarHost(hostState = snackbarHost)

        SectionCard(stringResource(R.string.settings_section_recording)) {
            Text(
                stringResource(R.string.settings_interval),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                intervalOptions.forEach { seconds ->
                    intervalChip(
                        seconds = seconds,
                        selected = state.settings.intervalSeconds == seconds,
                        onClick = { vm.setInterval(seconds) }
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            RadioRow(
                icon = { Icon(Icons.Filled.SatelliteAlt, contentDescription = null) },
                title = stringResource(R.string.settings_high_accuracy),
                selected = state.settings.accuracy == AccuracyMode.HIGH,
                onClick = { vm.setAccuracy(AccuracyMode.HIGH) }
            )
            RadioRow(
                icon = { Icon(Icons.Filled.SettingsInputAntenna, contentDescription = null) },
                title = stringResource(R.string.settings_balanced),
                selected = state.settings.accuracy == AccuracyMode.BALANCED,
                onClick = { vm.setAccuracy(AccuracyMode.BALANCED) }
            )
            SwitchRow(
                title = stringResource(R.string.settings_autostart),
                checked = state.settings.autoStartOnBoot,
                onChange = { vm.setAutoStart(it) }
            )
        }

        SectionCard(stringResource(R.string.settings_section_cloud)) {
            ProviderRow(
                label = stringResource(R.string.settings_provider_none),
                selected = state.settings.cloud == CloudProviderOption.NONE,
                onClick = { vm.selectCloud(CloudProviderOption.NONE) }
            )
            ProviderRow(
                label = stringResource(R.string.settings_provider_google),
                selected = state.settings.cloud == CloudProviderOption.GOOGLE,
                onClick = { vm.selectCloud(CloudProviderOption.GOOGLE) }
            )
            ProviderRow(
                label = stringResource(R.string.settings_provider_yandex),
                selected = state.settings.cloud == CloudProviderOption.YANDEX,
                onClick = { vm.selectCloud(CloudProviderOption.YANDEX) }
            )

            when (state.settings.cloud) {
                CloudProviderOption.GOOGLE -> {
                    if (state.auth.signedIn) {
                        Text(
                            state.auth.account ?: "Подключено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        OutlinedButton(onClick = { vm.signOutCurrent() }) {
                            Icon(Icons.Filled.Logout, contentDescription = null)
                            Text(
                                stringResource(R.string.settings_sign_out),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    } else {
                        Button(onClick = { googleLauncher.launch(vm.googleSignInIntent()) }) {
                            Icon(Icons.Filled.Cloud, contentDescription = null)
                            Text(
                                stringResource(R.string.settings_sign_in),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
                CloudProviderOption.YANDEX -> {
                    if (state.auth.signedIn) {
                        Text(
                            "Яндекс Диск подключён",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        OutlinedButton(onClick = { vm.signOutCurrent() }) {
                            Icon(Icons.Filled.Logout, contentDescription = null)
                            Text(
                                stringResource(R.string.settings_sign_out),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    } else if (state.yandexConfigured) {
                        Button(onClick = {
                            vm.beginYandexAuth()?.let { url ->
                                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
                            }
                        }) {
                            Icon(Icons.Filled.Cloud, contentDescription = null)
                            Text(
                                stringResource(R.string.settings_sign_in),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    } else {
                        Text(
                            "Добавьте yandex.clientId в local.properties (см. README)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                CloudProviderOption.NONE -> Unit
            }

            Button(onClick = { vm.syncNow() }, enabled = state.settings.cloud != CloudProviderOption.NONE) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    stringResource(R.string.settings_sync_now),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            if (state.settings.lastSyncAt > 0) {
                Text(
                    "Последняя синхронизация: " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
                        .format(Date(state.settings.lastSyncAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(stringResource(R.string.settings_section_storage)) {
            Text(
                stringResource(R.string.settings_retention),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { showClearDialog = true }) {
                Text(stringResource(R.string.settings_clear_all))
            }
        }

        SectionCard(stringResource(R.string.settings_section_about)) {
            Text(
                "TraceDay 0.1.0 · Карта: OpenStreetMap (osmdroid)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun intervalChip(seconds: Int, selected: Boolean, onClick: () -> Unit) {
    val label = if (seconds < 60) "$seconds с" else "${seconds / 60} мин"
    TextButton(onClick = onClick) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RadioRow(
    icon: @Composable () -> Unit,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        icon()
        Text(
            title,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ProviderRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
