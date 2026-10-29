package app.traceday.cloud

import app.traceday.cloud.format.PointDto
import app.traceday.cloud.format.TrackFileCodec
import app.traceday.cloud.google.GoogleDriveClient
import app.traceday.cloud.yandex.YandexDiskClient
import app.traceday.core.Clock
import app.traceday.core.day.DayKeys
import app.traceday.core.link.LinkParser
import app.traceday.core.link.TrackLink
import app.traceday.data.local.TrackDao
import app.traceday.data.local.TrackEntity
import app.traceday.data.local.TrackKind
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

    private sealed class Fetch {
        data class Ok(val points: List<PointDto>, val canonicalUrl: String) : Fetch()
        data class Fail(val message: String) : Fetch()
    }

    suspend fun import(link: String): ImportResult {
        val parsed = LinkParser.parse(link)
            ?: return ImportResult.Error("Ссылка не распознана")
        return when (val fetched = fetch(parsed)) {
            is Fetch.Fail -> ImportResult.Error(fetched.message)
            is Fetch.Ok -> {
                if (fetched.points.size > MAX_POINTS) {
                    ImportResult.Error("Слишком много точек в файле (${fetched.points.size})")
                } else {
                    insertNewTrack(fetched)
                }
            }
        }
    }

    suspend fun refresh(track: TrackEntity): ImportResult {
        val sourceUrl = track.sourceUrl
            ?: return ImportResult.Error("У трека нет ссылки-источника")
        val parsed = LinkParser.parse(sourceUrl)
            ?: return ImportResult.Error("Ссылка-источник не распознана")
        return when (val fetched = fetch(parsed)) {
            is Fetch.Fail -> ImportResult.Error(fetched.message)
            is Fetch.Ok -> {
                if (fetched.points.isEmpty()) return ImportResult.Error("Файл-источник пуст")
                if (fetched.points.size > MAX_POINTS) {
                    return ImportResult.Error("Слишком много точек (${fetched.points.size})")
                }
                dao.deletePoints(listOf(track.id))
                dao.insertPoints(
                    fetched.points.map {
                        TrackPointEntity(
                            trackId = track.id,
                            timestamp = it.t,
                            lat = it.lat,
                            lon = it.lon,
                            accuracy = it.acc,
                            provider = it.p,
                            uploaded = true
                        )
                    }
                )
                ImportResult.Success(track.title, fetched.points.size)
            }
        }
    }

    private suspend fun insertNewTrack(fetched: Fetch.Ok): ImportResult {
        val imported = dao.importedTracksFlow().first()
        imported.firstOrNull { it.sourceUrl == fetched.canonicalUrl }?.let { dao.deleteTrack(it.id) }
        val id = UUID.randomUUID().toString()
        val title = "Импорт · ${DayKeys.keyFor(fetched.points.first().t)}"
        val color = PALETTE[imported.size % PALETTE.size]
        dao.insertTrack(
            TrackEntity(
                id = id,
                title = title,
                kind = TrackKind.IMPORTED,
                sourceUrl = fetched.canonicalUrl,
                colorArgb = color,
                addedAt = clock.now()
            )
        )
        dao.insertPoints(
            fetched.points.map {
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
        return ImportResult.Success(title, fetched.points.size)
    }

    private suspend fun fetch(parsed: TrackLink): Fetch {
        val bytes = try {
            when (parsed) {
                is TrackLink.GoogleDrive -> googleDriveClient.downloadShared(parsed)
                is TrackLink.YandexDisk -> yandexDiskClient.downloadShared(parsed)
                is TrackLink.Raw -> httpGetBytes(http, parsed.url)
            }
        } catch (e: AuthRequiredException) {
            return Fetch.Fail("Нет доступа к файлу: ${e.message}")
        } catch (e: Exception) {
            return Fetch.Fail("Не удалось скачать файл: ${e.message}")
        }
        if (bytes.size > MAX_BYTES) return Fetch.Fail("Файл слишком большой")
        val points = codec.decode(bytes)
        if (points.isEmpty()) return Fetch.Fail("В файле нет корректных точек")
        val canonicalUrl = when (parsed) {
            is TrackLink.GoogleDrive -> "https://drive.google.com/file/d/${parsed.fileId}"
            is TrackLink.YandexDisk -> parsed.publicKeyUrl
            is TrackLink.Raw -> parsed.url
        }
        return Fetch.Ok(points, canonicalUrl)
    }

    companion object {
        private const val MAX_BYTES = 20 * 1024 * 1024
        private const val MAX_POINTS = 50_000
        private val PALETTE = listOf(0xFF2DD4BF, 0xFFFFB347, 0xFFE0A7FF, 0xFFFF7A9E, 0xFF8FE388)
    }
}
