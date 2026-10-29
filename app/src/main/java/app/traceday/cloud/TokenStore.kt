package app.traceday.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class StoredAuth(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAtMillis: Long = 0,
    val account: String? = null
)

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val lock = Any()
    private var prefsOrNull: SharedPreferences? = null
    private var encryptionBroken = false

    private val _authFlow = MutableStateFlow<Map<CloudProvider, StoredAuth>>(emptyMap())
    val authFlow: StateFlow<Map<CloudProvider, StoredAuth>> = _authFlow.asStateFlow()

    init {
        _authFlow.value = readAll()
    }

    fun get(provider: CloudProvider): StoredAuth = _authFlow.value[provider] ?: StoredAuth()

    fun save(provider: CloudProvider, auth: StoredAuth) {
        val prefs = prefs()
        if (prefs != null) {
            prefs.edit()
                .putString(key(provider, ACCESS), auth.accessToken)
                .putString(key(provider, REFRESH), auth.refreshToken)
                .putLong(key(provider, EXPIRES), auth.expiresAtMillis)
                .putString(key(provider, ACCOUNT), auth.account)
                .apply()
        }
        _authFlow.update { it + (provider to auth) }
    }

    fun clear(provider: CloudProvider) {
        prefs()?.edit()
            ?.remove(key(provider, ACCESS))
            ?.remove(key(provider, REFRESH))
            ?.remove(key(provider, EXPIRES))
            ?.remove(key(provider, ACCOUNT))
            ?.apply()
        _authFlow.update { it - provider }
    }

    private fun read(provider: CloudProvider): StoredAuth {
        val prefs = prefs() ?: return StoredAuth()
        return StoredAuth(
            accessToken = prefs.getString(key(provider, ACCESS), null),
            refreshToken = prefs.getString(key(provider, REFRESH), null),
            expiresAtMillis = prefs.getLong(key(provider, EXPIRES), 0),
            account = prefs.getString(key(provider, ACCOUNT), null)
        )
    }

    private fun readAll(): Map<CloudProvider, StoredAuth> {
        if (prefs() == null) return emptyMap()
        return CloudProvider.entries.associateWith { read(it) }
    }

    private fun prefs(): SharedPreferences? = synchronized(lock) {
        if (encryptionBroken) return null
        if (prefsOrNull != null) return prefsOrNull
        val created = runCatching { createEncrypted() }
        if (created.isSuccess) {
            prefsOrNull = created.getOrThrow()
            prefsOrNull
        } else {
            encryptionBroken = true
            null
        }
    }

    private fun createEncrypted(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun key(provider: CloudProvider, suffix: String) = "${provider.name.lowercase()}.$suffix"

    private companion object {
        const val FILE_NAME = "traceday_tokens"
        const val ACCESS = "access"
        const val REFRESH = "refresh"
        const val EXPIRES = "expires"
        const val ACCOUNT = "account"
    }
}
