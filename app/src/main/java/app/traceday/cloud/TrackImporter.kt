package app.traceday.cloud

import app.traceday.cloud.format.PointDto
import app.traceday.cloud.format.TrackFileCodec
import app.traceday.cloud.google.GoogleDriveClient
import app.traceday.cloud.yandex.YandexDiskClient
import app.traceday.core.Clock
import app.traceday.core.day.DayKeys
import app.traceday.core.link.LinkParser
import app.traceday.core.link.TrackLink
import app.traceday.data.local.TrackEntity
import app.traceday.data.local.TrackKind
import app.traceday.data.local.TrackDao
import app.traceday.data.local.TrackPointEntity
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class TrackImporter @Inject constructor(
    private val dao: TrackDao,
    private val googleDriveClient: GoogleDriveClient,
    private val yandexDiskClient: YandexDiskClient,
    private val codec: TrackFileCodec,
    @Named("plain") private val http: OkHttpClient,
    private val clock: Clock
) {

    suspend fun import(link: String): ImportResult {
        val parsed = LinkParser.parse(link)
            ?: return ImportResult.Error("Ссылка не распознана")
        val bytes = try {
            when (parsed) {
                is TrackLink.GoogleDrive -> googleDriveClient.downloadShared(parsed)
                is TrackLink.YandexDisk -> yandexDiskClient.downloadShared(parsed)
                is TrackLink.Raw -> httpGetBytes(http, parsed.url)
            }
        } catch (e: AuthRequiredException) {
            return ImportResult.Error("Нет доступа к файлу: ${e.message}")
        } catch (e: Exception) {
            return ImportResult.Error("Не удалось скачать файл: ${e.message}")
        }
        if (bytes.size > MAX_BYTES) return ImportResult.Error("Файл слишком большой")
        val points = codec.decode(bytes)
        if (points.isEmpty()) return ImportResult.Error("В файле нет корректных точек")
        if (points.size > MAX_POINTS) {
            return ImportResult.Error("Слишком много точек в файле (${points.size})")
        }
        val sourceUrl = when (parsed) {
            is TrackLink.GoogleDrive -> "https://drive.google.com/file/d/${parsed.fileId}"
            is TrackLink.YandexDisk -> parsed.publicKeyUrl
            is TrackLink.Raw -> parsed.url
        }
        val imported = dao.importedTracksFlow().first()
        imported.firstOrNull { it.sourceUrl == sourceUrl }?.let { dao.deleteTrack(it.id) }
        val id = UUID.randomUUID().toString()
        val title = "Импорт · ${DayKeys.keyFor(points.first().t)}"
        val color = PALETTE[imported.size % PALETTE.size]
        dao.insertTrack(
            TrackEntity(
                id = id,
                title = title,
                kind = TrackKind.IMPORTED,
                sourceUrl = sourceUrl,
                colorArgb = color,
                addedAt = clock.now()
            )
        )
        dao.insertPoints(
            points.map {
                TrackPointEntity(
                    trackId = id,
                    timestamp = it.t,
                    lat = it.lat,
                    lon = it.lon,
                    accuracy = it.acc,
                    provider = it.p,
                    uploaded = true
                )
            }
        )
        return ImportResult.Success(title, points.size)
    }

    companion object {
        private const val MAX_BYTES = 20 * 1024 * 1024
        private const val MAX_POINTS = 50_000
        private val PALETTE = listOf(0xFF2DD4BF, 0xFFFFB347, 0xFFE0A7FF, 0xFFFF7A9E, 0xFF8FE388)
    }
}
