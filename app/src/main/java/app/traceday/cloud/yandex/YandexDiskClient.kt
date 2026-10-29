package app.traceday.cloud.yandex

import app.traceday.cloud.AuthRequiredException
import app.traceday.cloud.CloudClient
import app.traceday.cloud.CloudException
import app.traceday.cloud.CloudProvider
import app.traceday.cloud.RemoteDayFile
import app.traceday.cloud.cloudCall
import app.traceday.cloud.defaultJson
import app.traceday.cloud.httpGetBytes
import app.traceday.cloud.httpPutBytes
import app.traceday.cloud.parseIsoMillis
import app.traceday.core.day.DayKeys
import app.traceday.core.link.TrackLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class YandexDiskClient @Inject constructor(
    private val api: YandexDiskApi,
    private val auth: YandexAuthManager,
    @Named("plain") private val plainHttp: OkHttpClient
) : CloudClient {

    override val provider: CloudProvider = CloudProvider.YANDEX

    override suspend fun ensureStorage() {
        cloudCall {
            val response = api.createFolder(FOLDER_PATH)
            if (!response.isSuccessful && response.code() != 409) {
                throw CloudException("Не удалось создать папку на Диске: HTTP ${response.code()}")
            }
        }
    }

    override suspend fun listDayFiles(): List<RemoteDayFile> = cloudCall {
        api.resources(FOLDER_PATH)._embedded?.items.orEmpty().mapNotNull { item ->
            item.path?.let { path ->
                RemoteDayFile(
                    id = path,
                    name = item.name ?: "",
                    modifiedAtMillis = parseIsoMillis(item.modified),
                    publicUrl = item.public_url
                )
            }
        }
    }

    override suspend fun uploadDayFile(dayKey: String, content: ByteArray) = cloudCall {
        ensureStorage()
        val path = dayFilePath(dayKey)
        val href = api.uploadLink(path).href
            ?: throw CloudException("Яндекс Диск не выдал ссылку для загрузки")
        httpPutBytes(plainHttp, href, content)
    }

    override suspend fun publishDayFile(dayKey: String): String = cloudCall {
        val path = dayFilePath(dayKey)
        val publishResponse = api.publish(path)
        if (!publishResponse.isSuccessful) {
            throw CloudException("Не удалось опубликовать файл: HTTP ${publishResponse.code()}")
        }
        api.resources(path).public_url
            ?: throw CloudException("Яндекс Диск не вернул публичную ссылку")
    }

    override suspend fun downloadShared(link: TrackLink): ByteArray {
        val yandexLink = link as? TrackLink.YandexDisk
            ?: throw CloudException("Ожидалась ссылка Яндекс Диска")
        val href = publicDownloadHref(yandexLink.publicKeyUrl)
        return httpGetBytes(plainHttp, href)
    }

    private suspend fun publicDownloadHref(publicKeyUrl: String): String =
        withContext(Dispatchers.IO) {
            val url = "https://cloud-api.yandex.net/v1/disk/public/resources/download" +
                "?public_key=${URLEncoder.encode(publicKeyUrl, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            plainHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw CloudException("HTTP ${response.code} для публичной ссылки")
                val body = response.body?.string() ?: throw CloudException("Пустой ответ")
                defaultJson.decodeFromString(YandexLinkDto.serializer(), body).href
                    ?: throw CloudException("Не удалось получить ссылку скачивания")
            }
        }

    override suspend fun deleteFilesOlderThan(cutoffMillis: Long): Int = cloudCall {
        var deleted = 0
        val items = api.resources(FOLDER_PATH)._embedded?.items.orEmpty()
        for (item in items) {
            val path = item.path ?: continue
            val modified = parseIsoMillis(item.modified)
            if (modified in 1 until cutoffMillis) {
                val response = api.delete(path)
                if (response.isSuccessful) deleted++
            }
        }
        deleted
    }

    fun isAvailable(): Boolean = auth.isConfigured

    private fun dayFilePath(dayKey: String): String = "$FOLDER_PATH/${DayKeys.fileName(dayKey)}"

    companion object {
        private const val FOLDER_PATH = "/TraceDay"
    }
}
