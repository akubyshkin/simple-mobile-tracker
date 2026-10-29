package app.traceday.cloud.google

import app.traceday.cloud.AuthRequiredException
import app.traceday.cloud.CloudClient
import app.traceday.cloud.CloudException
import app.traceday.cloud.CloudProvider
import app.traceday.cloud.RemoteDayFile
import app.traceday.cloud.cloudCall
import app.traceday.cloud.httpGetBytes
import app.traceday.cloud.parseIsoMillis
import app.traceday.cloud.toCloudException
import app.traceday.core.day.DayKeys
import app.traceday.core.link.TrackLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class GoogleDriveClient @Inject constructor(
    private val api: DriveApi,
    private val auth: GoogleAuthManager,
    @Named("plain") private val plainHttp: OkHttpClient
) : CloudClient {

    override val provider: CloudProvider = CloudProvider.GOOGLE

    private var folderId: String? = null

    override suspend fun ensureStorage() {
        ensureFolder()
    }

    override suspend fun listDayFiles(): List<RemoteDayFile> = cloudCall {
        val folder = ensureFolder()
        api.list("'$folder' in parents and trashed = false").files.mapNotNull { f ->
            f.id?.let { RemoteDayFile(it, f.name ?: "", parseIsoMillis(f.modifiedTime), f.webViewLink) }
        }
    }

    override suspend fun uploadDayFile(dayKey: String, content: ByteArray) = cloudCall {
        val folder = ensureFolder()
        val name = DayKeys.fileName(dayKey)
        val existing = findDayFileId(folder, name)
        val fileId = existing
            ?: api.create(
                DriveFileDto(
                    name = name,
                    mimeType = OCTET_STREAM_MIME,
                    parents = listOf(folder)
                )
            ).id
            ?: throw CloudException("Не удалось создать файл $name в Google Drive")
        val response = api.putMedia(
            "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media",
            content.toRequestBody(OCTET_STREAM_MIME.toMediaType())
        )
        if (!response.isSuccessful) throw CloudException("Загрузка в Google Drive: HTTP ${response.code()}")
    }

    override suspend fun publishDayFile(dayKey: String): String = cloudCall {
        val folder = ensureFolder()
        val name = DayKeys.fileName(dayKey)
        val fileId = findDayFileId(folder, name)
            ?: throw CloudException("Файл $name ещё не выгружен, выполните синхронизацию")
        val permission = api.grantPermission(fileId, permission = DrivePermissionDto())
        if (!permission.isSuccessful && permission.code() !in listOf(200, 204)) {
            throw CloudException("Не удалось открыть доступ: HTTP ${permission.code()}")
        }
        api.get(fileId).webViewLink
            ?: throw CloudException("Google Drive не вернул ссылку на файл")
    }

    override suspend fun downloadShared(link: TrackLink): ByteArray {
        val driveLink = link as? TrackLink.GoogleDrive
            ?: throw CloudException("Ожидалась ссылка Google Drive")
        return cloudCall {
            if (auth.isSignedIn()) {
                val response = api.getMedia(
                    "https://www.googleapis.com/drive/v3/files/${driveLink.fileId}?alt=media"
                )
                if (response.isSuccessful) {
                    response.body()?.bytes()
                        ?: throw CloudException("Пустой ответ Google Drive")
                }
            }
            httpGetBytes(plainHttp, "https://drive.google.com/uc?export=download&id=${driveLink.fileId}")
        }
    }

    override suspend fun deleteFilesOlderThan(cutoffMillis: Long): Int = cloudCall {
        val folder = ensureFolder()
        val files = api.list("'$folder' in parents and trashed = false").files
        var deleted = 0
        for (f in files) {
            val id = f.id ?: continue
            val modified = parseIsoMillis(f.modifiedTime)
            if (modified in 1 until cutoffMillis) {
                val response: Response<Unit> = api.delete(id)
                if (response.isSuccessful) deleted++
            }
        }
        deleted
    }

    private suspend fun ensureFolder(): String {
        folderId?.let { return it }
        val query = "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false"
        val existing = api.list(query).files.firstOrNull()?.id
        val id = existing
            ?: api.create(DriveFileDto(name = FOLDER_NAME, mimeType = FOLDER_MIME)).id
            ?: throw CloudException("Не удалось создать папку $FOLDER_NAME")
        folderId = id
        return id
    }

    private suspend fun findDayFileId(folder: String, name: String): String? =
        api.list("name = '$name' and '$folder' in parents and trashed = false")
            .files.firstOrNull()?.id

    private companion object {
        const val FOLDER_NAME = "TraceDay"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val OCTET_STREAM_MIME = "application/octet-stream"
    }
}
