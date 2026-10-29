package app.traceday.cloud

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

const val OCTET_STREAM = "application/octet-stream"

val defaultJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    coerceInputValues = true
}

fun httpGetBytes(client: OkHttpClient, url: String): ByteArray {
    client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
        if (!response.isSuccessful) throw CloudException("HTTP ${response.code} для $url")
        return response.body?.bytes() ?: throw CloudException("Пустой ответ от $url")
    }
}

fun httpPutBytes(client: OkHttpClient, url: String, bytes: ByteArray) {
    val body = bytes.toRequestBody(OCTET_STREAM.toMediaType())
    client.newCall(Request.Builder().url(url).put(body).build()).execute().use { response ->
        if (!response.isSuccessful) throw CloudException("HTTP ${response.code} при загрузке на $url")
    }
}

fun parseIsoMillis(iso: String?): Long {
    if (iso.isNullOrBlank()) return 0
    return try {
        Instant.parse(iso).toEpochMilli()
    } catch (e: Exception) {
        try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (e2: Exception) {
            0
        }
    }
}

fun HttpException.toCloudException(): CloudException =
    if (code() == 401 || code() == 403) {
        AuthRequiredException("Нет доступа к облачному файлу (HTTP ${code()})")
    } else {
        CloudException("HTTP ${code()}: ${message()}")
    }

suspend fun <T> cloudCall(block: suspend () -> T): T = try {
    block()
} catch (e: AuthRequiredException) {
    throw e
} catch (e: IOException) {
    throw e
} catch (e: HttpException) {
    throw e.toCloudException()
} catch (e: CloudException) {
    throw e
} catch (e: Exception) {
    throw CloudException("Ошибка облака: ${e.message ?: e.javaClass.simpleName}", e)
}
