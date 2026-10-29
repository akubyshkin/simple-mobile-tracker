package app.traceday.cloud.yandex

import app.traceday.cloud.AuthRequiredException
import okhttp3.Interceptor
import okhttp3.Response

class YandexAuthInterceptor(private val auth: YandexAuthManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = auth.currentTokenBlocking()
            ?: throw AuthRequiredException("Яндекс Диск не подключён")
        var response = chain.proceed(withAuth(chain, token))
        if (response.code == 401) {
            val fresh = auth.refreshTokenBlocking()?.accessToken
            if (fresh == null) {
                response.close()
                throw AuthRequiredException("Не удалось обновить токен Яндекса")
            }
            response.close()
            response = chain.proceed(withAuth(chain, fresh))
        }
        return response
    }

    private fun withAuth(chain: Interceptor.Chain, token: String) =
        chain.request().newBuilder().header("Authorization", "OAuth $token").build()
}
