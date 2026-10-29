package app.traceday.cloud

import app.traceday.data.prefs.CloudProviderOption

enum class CloudProvider { GOOGLE, YANDEX }

open class CloudException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AuthRequiredException(message: String) : CloudException(message)

data class RemoteDayFile(
    val id: String,
    val name: String,
    val modifiedAtMillis: Long,
    val publicUrl: String? = null
)

data class CloudAuthState(
    val cloud: CloudProviderOption,
    val signedIn: Boolean,
    val account: String?,
    val authIssue: Boolean
)

enum class SyncOutcome { OK, NO_PROVIDER, NETWORK, AUTH, ERROR }

sealed interface ImportResult {
    data class Success(val title: String, val points: Int) : ImportResult
    data class Error(val message: String) : ImportResult
}
