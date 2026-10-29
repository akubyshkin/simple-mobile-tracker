package app.traceday.ui.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traceday.cloud.CloudRepository
import app.traceday.cloud.TrackImporter
import app.traceday.core.Clock
import app.traceday.core.day.DayKeys
import app.traceday.data.local.TrackDao
import app.traceday.data.local.TrackEntity
import app.traceday.data.local.TrackKind
import app.traceday.data.local.TrackPointEntity
import app.traceday.data.prefs.AppSettings
import app.traceday.data.prefs.SettingsRepository
import app.traceday.domain.GeoPointData
import app.traceday.domain.TrackStats
import app.traceday.domain.TrackStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class RangeFilter(val hours: Int?) {
    ALL(null), H6(6), H3(3), H1(1)
}

data class TrackPolyline(
    val trackId: String,
    val title: String,
    val colorArgb: Long,
    val points: List<GeoPointData>
)

data class MapUiState(
    val selectedDay: LocalDate = LocalDate.now(),
    val range: RangeFilter = RangeFilter.ALL,
    val trackingRunning: Boolean = false,
    val unsyncedCount: Int = 0,
    val lastSyncAt: Long = 0,
    val authIssue: Boolean = false,
    val selfStats: TrackStats = TrackStats(0.0, 0, 0, null, null),
    val polylines: List<TrackPolyline> = emptyList(),
    val imported: List<TrackEntity> = emptyList(),
    val message: String? = null,
    val shareUrl: String? = null,
    val busy: Boolean = false
)

private data class CoreState(
    val day: LocalDate,
    val range: RangeFilter,
    val busy: Boolean,
    val message: String?,
    val shareUrl: String?
)

private data class TracksSnapshot(
    val tracks: List<TrackEntity>,
    val dayPoints: List<TrackPointEntity>,
    val importedPoints: List<TrackPointEntity>
)

private data class SyncSnapshot(val settings: AppSettings, val unsynced: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val dao: TrackDao,
    private val settingsRepository: SettingsRepository,
    private val cloudRepository: CloudRepository,
    private val trackImporter: TrackImporter,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val selectedDay = MutableStateFlow(
        savedStateHandle.get<String>("day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
    )
    private val range = MutableStateFlow(RangeFilter.ALL)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val shareUrl = MutableStateFlow<String?>(null)
    private var lastImportedRefreshAt = 0L

    private val tracks: StateFlow<List<TrackEntity>> = dao.tracksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dayPoints = selectedDay.flatMapLatest { day ->
        val bounds = DayKeys.bounds(day.toString())
        dao.pointsFlow(listOf(DayKeys.selfTrackId(day.toString())), bounds.first, bounds.last)
    }

    private val importedPoints = tracks.flatMapLatest { list ->
        val ids = list.filter { it.kind == TrackKind.IMPORTED && it.enabled }.map { it.id }
        if (ids.isEmpty()) flowOf(emptyList()) else dao.pointsFlow(ids, 0L, Long.MAX_VALUE)
    }

    val uiState: StateFlow<MapUiState> = combine(
        combine(selectedDay, range, busy, message, shareUrl, ::CoreState),
        combine(tracks, dayPoints, importedPoints, ::TracksSnapshot),
        combine(settingsRepository.settings, dao.unsyncedCountFlow(), ::SyncSnapshot)
    ) { core, snapshot, sync -> buildState(core, snapshot, sync) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MapUiState())

    fun selectDay(day: LocalDate) {
        selectedDay.value = day
    }

    fun setRange(filter: RangeFilter) {
        range.value = filter
    }

    fun toggleImported(trackId: String) {
        viewModelScope.launch {
            val track = tracks.value.firstOrNull { it.id == trackId } ?: return@launch
            dao.setEnabled(trackId, !track.enabled)
        }
    }

    fun removeImported(trackId: String) {
        viewModelScope.launch { dao.deleteTrack(trackId) }
    }

    fun importLink(link: String) {
        busy.value = true
        viewModelScope.launch {
            val result = trackImporter.import(link)
            busy.value = false
            message.value = when (result) {
                is app.traceday.cloud.ImportResult.Success ->
                    "Импортировано «${result.title}»: ${result.points} точек"
                is app.traceday.cloud.ImportResult.Error -> result.message
            }
        }
    }

    fun shareSelectedDay() {
        busy.value = true
        viewModelScope.launch {
            try {
                shareUrl.value = cloudRepository.publishDay(selectedDay.value.toString())
            } catch (e: Exception) {
                message.value = "Не удалось поделиться: ${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun consumeShareUrl() {
        shareUrl.value = null
    }

    fun refreshImported() {
        val now = clock.now()
        if (now - lastImportedRefreshAt < IMPORTED_REFRESH_THROTTLE_MS) return
        lastImportedRefreshAt = now
        val enabled = tracks.value.filter {
            it.kind == TrackKind.IMPORTED && it.enabled && it.sourceUrl != null
        }
        if (enabled.isEmpty()) return
        viewModelScope.launch {
            var failed = 0
            for (track in enabled) {
                if (trackImporter.refresh(track) is app.traceday.cloud.ImportResult.Error) failed++
            }
            if (failed > 0) {
                message.value = "Не удалось обновить чужие треки: $failed из ${enabled.size}"
            }
        }
    }

    private fun buildState(core: CoreState, snapshot: TracksSnapshot, sync: SyncSnapshot): MapUiState {
        val dayKey = core.day.toString()
        val dayBounds = DayKeys.bounds(dayKey)
        val rangeStart = core.range.hours
            ?.let { clock.now() - it * 3_600_000L }
            ?: dayBounds.first
        val filteredSelf = snapshot.dayPoints.filter { it.timestamp >= rangeStart }
        val selfEntity = snapshot.tracks.firstOrNull { it.id == DayKeys.selfTrackId(dayKey) }
        val polylines = buildList {
            if (filteredSelf.isNotEmpty()) {
                add(
                    TrackPolyline(
                        trackId = DayKeys.selfTrackId(dayKey),
                        title = "Мой трек",
                        colorArgb = selfEntity?.colorArgb ?: DEFAULT_SELF_COLOR,
                        points = filteredSelf.map { GeoPointData(it.timestamp, it.lat, it.lon) }
                    )
                )
            }
            snapshot.tracks.filter { it.kind == TrackKind.IMPORTED }.forEach { track ->
                val points = snapshot.importedPoints.filter { it.trackId == track.id }
                if (points.isNotEmpty()) {
                    add(
                        TrackPolyline(
                            trackId = track.id,
                            title = track.title,
                            colorArgb = track.colorArgb,
                            points = points.map { GeoPointData(it.timestamp, it.lat, it.lon) }
                        )
                    )
                }
            }
        }
        return MapUiState(
            selectedDay = core.day,
            range = core.range,
            trackingRunning = sync.settings.trackingRunning,
            unsyncedCount = sync.unsynced,
            lastSyncAt = sync.settings.lastSyncAt,
            authIssue = sync.settings.authIssue,
            selfStats = TrackStatsCalculator.compute(
                filteredSelf.map { GeoPointData(it.timestamp, it.lat, it.lon) }
            ),
            polylines = polylines,
            imported = snapshot.tracks.filter { it.kind == TrackKind.IMPORTED },
            message = core.message,
            shareUrl = core.shareUrl,
            busy = core.busy
        )
    }

    companion object {
        const val DEFAULT_SELF_COLOR = 0xFF4F8CFF
        private const val IMPORTED_REFRESH_THROTTLE_MS = 10 * 60_000L
    }
}
