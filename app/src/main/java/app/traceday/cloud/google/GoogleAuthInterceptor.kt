package app.traceday.cloud.google

import app.traceday.cloud.AuthRequiredException
import okhttp3.Interceptor
import okhttp3.Response

class GoogleAuthInterceptor(private val auth: GoogleAuthManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var token = auth.peekToken() ?: auth.fetchTokenBlocking()
        var response = chain.proceed(withAuth(chain, token))
        if (response.code == 401) {
            auth.invalidateTokenBlocking(token)
            token = try {
                auth.fetchTokenBlocking()
            } catch (e: AuthRequiredException) {
                response.close()
                throw e
            }
            response.close()
            response = chain.proceed(withAuth(chain, token))
        }
        return response
    }

    private fun withAuth(chain: Interceptor.Chain, token: String) =
        chain.request().newBuilder().header("Authorization", "Bearer $token").build()
}
