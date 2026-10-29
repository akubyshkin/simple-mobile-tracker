package app.traceday.cloud.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import app.traceday.cloud.AuthRequiredException
import app.traceday.cloud.CloudProvider
import app.traceday.cloud.StoredAuth
import app.traceday.cloud.TokenStore
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore
) {

    private val authScope = "oauth2:${Scopes.DRIVE_FILE}"
    private var cachedToken: String? = null

    private val signInClient by lazy {
        val gso = GoogleSignInOptions.Builder()
            .requestEmail()
            .requestScopes(Scope(Scopes.DRIVE_FILE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun isSignedIn(): Boolean = tokenStore.get(CloudProvider.GOOGLE).account != null

    fun signInIntent(): Intent = signInClient.signInIntent

    suspend fun handleSignInResult(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val name = account?.email ?: account?.displayName
            if (name != null) {
                tokenStore.save(CloudProvider.GOOGLE, StoredAuth(account = name))
                true
            } else {
                false
            }
        } catch (e: ApiException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun trySilentSignIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            val account = signInClient.silentSignIn().await()
            val name = account?.email
            if (name != null) {
                tokenStore.save(CloudProvider.GOOGLE, StoredAuth(account = name))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        cachedToken = null
        tokenStore.clear(CloudProvider.GOOGLE)
        runCatching { signInClient.signOut().await() }
        Unit
    }

    @Synchronized
    fun fetchTokenBlocking(): String {
        val accountName = tokenStore.get(CloudProvider.GOOGLE).account
            ?: throw AuthRequiredException("Google аккаунт не подключён")
        return try {
            GoogleAuthUtil.getToken(context, Account(accountName, GOOGLE_ACCOUNT_TYPE), authScope)
                .also { cachedToken = it }
        } catch (e: UserRecoverableAuthException) {
            throw AuthRequiredException("Требуется повторная авторизация Google")
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw AuthRequiredException("Ошибка авторизации Google: ${e.message}")
        }
    }

    fun peekToken(): String? = cachedToken

    @Synchronized
    fun invalidateTokenBlocking(token: String) {
        cachedToken = null
        runCatching { GoogleAuthUtil.clearToken(context, token) }
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
