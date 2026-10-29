package app.traceday.cloud.yandex

import android.net.Uri
import androidx.annotation.VisibleForTesting
import app.traceday.BuildConfig
import app.traceday.cloud.CloudProvider
import app.traceday.cloud.StoredAuth
import app.traceday.cloud.TokenStore
import app.traceday.cloud.defaultJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed class YandexAuthResult {
    data object Success : YandexAuthResult()
    data class Error(val message: String) : YandexAuthResult()
}

@Serializable
data class YandexTokenDto(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null
)

@Singleton
class YandexAuthManager @Inject constructor(
    private val tokenStore: TokenStore,
    @Named("plain") private val http: OkHttpClient
) {

    private val json = defaultJson
    private val random = SecureRandom()

    @Volatile private var pendingState: String? = null
    @Volatile private var pendingStateAt: Long = 0

    @VisibleForTesting
    internal var clientIdOverride: String? = null

    private val clientId: String
        get() = clientIdOverride ?: BuildConfig.YANDEX_CLIENT_ID

    val isConfigured: Boolean get() = clientId.isNotEmpty()

    fun matchesRedirect(uri: Uri?): Boolean =
        uri?.scheme == BuildConfig.YANDEX_REDIRECT_URI.substringBefore("://") &&
            uri.host == BuildConfig.YANDEX_REDIRECT_URI.substringAfter("://")

    @Synchronized
    fun beginAuthSession(): String? {
        if (!isConfigured) return null
        val state = randomState()
        pendingState = state
        pendingStateAt = System.currentTimeMillis()
        val redirect = URLEncoder.encode(BuildConfig.YANDEX_REDIRECT_URI, "UTF-8")
        return "https://oauth.yandex.ru/authorize?response_type=code" +
            "&client_id=$clientId" +
            "&redirect_uri=$redirect" +
            "&state=$state" +
            "&force_confirm=true"
    }

    suspend fun handleRedirect(uri: Uri): YandexAuthResult {
        val expected = synchronized(this) {
            val state = pendingState
            pendingState = null
            state
        }
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        return when {
            error != null -> YandexAuthResult.Error("Авторизация отклонена")
            code == null -> YandexAuthResult.Error("В ответе нет кода авторизации")
            expected == null -> YandexAuthResult.Error("Вход не был инициирован из приложения")
            state != expected -> YandexAuthResult.Error("Неверный параметр state — запрос отклонён")
            System.currentTimeMillis() - pendingStateAt > SESSION_TTL_MS ->
                YandexAuthResult.Error("Сессия входа истекла, попробуйте ещё раз")
            exchangeCode(code) -> YandexAuthResult.Success
            else -> YandexAuthResult.Error("Не удалось обменять код на токен")
        }
    }

    @Synchronized
    fun refreshTokenBlocking(): StoredAuth? {
        val current = tokenStore.get(CloudProvider.YANDEX)
        if (current.accessToken != null && current.expiresAtMillis - EXPIRY_MARGIN > System.currentTimeMillis()) {
            return current
        }
        val refresh = current.refreshToken ?: return null
        val fresh = requestToken(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refresh,
                "client_id" to clientId
            )
        ) ?: return null
        val merged = fresh.copy(refreshToken = fresh.refreshToken ?: refresh)
        tokenStore.save(CloudProvider.YANDEX, merged)
        return merged
    }

    fun currentTokenBlocking(): String? = refreshTokenBlocking()?.accessToken

    fun signOut() {
        tokenStore.clear(CloudProvider.YANDEX)
    }

    private suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val token = requestToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to clientId,
                "redirect_uri" to BuildConfig.YANDEX_REDIRECT_URI
            )
        ) ?: return@withContext false
        tokenStore.save(CloudProvider.YANDEX, token)
        true
    }

    private fun requestToken(form: Map<String, String>): StoredAuth? {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        return try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: return null
                if (!response.isSuccessful) return null
                val dto = json.decodeFromString<YandexTokenDto>(text)
                if (dto.access_token == null) null
                else StoredAuth(
                    accessToken = dto.access_token,
                    refreshToken = dto.refresh_token,
                    expiresAtMillis = System.currentTimeMillis() + (dto.expires_in ?: 0L) * 1000L
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun randomState(): String {
        val bytes = ByteArray(STATE_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TOKEN_URL = "https://oauth.yandex.ru/token"
        private const val EXPIRY_MARGIN = 60_000L
        private const val SESSION_TTL_MS = 10 * 60_000L
        private const val STATE_BYTES = 16
    }
}
