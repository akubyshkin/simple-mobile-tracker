package app.traceday.cloud.yandex

import android.net.Uri
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
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

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

    val isConfigured: Boolean get() = BuildConfig.YANDEX_CLIENT_ID.isNotEmpty()

    fun authUrl(): String? {
        if (!isConfigured) return null
        val redirect = URLEncoder.encode(BuildConfig.YANDEX_REDIRECT_URI, "UTF-8")
        return "https://oauth.yandex.ru/authorize?response_type=code" +
            "&client_id=${BuildConfig.YANDEX_CLIENT_ID}" +
            "&redirect_uri=$redirect" +
            "&force_confirm=true"
    }

    fun matchesRedirect(uri: Uri?): Boolean =
        uri?.scheme == BuildConfig.YANDEX_REDIRECT_URI.substringBefore("://") &&
            uri.host == BuildConfig.YANDEX_REDIRECT_URI.substringAfter("://")

    suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val token = requestToken(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to BuildConfig.YANDEX_CLIENT_ID,
                "redirect_uri" to BuildConfig.YANDEX_REDIRECT_URI
            )
        ) ?: return@withContext false
        tokenStore.save(CloudProvider.YANDEX, token)
        true
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
                "client_id" to BuildConfig.YANDEX_CLIENT_ID
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

    companion object {
        private const val TOKEN_URL = "https://oauth.yandex.ru/token"
        private const val EXPIRY_MARGIN = 60_000L
    }
}
