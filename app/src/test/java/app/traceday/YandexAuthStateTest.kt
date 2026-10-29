package app.traceday

import androidx.test.core.app.ApplicationProvider
import app.traceday.cloud.TokenStore
import app.traceday.cloud.yandex.YandexAuthManager
import app.traceday.cloud.yandex.YandexAuthResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YandexAuthStateTest {

    private lateinit var manager: YandexAuthManager

    @Before
    fun setUp() {
        manager = YandexAuthManager(
            TokenStore(ApplicationProvider.getApplicationContext()),
            OkHttpClient()
        )
        manager.clientIdOverride = "test-client-id"
    }

    @Test
    fun authUrl_containsRandomState() {
        val first = manager.beginAuthSession()
        val second = manager.beginAuthSession()
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first!!.contains("state="))
        assertTrue(second!!.contains("state="))
        assertTrue("state должен быть случайным", first != second)
    }

    @Test
    fun redirectWithoutActiveSession_rejected() = runTest {
        val result = manager.handleRedirect(
            android.net.Uri.parse("traceday://oauth2?code=abc&state=xyz")
        )
        assertTrue(result is YandexAuthResult.Error)
        assertEquals(
            "Вход не был инициирован из приложения",
            (result as YandexAuthResult.Error).message
        )
    }

    @Test
    fun redirectWithWrongState_rejected() = runTest {
        manager.beginAuthSession()
        val result = manager.handleRedirect(
            android.net.Uri.parse("traceday://oauth2?code=abc&state=evil")
        )
        assertTrue(result is YandexAuthResult.Error)
        assertEquals(
            "Неверный параметр state — запрос отклонён",
            (result as YandexAuthResult.Error).message
        )
    }

    @Test
    fun stateIsOneShot_secondRedirectRejected() = runTest {
        manager.beginAuthSession()
        manager.handleRedirect(android.net.Uri.parse("traceday://oauth2?code=abc&state=evil"))
        val result = manager.handleRedirect(android.net.Uri.parse("traceday://oauth2?code=abc"))
        assertTrue(result is YandexAuthResult.Error)
    }

    @Test
    fun redirectWithErrorParam_rejected() = runTest {
        manager.beginAuthSession()
        val result = manager.handleRedirect(
            android.net.Uri.parse("traceday://oauth2?error=access_denied")
        )
        assertTrue(result is YandexAuthResult.Error)
    }
}
