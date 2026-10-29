package app.traceday.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.traceday.data.local.TrackDao
import app.traceday.domain.GeoPointData
import app.traceday.domain.TrackStats
import app.traceday.domain.TrackStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryItem(
    val dayKey: String,
    val stats: TrackStats,
    val allSynced: Boolean
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: TrackDao
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> = dao.summariesFlow()
        .map { aggregates ->
            aggregates.filter { it.trackId.startsWith("self-") }
                .map { agg ->
                    val points = dao.pointsOnce(agg.trackId)
                    HistoryItem(
                        dayKey = agg.trackId.removePrefix("self-"),
                        stats = TrackStatsCalculator.compute(
                            points.map { GeoPointData(it.timestamp, it.lat, it.lon) }
                        ),
                        allSynced = points.all { it.uploaded }
                    )
                }
                .sortedByDescending { it.dayKey }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
